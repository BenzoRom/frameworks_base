/*
 * Copyright (C) 2014 The NamelessRom Project
 * Copyright (C) 2022 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("DEPRECATION")
package com.android.systemui.onthego

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.*
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.BlockUntrustedTouchesMode
import android.os.Handler
import android.os.IBinder
import android.os.Looper.*
import android.provider.Settings
import android.util.Log
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.android.internal.util.benzo.OnTheGoUtils
import com.android.systemui.R
import java.io.IOException

@Suppress("DEPRECATION")
class OnTheGoService : Service() {

    companion object {
        private const val logTag = "OnTheGoService"
        private const val isDebug = false
        private const val notificationId = 81333378
        private const val notificationChannelId = "onthego_notification"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_TOGGLE_ALPHA = "toggle_alpha"
        const val ACTION_TOGGLE_CAMERA = "toggle_camera"
        const val ACTION_TOGGLE_OPTIONS = "toggle_options"
        const val EXTRA_ALPHA = "extra_alpha"
        private const val cameraBack = 0
        private const val cameraFront = 1
        private const val notificationStarted = 0
        private const val notificationRestart = 1
        private const val notificationError = 2
    }

    private val handler = Handler()
    @BlockUntrustedTouchesMode private var blockedTouchDefault = 0
    private val restartObject = Any()
    private var overlay: FrameLayout? = null
    private var camera: Camera? = null
    private var notificationManager: NotificationManager? = null
    private val restartRunnable = Runnable { synchronized(restartObject) { setupViews(true) } }
    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceivers()
        resetViews()
    }

    private fun registerReceivers() {
        IntentFilter(ACTION_TOGGLE_ALPHA).apply {
            registerReceiver(alphaReceiver, this)
        }
        IntentFilter(ACTION_TOGGLE_CAMERA).apply {
            registerReceiver(cameraReceiver, this)
        }
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(alphaReceiver)
        } catch (ex: Exception) {}
        try {
            unregisterReceiver(cameraReceiver)
        } catch (ex: Exception) {}
    }

    private val alphaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            toggleOnTheGoAlpha(
                intent.getFloatExtra(EXTRA_ALPHA, 0.5f)
            )
        }
    }
    private val cameraReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(restartObject) {
                val restartService =
                    Settings.System.getInt(
                        contentResolver,
                        Settings.System.ON_THE_GO_SERVICE_RESTART,
                        0
                    ) == 1
                when {
                    restartService -> restartOnTheGo()
                    else           -> stopOnTheGo(true)
                }
            }
        }
    }

    private fun updateBlockedTouches(starting: Boolean) {
        @BlockUntrustedTouchesMode val touchMode: Int
        when {
            starting -> {
                blockedTouchDefault =
                    Settings.Global.getInt(
                        contentResolver,
                        Settings.Global.BLOCK_UNTRUSTED_TOUCHES_MODE,
                        BlockUntrustedTouchesMode.BLOCK
                    )
                touchMode = BlockUntrustedTouchesMode.DISABLED
            }
            else     -> touchMode = blockedTouchDefault
        }

        Settings.Global.putInt(
            contentResolver,
            Settings.Global.BLOCK_UNTRUSTED_TOUCHES_MODE,
            touchMode
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        logDebug("onStartCommand called")
        when {
            intent == null || !OnTheGoUtils.hasCamera(this) -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else                                            -> {
                val action = intent.action
                when {
                    action != null && action.isNotEmpty() -> {
                        logDebug("Action: $action")
                        when (action) {
                            ACTION_START          -> startOnTheGo()
                            ACTION_STOP           -> stopOnTheGo(false)
                            ACTION_TOGGLE_OPTIONS -> OnTheGoDialog(this).show()
                        }
                    }
                    else                                  -> {
                        logDebug("Action is NULL or EMPTY!")
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun startOnTheGo() {
        when {
            notificationManager != null -> {
                logDebug("Starting while active, stopping.")
                stopOnTheGo(false)
                return
            }
            else                        -> {
                resetViews()
                registerReceivers()
                setupViews(false)
                createNotification(notificationStarted)
                updateBlockedTouches(true)
            }
        }
    }

    private fun stopOnTheGo(shouldRestart: Boolean) {
        unregisterReceivers()
        resetViews()
        when {
            notificationManager != null -> {
                notificationManager!!.cancel(notificationId)
                notificationManager = null
            }
        }
        when {
            shouldRestart -> createNotification(notificationRestart)
            else          -> updateBlockedTouches(false)
        }
        stopSelf()
    }

    private fun restartOnTheGo() {
        resetViews()
        with(handler) {
            removeCallbacks(restartRunnable)
            postDelayed(restartRunnable, 750)
        }
    }

    private fun toggleOnTheGoAlpha() {
        Settings.System.getFloat(
            contentResolver,
            Settings.System.ON_THE_GO_ALPHA,
            0.5f
        ).apply { toggleOnTheGoAlpha(this) }
    }

    private fun toggleOnTheGoAlpha(alpha: Float) {
        Settings.System.putFloat(
            contentResolver,
            Settings.System.ON_THE_GO_ALPHA,
            alpha
        )
        if (overlay != null) overlay!!.alpha = alpha
    }

    @Throws(RuntimeException::class, IOException::class)
    private fun getCameraInstance(type: Int) {
        releaseCamera()
        when {
            !OnTheGoUtils.hasFrontCamera(this) -> {
                camera = Camera.open()
                return
            }
            else                               ->
                when (type) {
                    cameraBack  -> camera = Camera.open(0)
                    cameraFront -> {
                        val info = CameraInfo()
                        val count = Camera.getNumberOfCameras()
                        var idx = 0
                        while (idx < count) {
                            Camera.getCameraInfo(idx, info)
                            when (info.facing) {
                                CameraInfo.CAMERA_FACING_FRONT -> {
                                    camera = Camera.open(idx)
                                }
                            }
                            idx++
                        }
                    }
                    else        -> camera = Camera.open(0)
                }
        }
    }

    private fun setupViews(isRestarting: Boolean) {
        logDebug("Setup Views, restarting: ${if (isRestarting) "true" else "false"}")
        val cameraType =
            Settings.System.getInt(
                contentResolver,
                Settings.System.ON_THE_GO_CAMERA,
                0
            )
        try {
            getCameraInstance(cameraType)
        } catch (ex: Exception) {
            logDebug("Exception: ${ex.message}")
            createNotification(notificationError)
            stopOnTheGo(true)
        }
        val textureView = TextureView(this)
        object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                when {
                    camera != null -> {
                        try {
                            camera!!.setDisplayOrientation(90)
                            camera!!.setPreviewTexture(surfaceTexture)
                            camera!!.startPreview()
                        } catch (io: IOException) {
                            logDebug("IOException: ${io.message}")
                        }
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                releaseCamera()
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }.also { textureView.surfaceTextureListener = it }
        overlay = FrameLayout(this)
        overlay!!.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        overlay!!.addView(textureView)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        with(getSystemService(WINDOW_SERVICE) as WindowManager) {
            addView(overlay, layoutParams)
        }
        toggleOnTheGoAlpha()
    }

    private fun resetViews() {
        releaseCamera()
        when {
            overlay != null -> {
                overlay!!.removeAllViews()
                with(getSystemService(WINDOW_SERVICE) as WindowManager) {
                    removeView(overlay)
                }
                overlay = null
            }
        }
    }

    private fun releaseCamera() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.release()
            camera = null
        }
    }

    private fun createNotification(notifType: Int) {
        val notificationBuilder =
            Notification.Builder(this, notificationChannelId)
                .setTicker(
                    resources.getString(
                        when (notifType) {
                            1    -> R.string.onthego_notification_camera_changed
                            2    -> R.string.onthego_notification_error
                            else -> R.string.onthego_notification_ticker
                        }
                    )
                )
                .setContentTitle(
                    resources.getString(
                        when (notifType) {
                            1    -> R.string.onthego_notification_camera_changed
                            2    -> R.string.onthego_notification_error
                            else -> R.string.onthego_notification_title
                        }
                    )
                )
                .setSmallIcon(R.drawable.ic_lock_onthego)
                .setWhen(System.currentTimeMillis())
                .setOngoing(!(notifType == 1 || notifType == 2))
        if (notifType == 1 || notifType == 2) {
            val startIntent = Intent()
            startIntent.component = ComponentName(
                "com.android.systemui",
                "com.android.systemui.benzo.onthego.OnTheGoService"
            )
            startIntent.action = ACTION_START
            val startPendIntent =
                PendingIntent.getService(
                    this, 0, startIntent,
                    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                )
            val actionRestart =
                Notification.Action.Builder(
                    com.android.internal.R.drawable.ic_media_play,
                    resources.getString(R.string.onthego_notification_restart),
                    startPendIntent
                ).build()
            notificationBuilder.addAction(actionRestart)
        } else {
            val stopIntent = Intent(this, OnTheGoService::class.java)
            stopIntent.action = ACTION_STOP
            val stopPendIntent =
                PendingIntent.getService(
                    this,
                    0,
                    stopIntent,
                    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                )
            val optionsIntent = Intent(this, OnTheGoService::class.java)
            optionsIntent.action = ACTION_TOGGLE_OPTIONS
            val optionsPendIntent =
                PendingIntent.getService(
                    this,
                    0,
                    optionsIntent,
                    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                )
            val actionStop =
                Notification.Action.Builder(
                    com.android.internal.R.drawable.ic_media_stop,
                    resources.getString(R.string.onthego_notification_stop),
                    stopPendIntent
                ).build()
            val actionOptions =
                Notification.Action.Builder(
                    com.android.internal.R.drawable.ic_text_dot,
                    resources.getString(R.string.onthego_notification_options),
                    optionsPendIntent
                ).build()
            notificationBuilder
                .addAction(actionStop)
                .addAction(actionOptions)
        }

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager!!.createNotificationChannel(
            NotificationChannel(
                notificationChannelId,
                resources.getString(R.string.onthego_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        notificationManager!!.notify(
            notificationId,
            notificationBuilder.build()
        )
    }

    private fun logDebug(message: String) {
        if (isDebug) Log.e(logTag, message)
    }
}

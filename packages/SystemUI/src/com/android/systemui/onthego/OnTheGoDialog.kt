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
package com.android.systemui.onthego

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper.*
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager.*
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Switch
import com.android.internal.util.benzo.OnTheGoUtils
import com.android.systemui.R

@Suppress("DEPRECATION")
class OnTheGoDialog(private val mContext: Context) : Dialog(mContext) {
    private val handler = Handler()
    private val onTheGoDialogLongTimeout: Int
    private val onTheGoDialogShortTimeout: Int
    private val dismissDialogRunnable = Runnable { if (isShowing) dismiss() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        window!!.setType(LayoutParams.TYPE_VOLUME_OVERLAY)
        window.attributes.privateFlags =
            window.attributes.privateFlags or
                    LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
        window.clearFlags(LayoutParams.FLAG_DIM_BEHIND)
        window.requestFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.onthego_notification_dialog)
        setCanceledOnTouchOutside(true)

        val contentResolver = mContext.contentResolver
        val slider = findViewById<SeekBar>(R.id.onthego_alpha_slider)
        val alpha =
            Settings.System.getFloat(
                contentResolver,
                Settings.System.ON_THE_GO_ALPHA,
                0.5f
            )
        with(slider) {
            progress = (alpha * 100).toInt()
            setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromTouch: Boolean
                    ) {
                        sendAlphaBroadcast((progress + 10).toString())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        removeAllOnTheGoDialogCallbacks()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        dismissOnTheGoDialog(onTheGoDialogShortTimeout)
                    }
                }
            )
        }
        when {
            !OnTheGoUtils.hasFrontCamera(context) -> {
                findViewById<View>(R.id.onthego_category).visibility = View.GONE
            }
            else                                  -> {
                val serviceToggle = findViewById<Switch>(R.id.onthego_service_toggle)
                val restartService =
                    Settings.System.getInt(
                        contentResolver,
                        Settings.System.ON_THE_GO_SERVICE_RESTART,
                        0
                    ) == 1
                serviceToggle.isChecked = restartService
                serviceToggle.setOnCheckedChangeListener { _, isChecked ->
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.ON_THE_GO_SERVICE_RESTART,
                        if (isChecked) 1 else 0
                    )
                    dismissOnTheGoDialog(onTheGoDialogShortTimeout)
                }
                val camSwitch = findViewById<Switch>(R.id.onthego_camera_toggle)
                val useFrontCam =
                    Settings.System.getInt(
                        contentResolver,
                        Settings.System.ON_THE_GO_CAMERA,
                        0
                    ) == 1
                camSwitch.isChecked = useFrontCam
                camSwitch.setOnCheckedChangeListener { _, isChecked ->
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.ON_THE_GO_CAMERA,
                        if (isChecked) 1 else 0
                    )
                    sendCameraBroadcast()
                    dismissOnTheGoDialog(onTheGoDialogShortTimeout)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dismissOnTheGoDialog(onTheGoDialogLongTimeout)
    }

    override fun onStop() {
        super.onStop()
        removeAllOnTheGoDialogCallbacks()
    }

    private fun dismissOnTheGoDialog(timeout: Int) {
        removeAllOnTheGoDialogCallbacks()
        with(handler) { postDelayed(dismissDialogRunnable, timeout.toLong()) }
    }

    private fun removeAllOnTheGoDialogCallbacks() {
        with(handler) { removeCallbacks(dismissDialogRunnable) }
    }

    private fun sendAlphaBroadcast(progress: String) {
        val broadcast = Intent()
        with(mContext) {
            with(broadcast) {
                action = OnTheGoService.ACTION_TOGGLE_ALPHA
                putExtra(OnTheGoService.EXTRA_ALPHA, progress.toFloat() / 100)
            }
            sendBroadcast(broadcast)
        }
    }

    private fun sendCameraBroadcast() {
        val broadcast = Intent()
        with(mContext) {
            broadcast.action = OnTheGoService.ACTION_TOGGLE_CAMERA
            sendBroadcast(broadcast)
        }
    }

    init {
        mContext.resources.apply {
            onTheGoDialogLongTimeout = getInteger(R.integer.onthego_notification_dialog_long_timeout)
            onTheGoDialogShortTimeout = getInteger(R.integer.onthego_notification_dialog_short_timeout)
        }
    }
}

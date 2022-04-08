/*
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
package com.benzorom.systemui.statusbar.policy

import android.annotation.Nullable
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.power.EnhancedEstimates
import com.android.systemui.settings.UserContentResolverProvider
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.policy.BatteryControllerImpl
import com.google.android.systemui.reversecharging.ReverseChargingController
import java.io.FileDescriptor
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
open class BatteryControllerImplGoogle @Inject constructor (
    context: Context,
    enhancedEstimates: EnhancedEstimates,
    powerManager: PowerManager,
    broadcastDispatcher: BroadcastDispatcher,
    demoModeController: DemoModeController,
    @Main mainHandler: Handler,
    @Background bgHandler: Handler,
    private val contentResolverProvider: UserContentResolverProvider,
    private val reverseChargingController: ReverseChargingController
) : BatteryControllerImpl(
    context, enhancedEstimates, powerManager, broadcastDispatcher,
    demoModeController, mainHandler, bgHandler
), ReverseChargingController.ReverseChargingChangeCallback {

    companion object {
        private const val logTag = "BatteryControllerGoogle"
        private val isDebug = Log.isLoggable(logTag, Log.DEBUG)
        private const val EBS_STATE_AUTHORITY = "com.google.android.flipendo.api"
        private val IS_EBS_ENABLED_OBSERVABLE_URI =
            Uri.parse("content://com.google.android.flipendo.api/get_flipendo_state")
    }

    protected val contentObserver: ContentObserver
    private var extremeSaver = false
    private var reverse = false
    private var rtxLevel = 0
    private var rtxName: String? = null

    override fun init() {
        super.init()
        resetReverseInfo()
        reverseChargingController.init(this)
        reverseChargingController.addCallback(
            this as ReverseChargingController.ReverseChargingChangeCallback
        )
        try {
            val userContentResolver = contentResolverProvider.userContentResolver
            with(contentObserver) {
                userContentResolver.registerContentObserver(
                    IS_EBS_ENABLED_OBSERVABLE_URI,
                    isDebug,
                    this
                )
                onChange(isDebug, IS_EBS_ENABLED_OBSERVABLE_URI)
            }
        } catch (ex: Exception) {
            Log.w(logTag, "Couldn't register to observe provider", ex)
        }
    }

    override fun onReverseChargingChanged(
        isReverse: Boolean,
        level: Int,
        @Nullable name: String?
    ) {
        reverse = isReverse
        rtxLevel = level
        rtxName = name
        if (isDebug) Log.d(
            logTag, "onReverseChargingChanged(): "
                    + "rtx=${if (isReverse) 1 else 0} level=$level name=$name this=$this"
        )
        fireReverseChanged()
    }

    override fun addCallback(cb: BatteryStateChangeCallback) {
        super.addCallback(cb)
        with(cb) {
            onReverseChanged(reverse, rtxLevel, rtxName)
            onExtremeBatterySaverChanged(extremeSaver)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        with(reverseChargingController) { handleIntentForReverseCharging(intent) }
    }

    override fun isReverseSupported(): Boolean {
        return reverseChargingController.isReverseSupported
    }

    override fun isReverseOn(): Boolean {
        return reverse
    }

    override fun setReverseState(isReverse: Boolean) {
        with(reverseChargingController) { setReverseState(isReverse) }
    }

    private fun resetReverseInfo() {
        reverse = false
        rtxLevel = -1
        rtxName = null
    }

    private fun setExtremeSaver(isExtreme: Boolean) {
        if (isExtreme != extremeSaver) {
            extremeSaver = isExtreme
            fireExtremeSaverChanged()
        }
    }

    private fun fireExtremeSaverChanged() {
        synchronized(mChangeCallbacks) {
            val n = mChangeCallbacks.size
            for (i in 0 until n) {
                mChangeCallbacks[i].onExtremeBatterySaverChanged(extremeSaver)
            }
        }
    }

    private fun fireReverseChanged() {
        synchronized(mChangeCallbacks) {
            val n = mChangeCallbacks.size
            for (i in 0 until n) {
                mChangeCallbacks[i].onReverseChanged(reverse, rtxLevel, rtxName)
            }
        }
    }

    private val isExtremeBatterySaving: Boolean
        get() {
            val extras = try {
                with(contentResolverProvider) {
                    userContentResolver.call(
                        EBS_STATE_AUTHORITY,
                        "get_flipendo_state",
                        null,
                        Bundle()
                    )
                }
            } catch (ex: IllegalArgumentException) {
                Bundle()
            }
            return extras!!.getBoolean("flipendo_state", false)
        }

    override fun dump(
        fd: FileDescriptor,
        pw: PrintWriter,
        args: Array<String>
    ) {
        super.dump(fd, pw, args)
        pw.print("  mReverse=")
        pw.println(reverse)
        pw.print("  mExtremeSaver=")
        pw.println(extremeSaver)
    }

    init {
        contentObserver = object : ContentObserver(bgHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (isDebug) Log.d(logTag, "Change in EBS value $uri")
                setExtremeSaver(isExtremeBatterySaving)
            }
        }
    }
}

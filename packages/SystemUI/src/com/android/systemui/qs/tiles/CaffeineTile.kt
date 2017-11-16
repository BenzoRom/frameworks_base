/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (c) 2017 The LineageOS Project
 * Copyright (c) 2022 Benzo Rom
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
package com.android.systemui.qs.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.IntentFilter
import android.os.*
import android.service.quicksettings.Tile
import android.view.View
import com.android.internal.logging.MetricsLogger
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import javax.inject.Inject

/** Quick settings tile: Caffeine  */
class CaffeineTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
) {
    private var countdownTimer: CountDownTimer? = null
    private var duration = 0
    private var lastClickTime: Long = -1
    private var secondsRemaining = 0
    private val receiver = Receiver()
    private val wakeLock = mContext.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.FULL_WAKE_LOCK, "systemui:$TAG")

    init {
        receiver::init
    }

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState()
    }

    override fun handleDestroy() {
        super.handleDestroy()
        stopCountDown()
        receiver::destroy
        when { wakeLock.isHeld -> wakeLock.run(PowerManager.WakeLock::release) }
    }

    override fun handleSetListening(listening: Boolean) {}
    override fun handleClick(view: View?) {
        // If last user clicks < 5 seconds we cycle different
        // duration otherwise toggle on/off
        if (wakeLock.isHeld
            && lastClickTime != -1L
            && SystemClock.elapsedRealtime() - lastClickTime < 5000
        ) {
            // cycle duration
            duration++
            when {
                duration >= wakeLockDurations.size -> {
                    // all durations cycled, turn if off
                    duration = -1
                    stopCountDown()
                    when { wakeLock.isHeld -> wakeLock.run(PowerManager.WakeLock::release) }
                }
                else -> {
                    // change duration
                    startCountDown(wakeLockDurations[duration].toLong())
                    when { !wakeLock.isHeld -> wakeLock.run(PowerManager.WakeLock::acquire) }
                }
            }
        } else {
            // toggle
            when {
                wakeLock.isHeld -> {
                    wakeLock.run(PowerManager.WakeLock::release)
                    stopCountDown()
                }
                else -> {
                    wakeLock.run(PowerManager.WakeLock::acquire)
                    duration = 0
                    startCountDown(wakeLockDurations[duration].toLong())
                }
            }
        }
        lastClickTime = SystemClock.elapsedRealtime()
        refreshState()
    }

    override fun handleLongClick(view: View?) {
        when {
            wakeLock.isHeld -> if (duration == infiniteDurationIndex) return
            else -> wakeLock.run(PowerManager.WakeLock::acquire)
        }
        duration = infiniteDurationIndex
        startCountDown(wakeLockDurations[infiniteDurationIndex].toLong())
        refreshState()
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_caffeine_label)
    }

    private fun startCountDown(howLong: Long) {
        stopCountDown()
        secondsRemaining = howLong.toInt()
        if (howLong == -1L) return // infinity timing, no need to start timer
        object : CountDownTimer(howLong * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = (millisUntilFinished / 1000).toInt()
                refreshState()
            }

            override fun onFinish() {
                when { wakeLock.isHeld -> wakeLock.run(PowerManager.WakeLock::release) }
                refreshState()
            }
        }.start().also { countdownTimer = it }
    }

    private fun stopCountDown() {
        when {
            countdownTimer != null -> {
                countdownTimer!!::cancel
                countdownTimer = null
            }
        }
    }

    private fun formatValueWithRemainingTime(): String {
        return when (secondsRemaining) {
            -1   -> "\u221E" // infinity
            else -> String.format(
                "%02d:%02d",
                secondsRemaining / 60 % 60,
                secondsRemaining % 60
            )
        }
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any) {
        state.value = wakeLock.isHeld
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_caffeine)
        when {
            state.value -> {
                state.secondaryLabel = formatValueWithRemainingTime()
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_on
                )
                state.state = Tile.STATE_ACTIVE
            }
            else -> {
                state.secondaryLabel = null
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_caffeine_off
                )
                state.state = Tile.STATE_INACTIVE
            }
        }
    }

    private inner class Receiver : BroadcastReceiver() {
        fun init() {
            // Register for Intent broadcasts for...
            mContext.registerReceiver(
                this,
                IntentFilter().apply { addAction(ACTION_SCREEN_OFF) },
                null,
                mHandler
            )
        }

        fun destroy() {
            mContext.unregisterReceiver(this)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_SCREEN_OFF == intent.action) {
                // disable caffeine if user force off (power button)
                stopCountDown()
                when { wakeLock.isHeld -> wakeLock.run(PowerManager.WakeLock::release) }
                refreshState()
            }
        }
    }

    companion object {
        private val wakeLockDurations = intArrayOf(
            5 * 60,  // 5 min
            10 * 60, // 10 min
            30 * 60, // 30 min
            -1
        )
        private val infiniteDurationIndex = wakeLockDurations.size - 1
    }
}

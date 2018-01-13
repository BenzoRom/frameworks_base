/*
 * Copyright (C) 2018 The OmniROM Project
 * Copyright (C) 2020-2021 The LineageOS Project
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
package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.android.systemui.qs.SettingObserver
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

/** Quick settings tile: Enable/Disable Always-On-Display  */
class AlwaysOnDisplayTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    secureSettings: SecureSettings,
    userTracker: UserTracker,
    private val batteryController: BatteryController,
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
), BatteryController.BatteryStateChangeCallback {
    private val secureSetting: SettingObserver

    init {
        object : SettingObserver(
            secureSettings,
            mHandler,
            Settings.Secure.DOZE_ALWAYS_ON,
            userTracker.userId
        ) {
            override fun handleValueChanged(value: Int, observedChange: Boolean) {
                handleRefreshState(value)
            }
        }.also { secureSetting = it }
        batteryController.observe(lifecycle, this)
    }

    override fun onPowerSaveChanged(isPowerSave: Boolean) {
        refreshState()
    }

    override fun handleDestroy() {
        super.handleDestroy()
        secureSetting.isListening = false
    }

    override fun isAvailable(): Boolean {
        return mContext.resources.getBoolean(
            com.android.internal.R.bool.config_dozeAlwaysOnDisplayAvailable
        )
    }

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState()
    }

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        secureSetting.isListening = listening
    }

    override fun handleUserSwitch(newUserId: Int) {
        secureSetting.setUserId(newUserId)
        handleRefreshState(secureSetting.value)
    }

    override fun handleClick(view: View?) {
        secureSetting.value = when {
            state.value -> 0
            else        -> 1
        }
        refreshState()
    }

    override fun getLongClickIntent(): Intent {
        return Intent(
            "android.settings.LOCK_SCREEN_SETTINGS"
        ).apply { addCategory(Intent.CATEGORY_DEFAULT) }
    }

    override fun getTileLabel(): CharSequence {
        return when {
            batteryController.isAodPowerSave -> {
                mContext.getString(R.string.quick_settings_always_on_display_powersave_label)
            }
            else -> {
                mContext.getString(R.string.quick_settings_always_on_display_label)
            }
        }
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val value = if (arg is Int) arg else secureSetting.value
        val enable = value != 0
        state.value = enable
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_always_on_display)
        state.state = when {
            batteryController.isAodPowerSave -> Tile.STATE_UNAVAILABLE
            enable                           -> Tile.STATE_ACTIVE
            else                             -> Tile.STATE_INACTIVE
        }
        state.contentDescription = when {
            state.value -> mContext.getString(R.string.accessibility_quick_settings_always_on_display_on)
            else        -> mContext.getString(R.string.accessibility_quick_settings_always_on_display_off)
        }
    }
}

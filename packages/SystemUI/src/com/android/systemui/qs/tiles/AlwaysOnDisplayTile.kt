/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2018 Android Ice Cold Project
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
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.SecureSetting
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
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
    secureSettings: SecureSettings
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
) {
    private val secureSetting: SecureSetting

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
        with(secureSetting) { isListening = listening }
    }

    override fun handleDestroy() {
        super.handleDestroy()
        with(secureSetting) { isListening = false }
    }

    override fun handleUserSwitch(newUserId: Int) {
        with(secureSetting) { setUserId(newUserId) }
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
        return mContext.getString(R.string.quick_settings_always_on_display_label)
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val value = if (arg is Int) arg else secureSetting.value
        val enable = value != 0
        state.value = enable
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_always_on_display)
        when {
            enable -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_always_on_display_on
                )
                state.state = Tile.STATE_ACTIVE
            }
            else   -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_always_on_display_off
                )
                state.state = Tile.STATE_INACTIVE
            }
        }
    }

    override fun composeChangeAnnouncement(): String {
        return when {
            state.value -> {
                with(mContext) {
                    getString(R.string.accessibility_quick_settings_always_on_display_changed_on)
                }
            }
            else        -> {
                with(mContext) {
                    getString(R.string.accessibility_quick_settings_always_on_display_changed_off)
                }
            }
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.BENZO
    }

    init {
        object : SecureSetting(
            secureSettings,
            mHandler,
            Settings.Secure.DOZE_ALWAYS_ON,
            host.userContext.getUserId()
        ) {
            override fun handleValueChanged(
                value: Int,
                observedChange: Boolean
            ) {
                handleRefreshState(value)
            }
        }.also { secureSetting = it }
    }
}

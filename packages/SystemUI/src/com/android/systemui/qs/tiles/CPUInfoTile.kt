/*
 * Copyright (C) 2019-2022 Benzo Rom
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
import com.android.systemui.util.settings.GlobalSettings
import javax.inject.Inject

/** Quick settings tile: Enable/Disable CPUInfo Service */
class CPUInfoTile
@Inject
constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    globalSettings: GlobalSettings,
    userTracker: UserTracker
) :
    QSTileImpl<QSTile.BooleanState>(
        host,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger
) {
    private val settingsObserver: SettingObserver

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState().apply { handlesLongClick = false }
    }

    override fun getTileLabel(): CharSequence =
        mContext.getText(R.string.quick_settings_cpuinfo_label)

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        with(settingsObserver) { isListening = listening }
    }

    override fun handleDestroy() {
        super.handleDestroy()
        with(settingsObserver) { isListening = false }
    }

    override fun handleClick(view: View?) {
        val value = if (settingsObserver.value != 1) 1 else 0
        with(settingsObserver) { setValue(value) }
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val enable = (arg as? Int ?: settingsObserver.value) != 0
        state.value = enable
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_cpuinfo)
        when {
            enable -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_cpuinfo_on
                )
                state.state = Tile.STATE_ACTIVE
            }
            else   -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_cpuinfo_off
                )
                state.state = Tile.STATE_INACTIVE
            }
        }
    }

    override fun getLongClickIntent(): Intent? = null

    init {
        object : SettingObserver(
            globalSettings,
            mHandler,
            Settings.Global.SHOW_CPU_OVERLAY,
            userTracker.userId
        ) {
            override fun handleValueChanged(value: Int, observedChange: Boolean) {
                handleRefreshState(value)
            }
        }.also { settingsObserver = it }
    }
}

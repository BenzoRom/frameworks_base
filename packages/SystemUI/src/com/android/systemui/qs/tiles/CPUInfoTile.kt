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
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.GlobalSetting
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import javax.inject.Inject

/** Quick settings tile: Enable/Disable CPUInfo Service  */
class CPUInfoTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
) {
    private val icon = ResourceIcon.get(R.drawable.ic_qs_cpuinfo)
    private val globalSetting: GlobalSetting

    override fun newTileState(): BooleanState {
        return BooleanState().also {
            it.handlesLongClick = false
        }
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getText(
            R.string.quick_settings_cpuinfo_label
        )
    }

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        globalSetting.setListening(listening)
    }

    override fun handleDestroy() {
        super.handleDestroy()
        globalSetting.setListening(false)
    }

    override fun handleClick(view: View?) {
        val value = if (globalSetting.value != 1) 1 else 0
        globalSetting.setValue(value)
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val value = if (arg is Int) arg else globalSetting.value
        val enable = value != 0
        state.value = enable
        state.label = tileLabel
        state.icon = icon
        if (enable) {
            state.contentDescription = mContext.getString(
                R.string.accessibility_quick_settings_cpuinfo_on
            )
            state.state = Tile.STATE_ACTIVE
        } else {
            state.contentDescription = mContext.getString(
                R.string.accessibility_quick_settings_cpuinfo_off
            )
            state.state = Tile.STATE_INACTIVE
        }
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.BENZO
    }

    override fun composeChangeAnnouncement(): String {
        return if (state.value) {
            mContext.getString(
                R.string.accessibility_quick_settings_cpuinfo_on
            )
        } else {
            mContext.getString(
                R.string.accessibility_quick_settings_cpuinfo_off
            )
        }
    }

    init {
        globalSetting = object : GlobalSetting(
            mContext,
            mHandler,
            Settings.Global.SHOW_CPU_OVERLAY
        ) {
            override fun handleValueChanged(value: Int) {
                handleRefreshState(value)
            }
        }
    }
}

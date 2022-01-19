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
package com.benzorom.systemui.qs.tiles

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import com.android.internal.logging.MetricsLogger
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tiles.BatterySaverTile
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

class BatterySaverTileGoogle @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    batteryController: BatteryController,
    secureSettings: SecureSettings
) : BatterySaverTile(
    host, backgroundLooper, mainHandler, falsingManager,
    metricsLogger, statusBarStateController, activityStarter,
    qsLogger, batteryController, secureSettings
) {
    private var isExtremeOn = false
    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        super.handleUpdateState(state, arg)
        if (state.state != Tile.STATE_ACTIVE || !isExtremeOn) {
            state.secondaryLabel = ""
        } else {
            state.secondaryLabel = mContext.getString(R.string.extreme_battery_saver_text)
        }
        state.stateDescription = state.secondaryLabel
    }

    override fun onExtremeBatterySaverChanged(isExtreme: Boolean) {
        if (isExtremeOn != isExtreme) {
            isExtremeOn = isExtreme
            refreshState()
        }
    }
}

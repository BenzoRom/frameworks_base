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

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.view.View
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.benzo.OnTheGoUtils
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.onthego.OnTheGoService
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import javax.inject.Inject

/** Quick settings tile: Enable/Disable OnTheGo Mode  */
class OnTheGoTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
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
    private val isOnTheGoEnabled: Boolean
        get() {
            val onthegoService = OnTheGoService::class.java.name
            return OnTheGoUtils.isServiceRunning(mContext, onthegoService)
        }

    override fun handleSetListening(listening: Boolean) {}
    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState().apply {
            handlesLongClick = false
        }
    }

    override fun handleClick(view: View?) {
        toggleService(isOnTheGoEnabled)
    }

    private fun toggleService(enabled: Boolean) {
        val intent = Intent()
        with(mContext) {
            intent.component = ComponentName(
                "com.android.systemui",
                "com.android.systemui.onthego.OnTheGoService"
            )
            when {
                enabled -> intent.action = "stop"
                else    -> intent.action = "start"
            }
            startService(intent)
        }
        refreshState()
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_onthego_label)
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        state.value = isOnTheGoEnabled
        state.state = when {
            state.value -> Tile.STATE_ACTIVE
            else        -> Tile.STATE_INACTIVE
        }
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_onthego)
        state.contentDescription = state.label
    }

    override fun getLongClickIntent(): Intent? = null
}

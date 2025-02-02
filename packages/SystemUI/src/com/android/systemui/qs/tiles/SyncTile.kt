/*
 * Copyright (C) 2015 The CyanogenMod Project
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

import android.content.ContentResolver.*
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Handler
import android.os.Looper
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

/** Quick settings tile: Sync  */
class SyncTile @Inject constructor(
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
    private var listening = false
    private val syncObserver = SyncStatusObserver { mHandler.post(this::refreshState) }
    private var syncObserverHandle: Any? = null

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState()
    }

    override fun handleClick(view: View?) {
        setMasterSyncAutomatically(!state.value)
        refreshState()
    }

    override fun getLongClickIntent(): Intent {
        return Intent(
            "android.settings.SYNC_SETTINGS"
        ).apply { addCategory(Intent.CATEGORY_DEFAULT) }
    }

    override fun handleSetListening(listening: Boolean) {
        if (this.listening == listening) return
        this.listening = listening
        when {
            listening -> {
                addStatusChangeListener(SYNC_OBSERVER_TYPE_SETTINGS, syncObserver)
            }
            else      -> {
                removeStatusChangeListener(syncObserverHandle)
                null
            }
        }.also { syncObserverHandle = it }
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_sync_label)
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        state.value = getMasterSyncAutomatically()
        state.label = tileLabel
        state.icon = ResourceIcon.get(R.drawable.ic_qs_sync)
        when {
            state.value -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_sync_on
                )
                state.state = Tile.STATE_ACTIVE
            }
            else        -> {
                state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_sync_off
                )
                state.state = Tile.STATE_INACTIVE
            }
        }
    }
}

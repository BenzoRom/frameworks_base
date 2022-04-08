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
import com.android.systemui.qs.tiles.RotationLockTile
import com.android.systemui.statusbar.policy.DevicePostureController
import com.android.systemui.statusbar.policy.DevicePostureController.DEVICE_POSTURE_CLOSED
import com.android.systemui.statusbar.policy.RotationLockController
import com.android.systemui.statusbar.policy.dagger.StatusBarPolicyModule.DEVICE_STATE_ROTATION_LOCK_DEFAULTS
import java.lang.StringBuilder
import javax.inject.Inject
import javax.inject.Named

class RotationLockTileGoogle @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    rotationLockController: RotationLockController,
    @Named(DEVICE_STATE_ROTATION_LOCK_DEFAULTS) deviceStateRotationLockDefaults: Array<String>,
    private val devicePostureController: DevicePostureController
) : RotationLockTile(
    host, backgroundLooper, mainHandler, falsingManager,
    metricsLogger, statusBarStateController, activityStarter,
    qsLogger, rotationLockController
) {
    private val isPerDeviceStateRotationLockEnabled: Boolean =
        deviceStateRotationLockDefaults.isNotEmpty()

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        super.handleUpdateState(state, arg)
        if (isPerDeviceStateRotationLockEnabled) {
            val secondaryLabelWithPosture = getSecondaryLabelWithPosture(state)
            state.secondaryLabel = secondaryLabelWithPosture
            state.stateDescription = secondaryLabelWithPosture
        }
    }

    private fun getSecondaryLabelWithPosture(state: QSTile.BooleanState): CharSequence {
        val labelBuilder = StringBuilder()
        labelBuilder.append(
            mContext.resources.getStringArray(R.array.tile_states_rotation)[state.state]
        )
        labelBuilder.append(" / ")
        if (devicePostureController.devicePosture == DEVICE_POSTURE_CLOSED) {
            labelBuilder.append(
                mContext.getString(R.string.quick_settings_rotation_posture_folded)
            )
        } else {
            labelBuilder.append(
                mContext.getString(R.string.quick_settings_rotation_posture_unfolded)
            )
        }
        return labelBuilder.toString()
    }
}

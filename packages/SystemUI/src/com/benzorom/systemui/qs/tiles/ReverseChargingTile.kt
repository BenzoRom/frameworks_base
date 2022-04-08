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

import android.content.Intent
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import android.service.quicksettings.Tile
import android.util.Log
import android.view.View
import android.widget.Switch
import com.android.internal.logging.MetricsLogger
import com.android.systemui.R
import com.android.systemui.Prefs
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.statusbar.policy.BatteryController
import javax.inject.Inject

class ReverseChargingTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val batteryController: BatteryController,
    private val thermalService: IThermalService
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
), BatteryController.BatteryStateChangeCallback {
    private var batteryLevel = 0
    private var isListening = false
    private var overHeat = false
    private var powerSave = false
    private var reverse = false
    private var thresholdLevel = 0
    private val icon = ResourceIcon.get(R.drawable.ic_qs_reverse_charging)
    private val thermalEventListener: IThermalEventListener =
        object : IThermalEventListener.Stub() {
            override fun notifyThrottling(temp: Temperature) {
                val status: Int = temp.getStatus()
                overHeat = status >= Temperature.THROTTLING_CRITICAL
                if (DEBUG) Log.d(TAG, "notifyThrottling(): status=$status")
            }
        }
    private val settingsObserver: ContentObserver =
        object : ContentObserver(mHandler) {
            override fun onChange(selfChange: Boolean) {
                updateThresholdLevel()
            }
        }

    override fun getMetricsCategory(): Int {
        return 0
    }

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState()
    }

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (isListening != listening) {
            isListening = listening
            if (listening) {
                updateThresholdLevel()
                mContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(
                        "advanced_battery_usage_amount"
                    ), false, settingsObserver
                )
                try {
                    thermalService.registerThermalEventListenerWithType(
                        thermalEventListener, Temperature.TYPE_SKIN
                    )
                } catch (ex: RemoteException) {
                    Log.e(TAG, "Could not register thermal event listener, exception: $ex")
                }
                overHeat = isOverHeat
            } else {
                mContext.contentResolver.unregisterContentObserver(settingsObserver)
                try {
                    thermalService.unregisterThermalEventListener(thermalEventListener)
                } catch (ex: RemoteException) {
                    Log.e(TAG, "Could not unregister thermal event listener, exception: $ex")
                }
            }
            if (DEBUG) Log.d(
                TAG, "handleSetListening(): rtx=" + (if (reverse) 1 else 0)
                        + ",level=" + batteryLevel + ",threshold=" + thresholdLevel
                        + ",listening=" + listening
            )
        }
    }

    override fun isAvailable(): Boolean {
        return batteryController.isReverseSupported
    }

    override fun getLongClickIntent(): Intent {
        val intent = Intent("android.settings.REVERSE_CHARGING_SETTINGS")
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        return intent
    }

    override fun handleClick(view: View?) {
        if (state.state != Tile.STATE_UNAVAILABLE) {
            reverse = !reverse
            if (DEBUG) Log.d(
                TAG,
                "handleClick(): rtx=" + (if (reverse) 1 else 0) + ",this=" + this
            )
            batteryController.setReverseState(reverse)
            showBottomSheetIfNecessary()
            refreshState()
        }
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.reverse_charging_title)
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val isWirelessCharging: Boolean = batteryController.isWirelessCharging
        val lowBattery = if (batteryLevel <= thresholdLevel) 1 else 0
        val reverseUnavailable = overHeat || powerSave || isWirelessCharging || lowBattery != 0
        state.value = !reverseUnavailable && reverse
        when {
            reverseUnavailable -> state.state = Tile.STATE_UNAVAILABLE
            reverse            -> state.state = Tile.STATE_ACTIVE
            else               -> state.state = Tile.STATE_INACTIVE
        }
        state.icon = icon
        state.label = tileLabel
        state.contentDescription = tileLabel
        state.expandedAccessibilityClassName = Switch::class.java.name
        when {
            overHeat           -> state.secondaryLabel =
                mContext.getString(R.string.too_hot_label)
            powerSave          -> state.secondaryLabel =
                mContext.getString(R.string.quick_settings_dark_mode_secondary_label_battery_saver)
            isWirelessCharging -> state.secondaryLabel =
                mContext.getString(R.string.wireless_charging_label)
            lowBattery != 0    -> state.secondaryLabel =
                mContext.getString(R.string.low_battery_label)
            else               -> state.secondaryLabel = null
        }
        if (DEBUG) Log.d(
            TAG,
            "handleUpdateState(): ps=" + (if (powerSave) 1 else 0)
                    + ",wlc=" + (if (isWirelessCharging) 1 else 0)
                    + ",low=" + lowBattery + ",over=" + (if (overHeat) 1 else 0)
                    + ",rtx=" + (if (reverse) 1 else 0)
                    + ",this=" + this
        )
    }

    override fun onBatteryLevelChanged(level: Int, pluggedIn: Boolean, charging: Boolean) {
        batteryLevel = level
        reverse = batteryController.isReverseOn
        if (DEBUG) Log.d(
            TAG,
            "onBatteryLevelChanged(): rtx=" + (if (reverse) 1 else 0)
                    + ",level=" + batteryLevel + ",threshold=" + thresholdLevel
        )
        refreshState()
    }

    override fun onPowerSaveChanged(isPowerSave: Boolean) {
        powerSave = isPowerSave
        refreshState()
    }

    override fun onReverseChanged(isReverse: Boolean, level: Int, name: String?) {
        if (DEBUG) Log.d(
            TAG,
            "onReverseChanged(): rtx=" + (if (isReverse) 1 else 0)
                    + ",level=" + level + ",name=" + name + ",this=" + this
        )
        reverse = isReverse
        refreshState()
    }

    private fun showBottomSheetIfNecessary() {
        if (!Prefs.getBoolean(mHost.userContext, "HasSeenReverseBottomSheet", false)) {
            val intent = Intent("android.settings.REVERSE_CHARGING_BOTTOM_SHEET")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            mActivityStarter.postStartActivityDismissingKeyguard(intent, 0)
            Prefs.putBoolean(mHost.userContext, "HasSeenReverseBottomSheet", true)
        }
    }

    fun updateThresholdLevel() {
        thresholdLevel = Settings.Global.getInt(
            mContext.contentResolver,
            "advanced_battery_usage_amount",
            2
        ).times(5)
        if (DEBUG) Log.d(
            TAG, "updateThresholdLevel(): rtx=" + (if (reverse) 1 else 0)
                    + ",level=" + batteryLevel + ",threshold=" + thresholdLevel
        )
    }

    private val isOverHeat: Boolean
        get() {
            try {
                for (temp in thermalService.getCurrentTemperaturesWithType(Temperature.TYPE_SKIN)) {
                    if (temp.getStatus() >= Temperature.THROTTLING_CRITICAL) {
                        Log.w(
                            TAG, "isOverHeat(): current skin status = " + temp.getStatus()
                                .toString() + ", temperature = " + temp.getValue()
                        )
                        return true
                    }
                }
            } catch (ex: RemoteException) {
                Log.w(TAG, "isOverHeat(): $ex")
            }
            return false
        }

    init {
        batteryController.observe(lifecycle, this)
    }
}

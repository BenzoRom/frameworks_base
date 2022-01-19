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
import android.os.Handler
import android.os.IThermalEventListener
import android.os.IThermalService
import android.os.Looper
import android.os.RemoteException
import android.os.Temperature
import android.provider.Settings
import android.service.quicksettings.Tile
import android.util.Log
import android.view.View
import android.widget.Switch
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.Prefs
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
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
) : QSTileImpl<BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
), BatteryController.BatteryStateChangeCallback {
    private val icon = ResourceIcon.get(R.drawable.ic_qs_reverse_charging)
    private var batteryLevel = 0
    private var isListening = false
    private var overHeat = false
    private var powerSave = false
    private var reverse = false
    private var thresholdLevel = 0
    private val thermalEventListener = object : IThermalEventListener.Stub() {
        override fun notifyThrottling(temp: Temperature) {
            val status: Int = temp.getStatus()
            overHeat = status >= Temperature.THROTTLING_EMERGENCY
        }
    }
    private val settingsObserver: ContentObserver = object : ContentObserver(mHandler) {
        override fun onChange(selfChange: Boolean) {
            updateThresholdLevel()
        }
    }

    override fun newTileState(): BooleanState {
        return BooleanState()
    }

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (isListening != listening) {
            isListening = listening
            if (listening) {
                updateThresholdLevel()
                mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("advanced_battery_usage_amount"),
                    false, settingsObserver
                )
                try {
                    thermalService.registerThermalEventListenerWithType(
                        thermalEventListener,
                        Temperature.TYPE_SKIN
                    )
                } catch (e: RemoteException) {
                    Log.e(
                        TAG,
                        "Could not register thermal event listener, exception: $e"
                    )
                }
                overHeat = isOverHeat
            } else {
                mContext.getContentResolver().unregisterContentObserver(settingsObserver)
                try {
                    thermalService.unregisterThermalEventListener(thermalEventListener)
                } catch (ex: RemoteException) {
                    Log.e(
                        TAG,
                        "Could not unregister thermal event listener, exception: $ex"
                    )
                }
            }
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.QS_REVERSE_CHARGING
    }

    override fun isAvailable(): Boolean {
        return batteryController.isReverseSupported()
    }

    override fun getLongClickIntent(): Intent {
        val intent = Intent("android.settings.REVERSE_CHARGING_SETTINGS")
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        return intent
    }

    override fun handleClick(view: View?) {
        if (state?.state != Tile.STATE_UNAVAILABLE) {
            reverse = !reverse
            batteryController.setReverseState(reverse)
            showBottomSheetIfNecessary()
            refreshState()
        }
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.reverse_charging_title)
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val wirelessCharging: Boolean = batteryController.isWirelessCharging()
        val lowBattery = if (batteryLevel <= thresholdLevel) 1 else 0
        val reverseUnavailable = overHeat || powerSave || wirelessCharging || lowBattery != 0
        state.value = !reverseUnavailable && reverse
        state.state =
            if (reverseUnavailable) Tile.STATE_UNAVAILABLE else if (state.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        state.icon = icon
        state.label = tileLabel
        state.contentDescription = tileLabel
        state.expandedAccessibilityClassName = Switch::class.java.name
        if (overHeat) {
            state.secondaryLabel = mContext.getString(
                R.string.too_hot_label
            )
        } else if (powerSave) {
            state.secondaryLabel = mContext.getString(
                R.string.quick_settings_dark_mode_secondary_label_battery_saver
            )
        } else if (wirelessCharging) {
            state.secondaryLabel = mContext.getString(
                R.string.wireless_charging_label
            )
        } else {
            state.secondaryLabel = if (lowBattery != 0) mContext.getString(
                R.string.low_battery_label
            ) else null
        }
    }

    override fun onBatteryLevelChanged(level: Int, pluggedIn: Boolean, charging: Boolean) {
        batteryLevel = level
        reverse = batteryController.isReverseOn()
        refreshState()
    }

    override fun onPowerSaveChanged(isPowerSave: Boolean) {
        powerSave = isPowerSave
        refreshState()
    }

    override fun onReverseChanged(isReverse: Boolean, level: Int, name: String?) {
        reverse = isReverse
        refreshState()
    }

    private fun showBottomSheetIfNecessary() {
        if (!Prefs.getBoolean(mHost.getUserContext(), "HasSeenReverseBottomSheet", false)) {
            val intent = Intent("android.settings.REVERSE_CHARGING_BOTTOM_SHEET")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            mActivityStarter.postStartActivityDismissingKeyguard(intent, 0)
            Prefs.putBoolean(mHost.getUserContext(), "HasSeenReverseBottomSheet", true)
        }
    }

    private fun updateThresholdLevel() {
        thresholdLevel = Settings.Global.getInt(
            mContext.getContentResolver(),
            "advanced_battery_usage_amount",
            2
        ) * 5
    }

    private val isOverHeat: Boolean
        get() {
            try {
                for (temp in thermalService.getCurrentTemperaturesWithType(Temperature.TYPE_SKIN)) {
                    if (temp.getStatus() >= Temperature.THROTTLING_EMERGENCY) {
                        Log.w(
                            TAG,
                            "isOverHeat(): current skin status = " + temp.getStatus()
                                .toString() + ", temperature = " + temp.getValue()
                        )
                        return true
                    }
                }
            } catch (e: RemoteException) {
                Log.w(TAG, "isOverHeat(): $e")
            }
            return false
        }

    init {
        batteryController.observe(lifecycle, this)
    }
}

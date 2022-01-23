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
package com.benzorom.systemui.qs.tileimpl

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.external.CustomTile
import com.android.systemui.qs.tileimpl.QSFactoryImpl
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AirplaneModeTile
import com.android.systemui.qs.tiles.AlarmTile
import com.android.systemui.qs.tiles.BluetoothTile
import com.android.systemui.qs.tiles.CameraToggleTile
import com.android.systemui.qs.tiles.CastTile
import com.android.systemui.qs.tiles.CellularTile
import com.android.systemui.qs.tiles.ColorInversionTile
import com.android.systemui.qs.tiles.CPUInfoTile
import com.android.systemui.qs.tiles.DataSaverTile
import com.android.systemui.qs.tiles.DeviceControlsTile
import com.android.systemui.qs.tiles.DndTile
import com.android.systemui.qs.tiles.FlashlightTile
import com.android.systemui.qs.tiles.HotspotTile
import com.android.systemui.qs.tiles.InternetTile
import com.android.systemui.qs.tiles.LocationTile
import com.android.systemui.qs.tiles.MicrophoneToggleTile
import com.android.systemui.qs.tiles.NfcTile
import com.android.systemui.qs.tiles.NightDisplayTile
import com.android.systemui.qs.tiles.OnTheGoTile
import com.android.systemui.qs.tiles.QuickAccessWalletTile
import com.android.systemui.qs.tiles.ReduceBrightColorsTile
import com.android.systemui.qs.tiles.ScreenRecordTile
import com.android.systemui.qs.tiles.UiModeNightTile
import com.android.systemui.qs.tiles.UserTile
import com.android.systemui.qs.tiles.WifiTile
import com.android.systemui.qs.tiles.WorkModeTile
import com.android.systemui.util.leak.GarbageMonitor
import com.benzorom.systemui.qs.tiles.BatterySaverTileGoogle
import com.benzorom.systemui.qs.tiles.OverlayToggleTile
import com.benzorom.systemui.qs.tiles.ReverseChargingTile
import com.benzorom.systemui.qs.tiles.RotationLockTileGoogle
import javax.inject.Inject
import javax.inject.Provider
import dagger.Lazy

@SysUISingleton
class QSFactoryImplGoogle @Inject constructor(
    qsHostLazy: Lazy<QSHost>,
    customTileBuilderProvider: Provider<CustomTile.Builder>,
    wifiTileProvider: Provider<WifiTile>,
    internetTileProvider: Provider<InternetTile>,
    bluetoothTileProvider: Provider<BluetoothTile>,
    cellularTileProvider: Provider<CellularTile>,
    dndTileProvider: Provider<DndTile>,
    colorInversionTileProvider: Provider<ColorInversionTile>,
    airplaneModeTileProvider: Provider<AirplaneModeTile>,
    workModeTileProvider: Provider<WorkModeTile>,
    private val rotationLockTileGoogleProvider: Provider<RotationLockTileGoogle>,
    flashlightTileProvider: Provider<FlashlightTile>,
    locationTileProvider: Provider<LocationTile>,
    castTileProvider: Provider<CastTile>,
    hotspotTileProvider: Provider<HotspotTile>,
    userTileProvider: Provider<UserTile>,
    private val batterySaverTileGoogleProvider: Provider<BatterySaverTileGoogle>,
    dataSaverTileProvider: Provider<DataSaverTile>,
    nightDisplayTileProvider: Provider<NightDisplayTile>,
    nfcTileProvider: Provider<NfcTile>,
    memoryTileProvider: Provider<GarbageMonitor.MemoryTile>,
    uiModeNightTileProvider: Provider<UiModeNightTile>,
    screenRecordTileProvider: Provider<ScreenRecordTile>,
    reduceBrightColorsTileProvider: Provider<ReduceBrightColorsTile>,
    cameraToggleTileProvider: Provider<CameraToggleTile>,
    microphoneToggleTileProvider: Provider<MicrophoneToggleTile>,
    deviceControlsTileProvider: Provider<DeviceControlsTile>,
    alarmTileProvider: Provider<AlarmTile>,
    private val overlayToggleTileProvider: Provider<OverlayToggleTile>,
    quickAccessWalletTileProvider: Provider<QuickAccessWalletTile>,
    private val reverseChargingTileProvider: Provider<ReverseChargingTile>,
    cpuInfoTileProvider: Provider<CPUInfoTile>,
    onTheGoTileProvider: Provider<OnTheGoTile>
) : QSFactoryImpl(
    qsHostLazy,
    customTileBuilderProvider,
    wifiTileProvider,
    internetTileProvider,
    bluetoothTileProvider,
    cellularTileProvider,
    dndTileProvider,
    colorInversionTileProvider,
    airplaneModeTileProvider,
    workModeTileProvider,
    rotationLockTileGoogleProvider::get,
    flashlightTileProvider,
    locationTileProvider,
    castTileProvider,
    hotspotTileProvider,
    userTileProvider,
    batterySaverTileGoogleProvider::get,
    dataSaverTileProvider,
    nightDisplayTileProvider,
    nfcTileProvider,
    memoryTileProvider,
    uiModeNightTileProvider,
    screenRecordTileProvider,
    reduceBrightColorsTileProvider,
    cameraToggleTileProvider,
    microphoneToggleTileProvider,
    deviceControlsTileProvider,
    alarmTileProvider,
    quickAccessWalletTileProvider,
    cpuInfoTileProvider,
    onTheGoTileProvider
) {
    override fun createTile(tileSpec: String): QSTile {
        val tile = createTileInternal(tileSpec)
        return tile ?: super.createTile(tileSpec)
    }

    private fun createTileInternal(tileSpec: String): QSTileImpl<*>? {
        return when (tileSpec) {
            "rotation" -> rotationLockTileGoogleProvider.get()
            "ott"      -> overlayToggleTileProvider.get()
            "reverse"  -> reverseChargingTileProvider.get()
            else       -> null
        }
    }
}

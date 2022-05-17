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
package com.benzorom.systemui.dagger

import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IThermalService
import android.os.PowerManager
import android.os.Process.THREAD_PRIORITY_DISPLAY
import android.os.Process.setThreadPriority
import android.os.ServiceManager
import com.android.keyguard.KeyguardViewController
import com.android.systemui.R
import com.android.systemui.Dependency.ALLOW_NOTIFICATION_LONG_PRESS_NAME
import com.android.systemui.Dependency.LEAK_REPORT_EMAIL_NAME
import com.android.systemui.assist.AssistManager
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.dock.DockManager
import com.android.systemui.doze.DozeHost
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogBufferFactory
import com.android.systemui.navigationbar.NavigationBarOverlayController
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.qs.QSFactory
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.EnhancedEstimates
import com.android.systemui.recents.Recents
import com.android.systemui.recents.RecentsImplementation
import com.android.systemui.settings.UserContentResolverProvider
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.notification.NotificationEntryManager.KeyguardEnvironment
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager
import com.android.systemui.statusbar.phone.*
import com.android.systemui.statusbar.policy.*
import com.android.systemui.util.concurrency.Execution
import com.android.systemui.volume.dagger.VolumeModule
import com.benzorom.systemui.assist.AssistManagerGoogle
import com.benzorom.systemui.fingerprint.UdfpsHbmController
import com.benzorom.systemui.fingerprint.UdfpsLhbmProvider
import com.benzorom.systemui.log.dagger.NotifVoiceReplyLog
import com.benzorom.systemui.power.PowerModuleGoogle
import com.benzorom.systemui.qs.dagger.QSModuleGoogle
import com.benzorom.systemui.qs.tileimpl.QSFactoryImplGoogle
import com.benzorom.systemui.statusbar.phone.StatusBarGoogle
import com.benzorom.systemui.statusbar.policy.BatteryControllerImplGoogle
import com.google.android.systemui.LiveWallpaperScrimController
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle
import com.google.android.systemui.dreamliner.DockObserver
import com.google.android.systemui.gamedashboard.EntryPointController
import com.google.android.systemui.power.EnhancedEstimatesGoogleImpl
import com.google.android.systemui.reversecharging.ReverseChargingController
import com.google.android.systemui.reversecharging.ReverseWirelessCharger
import com.google.android.systemui.smartspace.BcSmartspaceDataProvider
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Named
import dagger.Binds
import dagger.Module
import dagger.Provides

/**
 * A dagger module for injecting Google's implementations of components of System UI.
 */
@Module(
    includes = [
        PowerModuleGoogle::class,
        QSModuleGoogle::class,
        VolumeModule::class
    ],
    subcomponents = []
)
abstract class SystemUIGoogleModule {

    @Module
    companion object {

        @Provides
        @SysUISingleton
        @Named(ALLOW_NOTIFICATION_LONG_PRESS_NAME)
        fun provideAllowNotificationLongPress(): Boolean = true

        @Provides
        @SysUISingleton
        @Named(LEAK_REPORT_EMAIL_NAME)
        fun provideLeakReportEmail(): String = "buganizer-system+187317@google.com"

        @Provides
        @SysUISingleton
        fun provideBatteryController(
            context: Context,
            enhancedEstimates: EnhancedEstimates,
            powerManager: PowerManager,
            broadcastDispatcher: BroadcastDispatcher,
            demoModeController: DemoModeController,
            @Main mainHandler: Handler,
            @Background bgHandler: Handler,
            contentResolver: UserContentResolverProvider,
            reverseChargingController: ReverseChargingController
        ): BatteryController {
            val bcGoogle: BatteryControllerImplGoogle =
                BatteryControllerImplGoogle(
                    context,
                    enhancedEstimates,
                    powerManager,
                    broadcastDispatcher,
                    demoModeController,
                    mainHandler,
                    bgHandler,
                    contentResolver,
                    reverseChargingController
                )
            bcGoogle.init()
            return bcGoogle
        }

        @Provides
        @SysUISingleton
        fun provideSensorPrivacyController(
            sensorPrivacyManager: SensorPrivacyManager
        ): SensorPrivacyController {
            val spC: SensorPrivacyControllerImpl =
                SensorPrivacyControllerImpl(sensorPrivacyManager)
            spC.init()
            return spC
        }

        @Provides
        @SysUISingleton
        fun provideIndividualSensorPrivacyController(
            sensorPrivacyManager: SensorPrivacyManager
        ): IndividualSensorPrivacyController {
            val spC: IndividualSensorPrivacyControllerImpl =
                IndividualSensorPrivacyControllerImpl(sensorPrivacyManager)
            spC.init()
            return spC
        }

        @Provides
        @SysUISingleton
        fun provideReverseWirelessCharger(
            context: Context
        ): Optional<ReverseWirelessCharger> {
            return if (context.resources.getBoolean(
                    R.bool.config_wlc_support_enabled
                )
            ) Optional.of(ReverseWirelessCharger(context)) else Optional.empty()
        }

        @Provides
        @SysUISingleton
        fun provideUsbManager(context: Context): Optional<UsbManager> {
            return Optional.ofNullable(context.getSystemService(UsbManager::class.java))
        }

        @Provides
        @SysUISingleton
        fun provideBcSmartspaceDataPlugin(): BcSmartspaceDataPlugin = BcSmartspaceDataProvider()

        @Provides
        @SysUISingleton
        fun provideIThermalService(): IThermalService {
            return IThermalService.Stub.asInterface(ServiceManager.getService("thermalservice"))
        }

        @Provides
        @SysUISingleton
        fun provideHeadsUpManagerPhone(
            context: Context,
            statusBarStateController: StatusBarStateController,
            bypassController: KeyguardBypassController,
            groupManager: GroupMembershipManager,
            configurationController: ConfigurationController
        ): HeadsUpManagerPhone {
            return HeadsUpManagerPhone(
                context,
                statusBarStateController,
                bypassController,
                groupManager,
                configurationController
            )
        }

        @Provides
        @SysUISingleton
        fun provideRecents(
            context: Context,
            recentsImplementation: RecentsImplementation,
            commandQueue: CommandQueue
        ): Recents {
            return Recents(context, recentsImplementation, commandQueue)
        }

        @Provides
        @SysUISingleton
        fun provideUdfpsHbmProvider(
            context: Context,
            execution: Execution,
            @Main mainHandler: Handler,
            lhbmProvider: UdfpsLhbmProvider,
            authController: AuthController,
            displayManager: DisplayManager
        ): UdfpsHbmProvider {
            return UdfpsHbmController(
                context,
                execution,
                mainHandler,
                Executors.newSingleThreadExecutor {
                    Thread {
                        setThreadPriority(THREAD_PRIORITY_DISPLAY)
                        it.run()
                    }
                },
                lhbmProvider,
                authController,
                displayManager
            )
        }

        @Provides
        @SysUISingleton
        fun provideUdfpsLhbm(): UdfpsLhbmProvider = UdfpsLhbmProvider()

        @Provides
        @SysUISingleton
        fun provideDeviceProvisionedController(
            deviceProvisionedController: DeviceProvisionedControllerImpl
        ): DeviceProvisionedController {
            deviceProvisionedController.init()
            return deviceProvisionedController
        }

        @Provides
        @SysUISingleton
        @NotifVoiceReplyLog
        fun provideNotifVoiceReplyLogBuffer(factory: LogBufferFactory): LogBuffer {
            return factory.create("NotifVoiceReplyLog", 500)
        }
    }

    @Binds
    abstract fun bindEnhancedEstimates(enhancedEstimates: EnhancedEstimatesGoogleImpl): EnhancedEstimates

    @Binds
    abstract fun bindNotificationLockscreenUserManager(
        notificationLockscreenUserManager: NotificationLockscreenUserManagerGoogle
    ): NotificationLockscreenUserManager

    @Binds
    @SysUISingleton
    abstract fun bindQSFactory(qsFactoryImpl: QSFactoryImplGoogle): QSFactory

    @Binds
    abstract fun provideDockManager(dockManager: DockObserver): DockManager

    @Binds
    abstract fun bindKeyguardEnvironment(keyguardEnvironment: KeyguardEnvironmentImpl): KeyguardEnvironment

    @Binds
    abstract fun provideShadeController(shadeController: ShadeControllerImpl): ShadeController

    @Binds
    abstract fun bindHeadsUpManagerPhone(headsUpManagerPhone: HeadsUpManagerPhone): HeadsUpManager

    @Binds
    abstract fun bindKeyguardViewController(
        statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    ): KeyguardViewController

    @Binds
    abstract fun bindNotificationShadeController(
        notificationShadeWindowController: NotificationShadeWindowControllerImpl
    ): NotificationShadeWindowController

    @Binds
    abstract fun provideDozeHost(dozeServiceHost: DozeServiceHost): DozeHost

    @Binds
    @SysUISingleton
    abstract fun bindAssistManagerGoogle(assistManager: AssistManagerGoogle): AssistManager

    @Binds
    abstract fun bindKeyguardIndicationControllerGoogle(
        keyguardIndicationControllerGoogle: KeyguardIndicationControllerGoogle
    ): KeyguardIndicationController

    @Binds
    abstract fun bindEntryPointController(assistManager: EntryPointController): NavigationBarOverlayController

    @Binds
    @SysUISingleton
    abstract fun bindScrimController(liveWallpaperScrimController: LiveWallpaperScrimController): ScrimController

    @Binds
    abstract fun bindStatusBar(statusBar: StatusBarGoogle): StatusBar
}

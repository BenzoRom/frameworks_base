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

import android.app.Activity
import android.app.Service
import com.android.systemui.LatencyTester
import com.android.systemui.ScreenDecorations
import com.android.systemui.SliceBroadcastRelayHandler
import com.android.systemui.SystemUI
import com.android.systemui.accessibility.SystemActions
import com.android.systemui.accessibility.WindowMagnification
import com.android.systemui.biometrics.AuthController
import com.android.systemui.dagger.GlobalRootComponent
import com.android.systemui.globalactions.GlobalActionsComponent
import com.android.systemui.keyguard.KeyguardViewMediator
import com.android.systemui.keyguard.dagger.KeyguardModule
import com.android.systemui.media.systemsounds.HomeSoundEffectController
import com.android.systemui.power.PowerUI
import com.android.systemui.privacy.television.TvOngoingPrivacyChip
import com.android.systemui.recents.Recents
import com.android.systemui.recents.RecentsModule
import com.android.systemui.shortcut.ShortcutKeyDispatcher
import com.android.systemui.statusbar.notification.InstantAppNotifier
import com.android.systemui.statusbar.phone.StatusBar
import com.android.systemui.statusbar.tv.TvStatusBar
import com.android.systemui.statusbar.tv.notifications.TvNotificationPanel
import com.android.systemui.theme.ThemeOverlayController
import com.android.systemui.toast.ToastUI
import com.android.systemui.util.leak.GarbageMonitor
import com.android.systemui.volume.VolumeUI
import com.android.systemui.wmshell.WMShell
import com.google.android.systemui.statusbar.NotificationVoiceReplyManagerService
import com.google.android.systemui.theme.ThemeOverlayControllerGoogle
import com.benzorom.systemui.GoogleServices
import com.benzorom.systemui.columbus.ColumbusTargetRequestServiceWrapper
import com.benzorom.systemui.gamedashboard.GameMenuActivityWrapper
import com.benzorom.systemui.statusbar.phone.StatusBarGoogle
import com.benzorom.systemui.statusbar.phone.StatusBarGoogleModule
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module(
    includes = [
        RecentsModule::class,
        StatusBarGoogleModule::class,
        KeyguardModule::class
    ])
abstract class SystemUIGoogleBinder {
    @Binds
    abstract fun bindGlobalRootComponent(
        globalRootComponent: SysUIGoogleGlobalRootComponent
    ): GlobalRootComponent

    /** Inject into AuthController. */
    @Binds
    @IntoMap
    @ClassKey(AuthController::class)
    abstract fun bindAuthController(service: AuthController): SystemUI

    /** Inject into GarbageMonitor.Service. */
    @Binds
    @IntoMap
    @ClassKey(GarbageMonitor.Service::class)
    abstract fun bindGarbageMonitorService(sysui: GarbageMonitor.Service): SystemUI

    /** Inject into GlobalActionsComponent. */
    @Binds
    @IntoMap
    @ClassKey(GlobalActionsComponent::class)
    abstract fun bindGlobalActionsComponent(sysui: GlobalActionsComponent): SystemUI

    /** Inject into InstantAppNotifier. */
    @Binds
    @IntoMap
    @ClassKey(InstantAppNotifier::class)
    abstract fun bindInstantAppNotifier(sysui: InstantAppNotifier): SystemUI

    /** Inject into KeyguardViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(KeyguardViewMediator::class)
    abstract fun bindKeyguardViewMediator(sysui: KeyguardViewMediator): SystemUI

    /** Inject into LatencyTests. */
    @Binds
    @IntoMap
    @ClassKey(LatencyTester::class)
    abstract fun bindLatencyTester(sysui: LatencyTester): SystemUI

    /** Inject into PowerUI. */
    @Binds
    @IntoMap
    @ClassKey(PowerUI::class)
    abstract fun bindPowerUI(sysui: PowerUI): SystemUI

    /** Inject into Recents. */
    @Binds
    @IntoMap
    @ClassKey(Recents::class)
    abstract fun bindRecents(sysui: Recents): SystemUI

    /** Inject into ScreenDecorations. */
    @Binds
    @IntoMap
    @ClassKey(ScreenDecorations::class)
    abstract fun bindScreenDecorations(sysui: ScreenDecorations): SystemUI

    /** Inject into ShortcutKeyDispatcher. */
    @Binds
    @IntoMap
    @ClassKey(ShortcutKeyDispatcher::class)
    abstract fun bindsShortcutKeyDispatcher(sysui: ShortcutKeyDispatcher): SystemUI

    /** Inject into SliceBroadcastRelayHandler. */
    @Binds
    @IntoMap
    @ClassKey(SliceBroadcastRelayHandler::class)
    abstract fun bindSliceBroadcastRelayHandler(sysui: SliceBroadcastRelayHandler): SystemUI

    /** Inject into StatusBar. */
    @Binds
    @IntoMap
    @ClassKey(StatusBar::class)
    abstract fun bindsStatusBar(sysui: StatusBarGoogle): SystemUI

    /** Inject into StatusBarGoogle. */
    @Binds
    @IntoMap
    @ClassKey(StatusBarGoogle::class)
    abstract fun bindsStatusBarGoogle(sysui: StatusBarGoogle): SystemUI

    /** Inject into SystemActions. */
    @Binds
    @IntoMap
    @ClassKey(SystemActions::class)
    abstract fun bindSystemActions(sysui: SystemActions): SystemUI

    /** Inject into ThemeOverlayController. */
    @Binds
    @IntoMap
    @ClassKey(ThemeOverlayController::class)
    abstract fun bindThemeOverlayController(sysui: ThemeOverlayControllerGoogle): SystemUI

    /** Inject into ToastUI. */
    @Binds
    @IntoMap
    @ClassKey(ToastUI::class)
    abstract fun bindToastUI(service: ToastUI): SystemUI

    /** Inject into TvStatusBar. */
    @Binds
    @IntoMap
    @ClassKey(TvStatusBar::class)
    abstract fun bindsTvStatusBar(sysui: TvStatusBar): SystemUI

    /** Inject into TvNotificationPanel. */
    @Binds
    @IntoMap
    @ClassKey(TvNotificationPanel::class)
    abstract fun bindsTvNotificationPanel(sysui: TvNotificationPanel): SystemUI

    /** Inject into TvOngoingPrivacyChip. */
    @Binds
    @IntoMap
    @ClassKey(TvOngoingPrivacyChip::class)
    abstract fun bindsTvOngoingPrivacyChip(sysui: TvOngoingPrivacyChip): SystemUI

    /** Inject into VolumeUI. */
    @Binds
    @IntoMap
    @ClassKey(VolumeUI::class)
    abstract fun bindVolumeUI(sysui: VolumeUI): SystemUI

    /** Inject into WindowMagnification. */
    @Binds
    @IntoMap
    @ClassKey(WindowMagnification::class)
    abstract fun bindWindowMagnification(sysui: WindowMagnification): SystemUI

    /** Inject into WMShell. */
    @Binds
    @IntoMap
    @ClassKey(WMShell::class)
    abstract fun bindWMShell(sysui: WMShell): SystemUI

    /** Inject into HomeSoundEffectController. */
    @Binds
    @IntoMap
    @ClassKey(HomeSoundEffectController::class)
    abstract fun bindHomeSoundEffectController(sysui: HomeSoundEffectController): SystemUI

    /** Inject into ColumbusTargetRequestService. */
    @Binds
    @IntoMap
    @ClassKey(ColumbusTargetRequestServiceWrapper::class)
    abstract fun bindColumbusTargetRequestService(service: ColumbusTargetRequestServiceWrapper): Service

    /** Inject into GameMenuActivity. */
    @Binds
    @IntoMap
    @ClassKey(GameMenuActivityWrapper::class)
    abstract fun bindGameMenuActivity(activity: GameMenuActivityWrapper): Activity

    /** Inject into GoogleServices. */
    @Binds
    @IntoMap
    @ClassKey(GoogleServices::class)
    abstract fun bindGoogleServices(sysui: GoogleServices): SystemUI

    /** Inject into NotificationVoiceReplyManagerService. */
    @Binds
    @IntoMap
    @ClassKey(NotificationVoiceReplyManagerService::class)
    abstract fun bindVoiceReplyManagerService(service: NotificationVoiceReplyManagerService): Service
}

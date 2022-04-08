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
package com.benzorom.systemui.columbus;

import static com.benzorom.systemui.Dependency.*;

import android.app.IActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserManager;
import android.os.Vibrator;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.logging.UiEventLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.recents.Recents;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.commandline.CommandRegistry;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.telephony.TelephonyListenerManager;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.sensors.ProximitySensor;
import com.google.android.systemui.columbus.ColumbusContentObserver;
import com.google.android.systemui.columbus.ColumbusService;
import com.google.android.systemui.columbus.ColumbusServiceWrapper;
import com.google.android.systemui.columbus.ColumbusSettings;
import com.google.android.systemui.columbus.ColumbusStructuredDataManager;
import com.google.android.systemui.columbus.ContentResolverWrapper;
import com.google.android.systemui.columbus.ColumbusTargetRequestService;
import com.google.android.systemui.columbus.PowerManagerWrapper;
import com.google.android.systemui.columbus.actions.Action;
import com.google.android.systemui.columbus.actions.DismissTimer;
import com.google.android.systemui.columbus.actions.LaunchApp;
import com.google.android.systemui.columbus.actions.LaunchOpa;
import com.google.android.systemui.columbus.actions.LaunchOverview;
import com.google.android.systemui.columbus.actions.ManageMedia;
import com.google.android.systemui.columbus.actions.OpenNotificationShade;
import com.google.android.systemui.columbus.actions.SettingsAction;
import com.google.android.systemui.columbus.actions.SilenceCall;
import com.google.android.systemui.columbus.actions.SnoozeAlarm;
import com.google.android.systemui.columbus.actions.TakeScreenshot;
import com.google.android.systemui.columbus.actions.UnpinNotifications;
import com.google.android.systemui.columbus.actions.UserAction;
import com.google.android.systemui.columbus.actions.UserSelectedAction;
import com.google.android.systemui.columbus.feedback.AssistInvocationEffect;
import com.google.android.systemui.columbus.feedback.FeedbackEffect;
import com.google.android.systemui.columbus.feedback.HapticClick;
import com.google.android.systemui.columbus.feedback.UserActivity;
import com.google.android.systemui.columbus.gates.CameraVisibility;
import com.google.android.systemui.columbus.gates.ChargingState;
import com.google.android.systemui.columbus.gates.FlagEnabled;
import com.google.android.systemui.columbus.gates.Gate;
import com.google.android.systemui.columbus.gates.KeyguardProximity;
import com.google.android.systemui.columbus.gates.KeyguardVisibility;
import com.google.android.systemui.columbus.gates.PowerSaveState;
import com.google.android.systemui.columbus.gates.PowerState;
import com.google.android.systemui.columbus.gates.Proximity;
import com.google.android.systemui.columbus.gates.ScreenTouch;
import com.google.android.systemui.columbus.gates.SetupWizard;
import com.google.android.systemui.columbus.gates.SilenceAlertsDisabled;
import com.google.android.systemui.columbus.gates.SystemKeyPress;
import com.google.android.systemui.columbus.gates.TelephonyActivity;
import com.google.android.systemui.columbus.gates.UsbState;
import com.google.android.systemui.columbus.gates.VrMode;
import com.google.android.systemui.columbus.sensors.CHREGestureSensor;
import com.google.android.systemui.columbus.sensors.GestureController;
import com.google.android.systemui.columbus.sensors.GestureSensor;
import com.google.android.systemui.columbus.sensors.GestureSensorImpl;
import com.google.android.systemui.columbus.sensors.config.Adjustment;
import com.google.android.systemui.columbus.sensors.config.GestureConfiguration;
import com.google.android.systemui.columbus.sensors.config.LowSensitivitySettingAdjustment;
import com.google.android.systemui.columbus.sensors.config.SensorConfiguration;

import java.util.*;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Named;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public interface ColumbusModule {
    @Provides
    @SysUISingleton
    static ColumbusServiceWrapper provideColumbusServiceWrapper(
            ColumbusSettings columbusSettings,
            Lazy<ColumbusService> columbusService,
            Lazy<SettingsAction> settingsAction,
            Lazy<ColumbusStructuredDataManager> columbusStructuredDataManager) {
        return new ColumbusServiceWrapper(
                columbusSettings,
                columbusService,
                settingsAction,
                columbusStructuredDataManager);
    }

    @Provides
    @SysUISingleton
    static ColumbusService provideColumbusService(
            List<Action> actions,
            Set<FeedbackEffect> effects,
            @Named(COLUMBUS_GATES) Set<Gate> gates,
            GestureController gestureController,
            PowerManagerWrapper powerManager) {
        return new ColumbusService(
                actions,
                effects,
                gates,
                gestureController,
                powerManager);
    }

    @Provides
    @SysUISingleton
    static ColumbusStructuredDataManager provideColumbusStructuredDataManager(
            Context context,
            UserTracker userTracker,
            @Background Executor bgExecutor) {
        return new ColumbusStructuredDataManager(context, userTracker, bgExecutor);
    }

    @Provides
    @SysUISingleton
    static ColumbusSettings provideColumbusSettings(
            Context context,
            UserTracker userTracker,
            ColumbusContentObserver.Factory contentObserverFactory) {
        return new ColumbusSettings(context, userTracker, contentObserverFactory);
    }

    @Provides
    @SysUISingleton
    static ColumbusContentObserver.Factory provideColumbusContentObserver(
            ContentResolverWrapper contentResolver,
            UserTracker userTracker,
            @Main Handler handler,
            @Main Executor executor) {
        return new ColumbusContentObserver.Factory(
                contentResolver,
                userTracker,
                handler,
                executor);
    }

    @Provides
    @SysUISingleton
    static ContentResolverWrapper provideContentResolverWrapper(Context context) {
        return new ContentResolverWrapper(context);
    }

    @Provides
    @SysUISingleton
    static GestureSensorImpl provideGestureSensorImpl(
            Context context,
            UiEventLogger uiEventLogger,
            @Main Handler handler) {
        return new GestureSensorImpl(context, uiEventLogger, handler);
    }

    @Provides
    @SysUISingleton
    static GestureController provideGestureController(
            GestureSensor gestureSensor,
            @Named(COLUMBUS_SOFT_GATES) Set<Gate> softGates,
            CommandRegistry commandRegistry,
            UiEventLogger uiEventLogger) {
        return new GestureController(
                gestureSensor,
                softGates,
                commandRegistry,
                uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static CHREGestureSensor provideCHREGestureSensor(
            Context context,
            UiEventLogger uiEventLogger,
            GestureConfiguration gestureConfiguration,
            StatusBarStateController statusBarStateController,
            WakefulnessLifecycle wakefulnessLifecycle,
            @Background Handler bgHandler) {
        return new CHREGestureSensor(
                context,
                uiEventLogger,
                gestureConfiguration,
                statusBarStateController,
                wakefulnessLifecycle,
                bgHandler);
    }

    @Provides
    @SysUISingleton
    static PowerManagerWrapper providePowerManagerWrapper(Context context) {
        return new PowerManagerWrapper(context);
    }

    @Provides
    @SysUISingleton
    static AssistInvocationEffect provideAssistInvocationEffectColumbus(
            AssistManager assistManager) {
        return new AssistInvocationEffect(assistManager);
    }

    @Provides
    @SysUISingleton
    static UserActivity provideUserActivity(Lazy<PowerManager> powerManager) {
        return new UserActivity(powerManager);
    }

    @Provides
    @SysUISingleton
    static HapticClick provideHapticClick(Lazy<Vibrator> vibrator) {
        return new HapticClick(vibrator);
    }

    @Provides
    @SysUISingleton
    static KeyguardProximity provideKeyguardProximity(
            Context context,
            KeyguardVisibility keyguardGate,
            Proximity proximity) {
        return new KeyguardProximity(context, keyguardGate, proximity);
    }

    @Provides
    @SysUISingleton
    static KeyguardVisibility provideKeyguardVisibility(
            Context context,
            Lazy<KeyguardStateController> keyguardStateController) {
        return new KeyguardVisibility(context, keyguardStateController);
    }

    @Provides
    @SysUISingleton
    static ChargingState provideChargingState(
            Context context,
            @Main Handler handler,
            @Named(COLUMBUS_TRANSIENT_GATE_DURATION) long gateDuration) {
        return new ChargingState(context, handler, gateDuration);
    }

    @Provides
    @SysUISingleton
    static UsbState provideUsbState(
            Context context,
            @Main Handler handler,
            @Named(COLUMBUS_TRANSIENT_GATE_DURATION) long gateDuration) {
        return new UsbState(context, handler, gateDuration);
    }

    @Provides
    @SysUISingleton
    static PowerSaveState providePowerSaveState(Context context) {
        return new PowerSaveState(context);
    }

    @Provides
    @SysUISingleton
    static SilenceAlertsDisabled provideSilenceAlertsDisabled(
            Context context, ColumbusSettings columbusSettings) {
        return new SilenceAlertsDisabled(context, columbusSettings);
    }

    @Provides
    @SysUISingleton
    static FlagEnabled provideFlagEnabled(
            Context context,
            ColumbusSettings columbusSettings,
            @Main Handler handler) {
        return new FlagEnabled(context, columbusSettings, handler);
    }

    @Provides
    @SysUISingleton
    static CameraVisibility provideCameraVisibility(
            Context context,
            List<Action> exceptions,
            KeyguardVisibility keyguardGate,
            PowerState powerState,
            IActivityManager activityManager,
            @Main Handler updateHandler) {
        return new CameraVisibility(
                context,
                exceptions,
                keyguardGate,
                powerState,
                activityManager,
                updateHandler);
    }

    @Provides
    @SysUISingleton
    static SetupWizard provideSetupWizard(
            Context context,
            @Named(COLUMBUS_SETUP_WIZARD_ACTIONS) Set<Action> exceptions,
            Lazy<DeviceProvisionedController> provisionedController) {
        return new SetupWizard(context, exceptions, provisionedController);
    }

    @Provides
    @SysUISingleton
    static PowerState providePowerState(
            Context context, Lazy<WakefulnessLifecycle> wakefulnessLifecycle) {
        return new PowerState(context, wakefulnessLifecycle);
    }

    @Provides
    @SysUISingleton
    static SystemKeyPress provideSystemKeyPress(
            Context context,
            @Main Handler handler,
            CommandQueue commandQueue,
            @Named(COLUMBUS_TRANSIENT_GATE_DURATION) long gateDuration,
            @Named(COLUMBUS_BLOCKING_SYSTEM_KEYS) Set<Integer> blockingKeysProvider) {
        return new SystemKeyPress(
                context,
                handler,
                commandQueue,
                gateDuration,
                blockingKeysProvider);
    }

    @Provides
    @SysUISingleton
    static ScreenTouch provideScreenTouch(
            Context context,
            PowerState powerState,
            @Main Handler handler) {
        return new ScreenTouch(context, powerState, handler);
    }

    @Provides
    @SysUISingleton
    static TelephonyActivity provideTelephonyActivityColumbus(
                Context context,
                Lazy<TelephonyManager> telephonyManager,
                Lazy<TelephonyListenerManager> telephonyListenerManager) {
        return new TelephonyActivity(context, telephonyManager, telephonyListenerManager);
    }

    @Provides
    @SysUISingleton
    static Proximity provideProximity(
            Context context, ProximitySensor proximitySensor) {
        return new Proximity(context, proximitySensor);
    }

    @Provides
    @SysUISingleton
    static VrMode provideVrMode(Context context) {
        return new VrMode(context);
    }

    @Provides
    @SysUISingleton
    static GestureConfiguration provideGestureConfiguration(
            List<Adjustment> adjustments, SensorConfiguration sensorConfiguration) {
        return new GestureConfiguration(adjustments, sensorConfiguration);
    }

    @Provides
    @SysUISingleton
    static SensorConfiguration provideSensorConfiguration(Context context) {
        return new SensorConfiguration(context);
    }

    @Provides
    @SysUISingleton
    static LowSensitivitySettingAdjustment provideLowSensitivitySettingAdjustment(
            Context context,
            ColumbusSettings columbusSettings,
            SensorConfiguration sensorConfiguration) {
        return new LowSensitivitySettingAdjustment(
                context,
                columbusSettings,
                sensorConfiguration);
    }

    @Provides
    @SysUISingleton
    static SettingsAction provideSettingsActionColumbus(
            Context context,
            StatusBar statusBar,
            UiEventLogger uiEventLogger) {
        return new SettingsAction(context, statusBar, uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static UserSelectedAction provideUserSelectedAction(
            Context context,
            ColumbusSettings columbusSettings,
            Map<String, UserAction> userSelectedActions,
            TakeScreenshot takeScreenshot,
            KeyguardStateController keyguardStateController,
            PowerManagerWrapper powerManagerWrapper,
            WakefulnessLifecycle wakefulnessLifecycle) {
        return new UserSelectedAction(
                context,
                columbusSettings,
                userSelectedActions,
                takeScreenshot,
                keyguardStateController,
                powerManagerWrapper,
                wakefulnessLifecycle);
    }

    @Provides
    @SysUISingleton
    static DismissTimer provideDismissTimer(
            Context context,
            SilenceAlertsDisabled silenceAlertsDisabled,
            IActivityManager activityManager) {
        return new DismissTimer(
                context,
                silenceAlertsDisabled,
                activityManager);
    }

    @Provides
    @SysUISingleton
    static UnpinNotifications provideUnpinNotificationsColumbus(
            Context context,
            SilenceAlertsDisabled silenceAlertsDisabled,
            Optional<HeadsUpManager> headsUpManagerOptional) {
        return new UnpinNotifications(
                context,
                silenceAlertsDisabled,
                headsUpManagerOptional);
    }

    @Provides
    @SysUISingleton
    static ManageMedia provideManageMedia(
            Context context,
            AudioManager audioManager,
            UiEventLogger uiEventLogger) {
        return new ManageMedia(context, audioManager, uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static LaunchApp provideLaunchApp(
            Context context,
            LauncherApps launcherApps,
            ActivityStarter activityStarter,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            IActivityManager activityManagerService,
            UserManager userManager,
            ColumbusSettings columbusSettings,
            KeyguardVisibility keyguardVisibility,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            @Main Handler mainHandler,
            @Background Handler bgHandler,
            @Background Executor bgExecutor,
            UiEventLogger uiEventLogger,
            UserTracker userTracker) {
        return new LaunchApp(
                context,
                launcherApps,
                activityStarter,
                statusBarKeyguardViewManager,
                activityManagerService,
                userManager,
                columbusSettings,
                keyguardVisibility,
                keyguardUpdateMonitor,
                mainHandler,
                bgHandler,
                bgExecutor,
                uiEventLogger,
                userTracker);
    }

    @Provides
    @SysUISingleton
    static SilenceCall provideSilenceCallColumbus(
            Context context,
            SilenceAlertsDisabled silenceAlertsDisabled,
            Lazy<TelecomManager> telecomManager,
            Lazy<TelephonyManager> telephonyManager,
            Lazy<TelephonyListenerManager> telephonyListenerManager) {
        return new SilenceCall(
                context,
                silenceAlertsDisabled,
                telecomManager,
                telephonyManager,
                telephonyListenerManager);
    }

    @Provides
    @SysUISingleton
    static LaunchOverview provideLaunchOverview(
            Context context,
            Recents recents,
            UiEventLogger uiEventLogger) {
        return new LaunchOverview(context, recents, uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static LaunchOpa provideLaunchOpaColumbus(
            Context context,
            StatusBar statusBar,
            Set<FeedbackEffect> feedbackEffects,
            AssistManager assistManager,
            Lazy<KeyguardManager> keyguardManager,
            TunerService tunerService,
            ColumbusContentObserver.Factory settingsObserverFactory,
            UiEventLogger uiEventLogger) {
        return new LaunchOpa(
                context,
                statusBar,
                feedbackEffects,
                assistManager,
                keyguardManager,
                tunerService,
                settingsObserverFactory,
                uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static SnoozeAlarm provideSnoozeAlarmColumbus(
            Context context,
            SilenceAlertsDisabled silenceAlertsDisabled,
            IActivityManager activityManagerService) {
        return new SnoozeAlarm(
                context,
                silenceAlertsDisabled,
                activityManagerService);
    }

    @Provides
    @SysUISingleton
    static OpenNotificationShade provideOpenNotificationShade(
            Context context,
            Lazy<NotificationShadeWindowController> notificationShadeWindowController,
            Lazy<StatusBar> statusBar,
            UiEventLogger uiEventLogger) {
        return new OpenNotificationShade(
                context,
                notificationShadeWindowController,
                statusBar,
                uiEventLogger);
    }

    @Provides
    @SysUISingleton
    static TakeScreenshot provideTakeScreenshot(
            Context context,
            @Main Handler handler,
            UiEventLogger uiEventLogger) {
        return new TakeScreenshot(context, handler, uiEventLogger);
    }

    @Provides
    @SysUISingleton
    @Named(COLUMBUS_TRANSIENT_GATE_DURATION)
    static long provideTransientGateDuration() {
        return 500;
    }

    @Provides
    @SysUISingleton
    @ElementsIntoSet
    @Named(COLUMBUS_GATES)
    static Set<Gate> provideColumbusGates(
            FlagEnabled flagEnabled,
            KeyguardProximity keyguardProximity,
            SetupWizard setupWizard,
            TelephonyActivity telephonyActivity,
            VrMode vrMode,
            CameraVisibility cameraVisibility,
            PowerSaveState powerSaveState,
            PowerState powerState) {
        return new HashSet(Arrays.asList(
                flagEnabled,
                keyguardProximity,
                setupWizard,
                telephonyActivity,
                vrMode,
                cameraVisibility,
                powerSaveState,
                powerState));
    }

    @Provides
    @SysUISingleton
    @ElementsIntoSet
    @Named(COLUMBUS_SOFT_GATES)
    static Set<Gate> provideColumbusSoftGates(
            ChargingState chargingState,
            UsbState usbState,
            SystemKeyPress systemKeyPress,
            ScreenTouch screenTouch) {
        return new HashSet(Arrays.asList(
                chargingState,
                usbState,
                systemKeyPress,
                screenTouch));
    }

    @Provides
    @SysUISingleton
    static Map<String, UserAction> provideUserSelectedActions(
            LaunchOpa launchOpa,
            ManageMedia manageMedia,
            TakeScreenshot takeScreenshot,
            LaunchOverview launchOverview,
            OpenNotificationShade openNotificationShade,
            LaunchApp launchApp) {
        Map<String, UserAction> result = new HashMap<>();
        result.put("assistant", launchOpa);
        result.put("media", manageMedia);
        result.put("screenshot", takeScreenshot);
        result.put("overview", launchOverview);
        result.put("notifications", openNotificationShade);
        result.put("launch", launchApp);
        return result;
    }

    @Provides
    @SysUISingleton
    static GestureSensor provideGestureSensor(
            Context context,
            ColumbusSettings columbusSettings,
            Lazy<CHREGestureSensor> chreGestureSensor,
            Lazy<GestureSensorImpl> apGestureSensor) {
        if (columbusSettings.useApSensor()
                || !context.getPackageManager().hasSystemFeature("android.hardware.context_hub")) {
            Log.i("Columbus/Module", "Creating AP sensor");
            return apGestureSensor.get();
        }
        Log.i("Columbus/Module", "Creating CHRE sensor");
        return chreGestureSensor.get();
    }

    @Provides
    @SysUISingleton
    static List<Action> provideColumbusActions(
            @Named(COLUMBUS_FULL_SCREEN_ACTIONS) List<Action> fullscreenActions,
            UnpinNotifications unpinNotifications,
            UserSelectedAction userSelectedAction) {
        List<Action> result = new ArrayList<>(fullscreenActions);
        result.add(unpinNotifications);
        result.add(userSelectedAction);
        return result;
    }

    @Provides
    @SysUISingleton
    @Named(COLUMBUS_FULL_SCREEN_ACTIONS)
    static List<Action> provideFullscreenActions(
            DismissTimer dismissTimer,
            SnoozeAlarm snoozeAlarm,
            SilenceCall silenceCall,
            SettingsAction settingsAction) {
        return Arrays.asList(
                dismissTimer,
                snoozeAlarm,
                silenceCall,
                settingsAction);
    }

    @Provides
    @SysUISingleton
    @ElementsIntoSet
    static Set<FeedbackEffect> provideColumbusEffects(
            HapticClick hapticClick, UserActivity userActivity) {
        return new HashSet(Arrays.asList(hapticClick, userActivity));
    }

    @Provides
    @SysUISingleton
    static List<Adjustment> provideGestureAdjustments(
            LowSensitivitySettingAdjustment lowSensitivitySettingAdjustment) {
        return Collections.singletonList(lowSensitivitySettingAdjustment);
    }

    @Provides
    @SysUISingleton
    @Named(COLUMBUS_BLOCKING_SYSTEM_KEYS)
    @ElementsIntoSet
    static Set<Integer> provideBlockingSystemKeys() {
        return new HashSet(Arrays.asList(24, 25, 26));
    }

    @Provides
    @SysUISingleton
    @Named(COLUMBUS_SETUP_WIZARD_ACTIONS)
    @ElementsIntoSet
    static Set<Action> provideSetupWizardActions(SettingsAction settingsAction) {
        return new HashSet(Arrays.asList(settingsAction));
    }
}

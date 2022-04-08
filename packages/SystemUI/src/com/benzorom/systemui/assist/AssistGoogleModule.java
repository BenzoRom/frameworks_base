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
package com.benzorom.systemui.assist;

import static com.benzorom.systemui.Dependency.OVERLAY_UI_HOST_PARENT_VIEW_GROUP;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.internal.app.AssistUtils;
import com.android.internal.logging.UiEventLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.assist.AssistLogger;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.assist.PhoneStateMonitor;
import com.android.systemui.assist.ui.DefaultUiController;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.google.android.systemui.assist.GoogleAssistLogger;
import com.google.android.systemui.assist.OpaEnabledDispatcher;
import com.google.android.systemui.assist.OpaEnabledReceiver;
import com.google.android.systemui.assist.OpaEnabledSettings;
import com.google.android.systemui.assist.uihints.AssistantPresenceHandler;
import com.google.android.systemui.assist.uihints.AssistantWarmer;
import com.google.android.systemui.assist.uihints.ColorChangeHandler;
import com.google.android.systemui.assist.uihints.ConfigurationHandler;
import com.google.android.systemui.assist.uihints.FlingVelocityWrapper;
import com.google.android.systemui.assist.uihints.GlowController;
import com.google.android.systemui.assist.uihints.GoBackHandler;
import com.google.android.systemui.assist.uihints.GoogleDefaultUiController;
import com.google.android.systemui.assist.uihints.IconController;
import com.google.android.systemui.assist.uihints.KeyboardMonitor;
import com.google.android.systemui.assist.uihints.NavBarFader;
import com.google.android.systemui.assist.uihints.NgaMessageHandler;
import com.google.android.systemui.assist.uihints.NgaUiController;
import com.google.android.systemui.assist.uihints.OverlappedElementController;
import com.google.android.systemui.assist.uihints.OverlayUiHost;
import com.google.android.systemui.assist.uihints.ScrimController;
import com.google.android.systemui.assist.uihints.TakeScreenshotHandler;
import com.google.android.systemui.assist.uihints.TaskStackNotifier;
import com.google.android.systemui.assist.uihints.TimeoutManager;
import com.google.android.systemui.assist.uihints.TouchInsideHandler;
import com.google.android.systemui.assist.uihints.TouchOutsideHandler;
import com.google.android.systemui.assist.uihints.TranscriptionController;
import com.google.android.systemui.assist.uihints.edgelights.EdgeLightsController;
import com.google.android.systemui.assist.uihints.input.NgaInputHandler;
import com.google.android.systemui.assist.uihints.input.TouchActionRegion;
import com.google.android.systemui.assist.uihints.input.TouchInsideRegion;
import com.benzorom.systemui.assist.AssistManagerGoogle;
import com.benzorom.systemui.assist.uihints.LightnessProvider;

import java.util.*;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Provider;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public interface AssistGoogleModule {
    @Provides
    @SysUISingleton
    static AssistManagerGoogle provideAssistManagerGoogle(
            DeviceProvisionedController controller,
            Context context,
            AssistUtils assistUtils,
            NgaUiController ngaUiController,
            CommandQueue commandQueue,
            OpaEnabledReceiver opaEnabledReceiver,
            PhoneStateMonitor phoneStateMonitor,
            OverviewProxyService overviewProxyService,
            OpaEnabledDispatcher opaEnabledDispatcher,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            NavigationModeController navigationModeController,
            AssistantPresenceHandler assistantPresenceHandler,
            NgaMessageHandler ngaMessageHandler,
            Lazy<SysUiState> sysUiState,
            @Main Handler uiHandler,
            DefaultUiController defaultUiController,
            GoogleDefaultUiController googleDefaultUiController,
            IWindowManager windowManagerService,
            AssistLogger assistLogger) {
        return new AssistManagerGoogle(
                controller,
                context,
                assistUtils,
                ngaUiController,
                commandQueue,
                opaEnabledReceiver,
                phoneStateMonitor,
                overviewProxyService,
                opaEnabledDispatcher,
                keyguardUpdateMonitor,
                navigationModeController,
                assistantPresenceHandler,
                ngaMessageHandler,
                sysUiState,
                uiHandler,
                defaultUiController,
                googleDefaultUiController,
                windowManagerService,
                assistLogger);
    }

    @Provides
    @SysUISingleton
    static NgaUiController provideNgaUiController(
            Context context,
            TimeoutManager timeoutManager,
            AssistantPresenceHandler assistantPresenceHandler,
            TouchInsideHandler touchInsideHandler,
            ColorChangeHandler colorChangeHandler,
            OverlayUiHost uiHost,
            EdgeLightsController edgeLightsController,
            GlowController glowController,
            ScrimController scrimController,
            TranscriptionController transcriptionController,
            IconController iconController,
            LightnessProvider lightness,
            StatusBarStateController statusBarStateController,
            Lazy<AssistManager> assistManager,
            FlingVelocityWrapper flingVelocity,
            AssistantWarmer assistantWarmer,
            NavBarFader navBarFader,
            AssistLogger assistLogger) {
        return new NgaUiController(
                context,
                timeoutManager,
                assistantPresenceHandler,
                touchInsideHandler,
                colorChangeHandler,
                uiHost,
                edgeLightsController,
                glowController,
                scrimController,
                transcriptionController,
                iconController,
                lightness,
                statusBarStateController,
                assistManager,
                flingVelocity,
                assistantWarmer,
                navBarFader,
                assistLogger);
    }

    @Provides
    @SysUISingleton
    static OpaEnabledReceiver provideOpaEnabledReceiver(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            @Main Executor fgExecutor,
            @Background Executor bgExecutor,
            OpaEnabledSettings opaEnabledSettings) {
        return new OpaEnabledReceiver(
                context,
                broadcastDispatcher,
                fgExecutor,
                bgExecutor,
                opaEnabledSettings);
    }

    @Provides
    @SysUISingleton
    static OpaEnabledSettings provideOpaEnabledSettings(Context context) {
        return new OpaEnabledSettings(context);
    }

    @Provides
    @SysUISingleton
    static OpaEnabledDispatcher provideOpaEnabledDispatcher(
            Lazy<StatusBar> statusBarLazy) {
        return new OpaEnabledDispatcher(statusBarLazy);
    }

    @Provides
    @SysUISingleton
    static AssistantPresenceHandler provideAssistantPresenceHandler(
            Context context, AssistUtils assistUtils) {
        return new AssistantPresenceHandler(context, assistUtils);
    }

    @Provides
    @SysUISingleton
    static AssistantWarmer provideAssistantWarmer(Context context) {
        return new AssistantWarmer(context);
    }

    @Provides
    @SysUISingleton
    static GoogleDefaultUiController provideGoogleDefaultUiController(
            Context context, GoogleAssistLogger googleAssistLogger) {
        return new GoogleDefaultUiController(context, googleAssistLogger);
    }

    @Provides
    @SysUISingleton
    static GoogleAssistLogger provideGoogleAssistLogger(
            Context context,
            UiEventLogger uiEventLogger,
            AssistUtils assistUtils,
            PhoneStateMonitor phoneStateMonitor,
            AssistantPresenceHandler assistantPresenceHandler) {
        return new GoogleAssistLogger(
                context,
                uiEventLogger,
                assistUtils,
                phoneStateMonitor,
                assistantPresenceHandler);
    }

    @Provides
    @SysUISingleton
    static ColorChangeHandler provideColorChangeHandler(Context context) {
        return new ColorChangeHandler(context);
    }

    @Provides
    @SysUISingleton
    static ConfigurationHandler provideConfigurationHandler(Context context) {
        return new ConfigurationHandler(context);
    }

    @Provides
    @SysUISingleton
    static EdgeLightsController provideEdgeLightsController(
            Context context,
            @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP) ViewGroup parent,
            AssistLogger assistLogger) {
        return new EdgeLightsController(context, parent, assistLogger);
    }

    @Provides
    @SysUISingleton
    static FlingVelocityWrapper provideFlingVelocityWrapper() {
        return new FlingVelocityWrapper();
    }

    @Provides
    @SysUISingleton
    static GlowController provideGlowController(
            Context context,
            @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP) ViewGroup parent,
            TouchInsideHandler touchInsideHandler) {
        return new GlowController(context, parent, touchInsideHandler);
    }

    @Provides
    @SysUISingleton
    static GoBackHandler provideGoBackHandler() {
        return new GoBackHandler();
    }

    @Provides
    @SysUISingleton
    static IconController provideIconController(
            LayoutInflater inflater,
            @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP) ViewGroup parent,
            ConfigurationController configurationController) {
        return new IconController(inflater, parent, configurationController);
    }

    @Provides
    @SysUISingleton
    static LightnessProvider provideLightnessProvider() {
        return new LightnessProvider();
    }

    @Provides
    @SysUISingleton
    static NavBarFader provideNavBarFader(
            NavigationBarController navigationBarController, Handler handler) {
        return new NavBarFader(navigationBarController, handler);
    }

    @Provides
    @SysUISingleton
    static OverlappedElementController provideOverlappedElementController(
            Lazy<StatusBar> statusBarLazy) {
        return new OverlappedElementController(statusBarLazy);
    }

    @Provides
    @SysUISingleton
    static OverlayUiHost provideOverlayUiHost(
            Context context, TouchOutsideHandler touchOutsideHandler) {
        return new OverlayUiHost(context, touchOutsideHandler);
    }

    @Provides
    @SysUISingleton
    static ScrimController provideScrimController(
            @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP) ViewGroup parent,
            OverlappedElementController overlappedElementController,
            LightnessProvider lightness,
            TouchInsideHandler touchInsideHandler) {
        return new ScrimController(
                parent,
                overlappedElementController,
                lightness,
                touchInsideHandler);
    }

    @Provides
    @SysUISingleton
    static TimeoutManager provideTimeoutManager(Lazy<AssistManager> assistManager) {
        return new TimeoutManager(assistManager);
    }

    @Provides
    @SysUISingleton
    static TouchInsideHandler provideTouchInsideHandler(
            Lazy<AssistManager> assistManager,
            NavigationModeController navigationModeController,
            AssistLogger assistLogger) {
        return new TouchInsideHandler(assistManager, navigationModeController, assistLogger);
    }

    @Provides
    @SysUISingleton
    static TouchOutsideHandler provideTouchOutsideHandler() {
        return new TouchOutsideHandler();
    }

    @Provides
    @SysUISingleton
    static TranscriptionController provideTranscriptionController(
            @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP) ViewGroup parent,
            TouchInsideHandler defaultOnTap,
            FlingVelocityWrapper flingVelocity,
            ConfigurationController configurationController) {
        return new TranscriptionController(
                parent,
                defaultOnTap,
                flingVelocity,
                configurationController);
    }

    @Provides
    @SysUISingleton
    static TaskStackNotifier provideTaskStackNotifier() {
        return new TaskStackNotifier();
    }

    @Provides
    @SysUISingleton
    static TakeScreenshotHandler provideTakeScreenshotHandler(Context context) {
        return new TakeScreenshotHandler(context);
    }

    @Provides
    @SysUISingleton
    static NgaMessageHandler provideNgaMessageHandler(
            NgaUiController ngaUiController,
            AssistantPresenceHandler assistantPresenceHandler,
            NavigationModeController navigationModeController,
            Set<NgaMessageHandler.KeepAliveListener> keepAliveListeners,
            Set<NgaMessageHandler.AudioInfoListener> audioInfoListeners,
            Set<NgaMessageHandler.CardInfoListener> cardInfoListeners,
            Set<NgaMessageHandler.ConfigInfoListener> configInfoListeners,
            Set<NgaMessageHandler.EdgeLightsInfoListener> edgeLightsInfoListeners,
            Set<NgaMessageHandler.TranscriptionInfoListener> transcriptionInfoListeners,
            Set<NgaMessageHandler.GreetingInfoListener> greetingInfoListeners,
            Set<NgaMessageHandler.ChipsInfoListener> chipsInfoListeners,
            Set<NgaMessageHandler.ClearListener> clearListeners,
            Set<NgaMessageHandler.StartActivityInfoListener> startActivityInfoListeners,
            Set<NgaMessageHandler.KeyboardInfoListener> keyboardInfoListeners,
            Set<NgaMessageHandler.ZerostateInfoListener> zerostateInfoListeners,
            Set<NgaMessageHandler.GoBackListener> goBackListeners,
            Set<NgaMessageHandler.TakeScreenshotListener> takeScreenshotListeners,
            Set<NgaMessageHandler.WarmingListener> warmingListeners,
            Set<NgaMessageHandler.NavBarVisibilityListener> navBarVisibilityListeners,
            Handler handler) {
        return new NgaMessageHandler(
                ngaUiController,
                assistantPresenceHandler,
                navigationModeController,
                keepAliveListeners,
                audioInfoListeners,
                cardInfoListeners,
                configInfoListeners,
                edgeLightsInfoListeners,
                transcriptionInfoListeners,
                greetingInfoListeners,
                chipsInfoListeners,
                clearListeners,
                startActivityInfoListeners,
                keyboardInfoListeners,
                zerostateInfoListeners,
                goBackListeners,
                takeScreenshotListeners,
                warmingListeners,
                navBarVisibilityListeners,
                handler);
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.AudioInfoListener> provideAudioInfoListeners(
            EdgeLightsController edgeLightsController, GlowController glowController) {
        return new HashSet(Arrays.asList(edgeLightsController, glowController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.CardInfoListener> provideCardInfoListeners(
            GlowController glowController,
            ScrimController scrimController,
            TranscriptionController transcriptionController,
            LightnessProvider lightness) {
        return new HashSet(Arrays.asList(
                glowController,
                scrimController,
                transcriptionController,
                lightness));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.TranscriptionInfoListener>
                provideTranscriptionInfoListener(TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(transcriptionController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.GreetingInfoListener> provideGreetingInfoListener(
            TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(transcriptionController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.ChipsInfoListener> provideChipsInfoListener(
            TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(transcriptionController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.ClearListener> provideClearListener(
            TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(transcriptionController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.KeyboardInfoListener> provideKeyboardInfoListener(
            IconController iconController) {
        return new HashSet(Arrays.asList(iconController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.ZerostateInfoListener> provideZerostateInfoListener(
            IconController iconController) {
        return new HashSet(Arrays.asList(iconController));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.GoBackListener> provideGoBackListener(
            GoBackHandler goBackHandler) {
        return new HashSet(Arrays.asList(goBackHandler));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.TakeScreenshotListener>
            provideTakeScreenshotListener(TakeScreenshotHandler takeScreenshotHandler) {
        return new HashSet(Arrays.asList(takeScreenshotHandler));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.WarmingListener> provideWarmingListener(
            AssistantWarmer assistantWarmer) {
        return new HashSet(Arrays.asList(assistantWarmer));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.NavBarVisibilityListener>
            provideNavBarVisibilityListener(NavBarFader navBarFader) {
        return new HashSet(Arrays.asList(navBarFader));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.ConfigInfoListener> provideConfigInfoListeners(
            AssistantPresenceHandler assistantPresenceHandler,
            TouchInsideHandler touchInsideHandler,
            TouchOutsideHandler touchOutsideHandler,
            TaskStackNotifier taskStackNotifier,
            KeyboardMonitor keyboardMonitor,
            ColorChangeHandler colorChangeHandler,
            ConfigurationHandler configurationHandler) {
        return new HashSet(Arrays.asList(
                assistantPresenceHandler,
                touchInsideHandler,
                touchOutsideHandler,
                taskStackNotifier,
                keyboardMonitor,
                colorChangeHandler,
                configurationHandler));
    }

    @Provides
    @SysUISingleton
    static KeyboardMonitor provideKeyboardMonitor(
            Context context, Optional<CommandQueue> optional) {
        return new KeyboardMonitor(context, optional);
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.EdgeLightsInfoListener> provideEdgeLightsInfoListeners(
                EdgeLightsController edgeLightsController, NgaInputHandler ngaInputHandler) {
        return new HashSet(Arrays.asList(edgeLightsController, ngaInputHandler));
    }

    @Provides
    @SysUISingleton
    static NgaInputHandler provideNgaInputHandler(
            TouchInsideHandler touchInsideHandler,
            Set<TouchActionRegion> touchables,
            Set<TouchInsideRegion> dismissables) {
        return new NgaInputHandler(touchInsideHandler, touchables, dismissables);
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.KeepAliveListener> provideKeepAliveListener(
            TimeoutManager timeoutManager) {
        return new HashSet(Arrays.asList(timeoutManager));
    }

    @Provides
    @ElementsIntoSet
    static Set<NgaMessageHandler.StartActivityInfoListener> provideActivityStarter(
            final Lazy<StatusBar> statusBarLazy) {
        return new HashSet(Collections.singletonList(
            (NgaMessageHandler.StartActivityInfoListener) (intent, dismissShade) -> {
                if (intent != null) {
                    statusBarLazy.get().startActivity(intent, dismissShade);
                } else {
                    Log.e("ActivityStarter", "Null intent; cannot start activity");
                }
            }));
    }

    @Provides
    @ElementsIntoSet
    static Set<TouchActionRegion> provideTouchActionRegions(
            IconController iconController,
            TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(iconController, transcriptionController));
    }

    @Provides
    @ElementsIntoSet
    static Set<TouchInsideRegion> provideTouchInsideRegions(
            GlowController glowController,
            ScrimController scrimController,
            TranscriptionController transcriptionController) {
        return new HashSet(Arrays.asList(
                glowController,
                scrimController,
                transcriptionController));
    }

    @Provides
    @SysUISingleton
    @Named(OVERLAY_UI_HOST_PARENT_VIEW_GROUP)
    static ViewGroup provideParentViewGroup(OverlayUiHost overlayUiHost) {
        return overlayUiHost.getParent();
    }
}

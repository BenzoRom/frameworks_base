
/*
 * Copyright (C) 2021 The Pixel Experience Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.benzorom.systemui;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;
import com.benzorom.systemui.dagger.SysUIGoogleSysUIComponent;
import com.benzorom.systemui.dagger.DaggerSysUIGoogleGlobalRootComponent;
import com.google.android.systemui.gesture.BackGestureTfClassifierProviderGoogle;
import com.google.android.systemui.screenshot.ScreenshotNotificationSmartActionsProviderGoogle;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SystemUIGoogleFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerSysUIGoogleGlobalRootComponent.builder()
                .context(context)
                .build();
    }

    @Override
    public ScreenshotNotificationSmartActionsProvider
                createScreenshotNotificationSmartActionsProvider(
                        Context context, Executor executor, Handler uiHandler) {
        return new ScreenshotNotificationSmartActionsProviderGoogle(context, executor, uiHandler);
    }

    @Override
    public BackGestureTfClassifierProvider createBackGestureTfClassifierProvider(
            AssetManager am, String modelName) {
        return new BackGestureTfClassifierProviderGoogle(am, modelName);
    }

    @Override
    public void init(Context context, boolean fromTest)
            throws ExecutionException, InterruptedException {
        super.init(context, fromTest);
        if (shouldInitializeComponents()) {
            ((SysUIGoogleSysUIComponent) getSysUIComponent()).createKeyguardSmartspaceController();
        }
    }
}

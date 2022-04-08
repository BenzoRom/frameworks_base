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
package com.benzorom.systemui

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import com.android.systemui.SystemUIFactory
import com.android.systemui.dagger.GlobalRootComponent
import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider
import com.benzorom.systemui.dagger.DaggerSysUIGoogleGlobalRootComponent
import com.benzorom.systemui.dagger.SysUIGoogleSysUIComponent
import com.google.android.systemui.gesture.BackGestureTfClassifierProviderGoogle
import com.google.android.systemui.screenshot.ScreenshotNotificationSmartActionsProviderGoogle
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Google variant SystemUIFactory, that substitutes default GlobalRootComponent
 * for SysUIGoogleGlobalRootComponent
 */
class SystemUIGoogleFactory : SystemUIFactory() {
    override fun buildGlobalRootComponent(context: Context): GlobalRootComponent {
        return DaggerSysUIGoogleGlobalRootComponent.builder()
            .context(context)
            .build()
    }

    /**
     * Creates an instance of ScreenshotNotificationSmartActionsProvider.
     * This method is overridden in vendor specific implementation of Sys UI.
     */
    override fun createScreenshotNotificationSmartActionsProvider(
        context: Context, executor: Executor, uiHandler: Handler
    ): ScreenshotNotificationSmartActionsProvider {
        return ScreenshotNotificationSmartActionsProviderGoogle(context, executor, uiHandler)
    }

    /**
     * Creates an instance of BackGestureTfClassifierProvider.
     * This method is overridden in vendor specific implementation of Sys UI.
     */
    override fun createBackGestureTfClassifierProvider(
        am: AssetManager, modelName: String
    ): BackGestureTfClassifierProvider {
        return BackGestureTfClassifierProviderGoogle(am, modelName)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun init(context: Context, fromTest: Boolean) {
        super.init(context, fromTest)
        if (shouldInitializeComponents()) {
            (sysUIComponent as SysUIGoogleSysUIComponent).createKeyguardSmartspaceController()
        }
    }
}

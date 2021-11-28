/*
 * Copyright (C) 2021 Benzo Rom
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
package com.benzorom.systemui.dagger

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler

import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.benzorom.systemui.fingerprint.UdfpsGhbmProvider
import com.benzorom.systemui.fingerprint.UdfpsHbmController
import com.benzorom.systemui.fingerprint.UdfpsLhbmProvider

import dagger.Binds
import dagger.Module
import dagger.Provides

import java.util.concurrent.Executor

@Module
object SystemUIBenzoModule {
    @Provides
    @SysUISingleton
    fun provideUdfpsHbmProvider(
        context: Context,
        @Main mainHandler: Handler,
        @UiBackground uiBgExecutor: Executor,
        udfpsGhbmProvider: UdfpsGhbmProvider,
        udfpsLhbmProvider: UdfpsLhbmProvider,
        authController: AuthController,
        displayManager: DisplayManager
    ): UdfpsHbmProvider {
        return UdfpsHbmController(
            context,
            mainHandler,
            uiBgExecutor,
            udfpsGhbmProvider,
            udfpsLhbmProvider,
            authController,
            displayManager
        )
    }

    @Provides
    fun provideUdfpsGhbmProvider(): UdfpsGhbmProvider {
        return UdfpsGhbmProvider()
    }

    @Provides
    fun provideUdfpsLhbmProvider(): UdfpsLhbmProvider {
        return UdfpsLhbmProvider()
    }
}

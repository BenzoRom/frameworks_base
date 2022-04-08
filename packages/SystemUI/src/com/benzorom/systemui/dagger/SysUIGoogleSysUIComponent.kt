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

import com.android.systemui.dagger.DefaultComponentBinder
import com.android.systemui.dagger.DependencyProvider
import com.android.systemui.dagger.SysUIComponent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.SystemUIBinder
import com.android.systemui.dagger.SystemUIModule
import com.google.android.systemui.smartspace.KeyguardSmartspaceController
import com.benzorom.systemui.keyguard.KeyguardSliceProviderGoogle
import dagger.Subcomponent

/**
 * Dagger Subcomponent for Core SysUI.
 */
@SysUISingleton
@Subcomponent(
    modules = [
        DefaultComponentBinder::class,
        DependencyProvider::class,
        DependencyProviderGoogle::class,
        SystemUIGoogleBinder::class,
        SystemUIModule::class,
        SystemUIGoogleModule::class
    ])
interface SysUIGoogleSysUIComponent : SysUIComponent {

    /**
     * Builder for SysUIGoogleSysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder : SysUIComponent.Builder {
        override fun build(): SysUIGoogleSysUIComponent
    }

    @SysUISingleton
    fun createKeyguardSmartspaceController(): KeyguardSmartspaceController

    /**
     * Member injection into KeyguardSliceProviderGoogle.
     */
    fun inject(keyguardSliceProvider: KeyguardSliceProviderGoogle)
}

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
package com.benzorom.systemui.fingerprint

import android.os.Handler
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.dagger.qualifiers.Main
import java.util.concurrent.Executor

class HbmRequest(
    @Main internal val mainHandler: Handler,
    @Main internal val biometricExecutor: Executor,
    internal val authController: AuthController,
    internal val lhbmProvider: UdfpsLhbmProvider,
    @DisplayId internal val displayId: Int,
    @HbmType internal val hbmType: Int,
    internal val onHbmEnabled: Runnable?
) {
    internal var finishedStarting = false
    internal var startedRequest = false
}

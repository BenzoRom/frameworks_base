/*
 * Copyright (C) 2021 Benzo Rom
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

import android.view.Surface
import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType

internal class UdfpsHbmRequest(
    displayId: Int,
    @HbmType hbmType: Int,
    surface: Surface?,
    onHbmEnabled: Runnable?
) {
    val args: Args
    var beganEnablingHbm = false
    var finishedEnablingHbm = false

    internal class Args(
        val displayId: Int,
        @HbmType val hbmType: Int,
        val surface: Surface?,
        val onHbmEnabled: Runnable?
    )

    init {
        args = Args(displayId, hbmType, surface, onHbmEnabled)
    }
}

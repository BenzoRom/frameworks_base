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
package com.benzorom.systemui.fingerprint;

import android.annotation.Nullable;
import android.view.Surface;

import com.android.systemui.biometrics.UdfpsHbmTypes.HbmType;

class UdfpsHbmRequest {
    public final Args args;
    public boolean beganEnablingHbm;
    public boolean finishedEnablingHbm;

    public static class Args {
        public final int displayId;
        public final @HbmType int hbmType;
        public final @Nullable Surface surface;
        public final @Nullable Runnable onHbmEnabled;

        Args(int displayId,
             @HbmType int hbmType,
             @Nullable Surface surface,
             @Nullable Runnable onHbmEnabled) {
            this.displayId = displayId;
            this.hbmType = hbmType;
            this.surface = surface;
            this.onHbmEnabled = onHbmEnabled;
        }
    }

    public UdfpsHbmRequest(int displayId,
                           @HbmType int hbmType,
                           @Nullable Surface surface,
                           @Nullable Runnable onHbmEnabled) {
        args = new Args(displayId, hbmType, surface, onHbmEnabled);
    }
}

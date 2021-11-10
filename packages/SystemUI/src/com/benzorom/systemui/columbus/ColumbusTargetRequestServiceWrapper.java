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

package com.benzorom.systemui.columbus;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.android.internal.logging.UiEventLogger;

import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.google.android.systemui.columbus.ColumbusTargetRequestService;
import com.google.android.systemui.columbus.ColumbusSettings;
import com.google.android.systemui.columbus.ColumbusStructuredDataManager;

import javax.inject.Inject;

public class ColumbusTargetRequestServiceWrapper extends ColumbusTargetRequestService {
    private final Context sysUIContext;
    private final UserTracker userTracker;
    private final ColumbusSettings columbusSettings;
    private final ColumbusStructuredDataManager columbusStructuredDataManager;
    private final UiEventLogger uiEventLogger;
    @Main
    private final Handler mainHandler;
    @Background
    private final Looper looper;

    @Inject
    public ColumbusTargetRequestServiceWrapper(
            Context sysUIContext,
            UserTracker userTracker,
            ColumbusSettings columbusSettings,
            ColumbusStructuredDataManager columbusStructuredDataManager,
            UiEventLogger uiEventLogger,
            @Main Handler mainHandler,
            @Background Looper looper) {
        super(sysUIContext, userTracker, columbusSettings, columbusStructuredDataManager,
                uiEventLogger, mainHandler, looper);
        this.sysUIContext = sysUIContext;
        this.userTracker = userTracker;
        this.columbusSettings = columbusSettings;
        this.columbusStructuredDataManager = columbusStructuredDataManager;
        this.uiEventLogger = uiEventLogger;
        this.mainHandler = mainHandler;
        this.looper = looper;
    }
}

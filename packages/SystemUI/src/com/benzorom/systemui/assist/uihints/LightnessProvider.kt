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
package com.benzorom.systemui.assist.uihints

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.CompositionSamplingListener
import android.view.SurfaceControl
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.assist.uihints.LightnessListener
import com.google.android.systemui.assist.uihints.NgaMessageHandler.CardInfoListener
import javax.inject.Inject

class LightnessProvider @Inject constructor() : CardInfoListener {
    private var listener: LightnessListener? = null
    private var cardVisible = false
    private var colorMode = 0
    private var isMonitoringColor = false
    private var isMuted = false
    @Main private val uiHandler = Handler(Looper.getMainLooper())
    private val colorMonitor = object : CompositionSamplingListener(Runnable::run) {
        override fun onSampleCollected(medianLuma: Float) {
            uiHandler.post {
                if (listener != null || !isMuted) {
                    if (!cardVisible && colorMode == 0) {
                        listener?.onLightnessUpdate(medianLuma)
                    }
                }
            }
        }
    }

    override fun onCardInfo(
        isVisible: Boolean,
        sysuiColor: Int,
        animate: Boolean,
        forceScrim: Boolean
    ) {
        setCardVisible(isVisible, sysuiColor)
    }

    fun setListener(lightnessListener: LightnessListener?) {
        listener = lightnessListener
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun enableColorMonitoring(
        isMonitoring: Boolean,
        samplingArea: Rect?,
        stopLayer: SurfaceControl?
    ) {
        if (isMonitoringColor != isMonitoring) {
            isMonitoringColor = isMonitoring
            if (isMonitoring) {
                CompositionSamplingListener.register(
                    colorMonitor,
                    0,
                    stopLayer,
                    samplingArea
                )
            } else {
                CompositionSamplingListener.unregister(colorMonitor)
            }
        }
    }

    fun setCardVisible(isVisible: Boolean, sysuiColor: Int) {
        cardVisible = isVisible
        colorMode = sysuiColor
        if (listener != null || isVisible) {
            if (sysuiColor == 1) {
                listener?.onLightnessUpdate(0.0f)
            } else if (sysuiColor == 2) {
                listener?.onLightnessUpdate(1.0f)
            }
        }
    }
}

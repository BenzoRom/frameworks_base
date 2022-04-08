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
@file:Suppress("DEPRECATION")
package com.benzorom.systemui.keyguard

import android.annotation.NonNull
import android.annotation.Nullable
import android.graphics.*
import android.net.Uri
import android.os.AsyncTask
import android.os.Trace
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.drawable.IconCompat
import androidx.slice.Slice
import androidx.slice.builders.ListBuilder
import androidx.slice.builders.ListBuilder.HeaderBuilder
import androidx.slice.builders.SliceAction
import com.android.systemui.R
import com.android.systemui.keyguard.KeyguardSliceProvider
import com.google.android.systemui.smartspace.SmartSpaceCard
import com.google.android.systemui.smartspace.SmartSpaceController
import com.google.android.systemui.smartspace.SmartSpaceData
import com.google.android.systemui.smartspace.SmartSpaceUpdateListener
import java.lang.ref.WeakReference
import javax.inject.Inject

class KeyguardSliceProviderGoogle : KeyguardSliceProvider(), SmartSpaceUpdateListener {

    companion object {
        private const val logTag = "KeyguardSliceProvider"
        private val isDebug = Log.isLoggable(logTag, Log.DEBUG)
    }

    private val weatherUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/weather")
    private val calendarUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/calendar")
    @Inject lateinit var smartSpaceController: SmartSpaceController
    private var smartSpaceData: SmartSpaceData? = null
    private var hideSensitiveContent = false
    private var hideWorkContent = true

    override fun onCreateSliceProvider(): Boolean {
        val created = super.onCreateSliceProvider()
        smartSpaceData = SmartSpaceData()
        smartSpaceController.addListener(this)
        return created
    }

    override fun onDestroy() {
        super.onDestroy()
        smartSpaceController.removeListener(this)
    }

    override fun onBindSlice(sliceUri: Uri): Slice {
        Trace.beginSection("KeyguardSliceProviderGoogle#onBindSlice")
        var slice: Slice
        synchronized(this) {
            val sliceBuilder = ListBuilder(
                context!!,
                mSliceUri,
                ListBuilder.INFINITY
            )
            val card = smartSpaceData!!.currentCard
            var hasAction = false
            if (card != null && !card.isExpired && !TextUtils.isEmpty(card.title)) {
                if (!card.isSensitive || card.isSensitive &&
                    (!hideSensitiveContent && !card.isWorkProfile
                            || !hideWorkContent && card.isWorkProfile)
                ) hasAction = true
            }
            if (hasAction) {
                val icon = card!!.icon
                var action: SliceAction? = null
                val iconBitmap = if (icon != null) IconCompat.createWithBitmap(icon) else null
                val intent = card.pendingIntent
                if (!(iconBitmap == null || intent == null))
                    action = SliceAction.create(
                        intent,
                        iconBitmap,
                        ListBuilder.SMALL_IMAGE,
                        card.title
                    )
                val title = HeaderBuilder(mHeaderUri).setTitle(card.formattedTitle)
                if (action != null) title.setPrimaryAction(action)
                sliceBuilder.setHeader(title)
                val subtitle = card.subtitle
                if (subtitle != null) {
                    val calendar = ListBuilder.RowBuilder(calendarUri).setTitle(subtitle)
                    if (iconBitmap != null)
                        calendar.addEndItem(
                            iconBitmap,
                            ListBuilder.SMALL_IMAGE
                        )
                    if (action != null) calendar.setPrimaryAction(action)
                    sliceBuilder.addRow(calendar)
                }
                addZenModeLocked(sliceBuilder)
                addPrimaryActionLocked(sliceBuilder)
                slice = sliceBuilder.build()
                Trace.endSection()
                return slice
            }
            if (needsMediaLocked()) {
                addMediaLocked(sliceBuilder)
            } else {
                sliceBuilder.addRow(
                    ListBuilder.RowBuilder(mDateUri).setTitle(formattedDateLocked)
                )
            }
            addWeather(sliceBuilder)
            addNextAlarmLocked(sliceBuilder)
            addZenModeLocked(sliceBuilder)
            addPrimaryActionLocked(sliceBuilder)
            slice = sliceBuilder.build()
            if (isDebug) Log.d(logTag, "Binding slice: $slice")
            Trace.endSection()
            return slice
        }
    }

    private fun addWeather(sliceBuilder: ListBuilder) {
        val weatherCard = smartSpaceData!!.weatherCard
        if (weatherCard != null && !weatherCard.isExpired) {
            val title = ListBuilder.RowBuilder(weatherUri).setTitle(weatherCard.title)
            val icon = weatherCard.icon
            if (icon != null) {
                val iconBitmap = IconCompat.createWithBitmap(icon)
                iconBitmap.setTintMode(PorterDuff.Mode.DST)
                title.addEndItem(iconBitmap, ListBuilder.SMALL_IMAGE)
            }
            sliceBuilder.addRow(title)
        }
    }

    override fun onSensitiveModeChanged(hideSensitive: Boolean, hideWork: Boolean) {
        var notify = false
        synchronized(this) {
            if (hideSensitiveContent != hideSensitive) {
                hideSensitiveContent = hideSensitive
                notify = true
                if (isDebug) Log.d(logTag, "Public mode changed, hide data: $hideSensitive")
            }
            if (hideWorkContent != hideWork) {
                hideWorkContent = hideWork
                notify = true
                if (isDebug) Log.d(logTag, "Public work mode changed, hide data: $hideWork")
            }
        }
        if (notify) notifyChange()
    }

    override fun onSmartSpaceUpdated(data: SmartSpaceData) {
        synchronized(this) { smartSpaceData = data }
        val weatherCard = data.weatherCard
        if (weatherCard == null || weatherCard.icon == null || weatherCard.isIconProcessed) {
            notifyChange()
        } else {
            weatherCard.isIconProcessed = true
            AddShadowTask(this, weatherCard).execute(weatherCard.icon)
        }
    }

    override fun updateClockLocked() {
        notifyChange()
    }

    private class AddShadowTask(
        provider: KeyguardSliceProviderGoogle,
        card: SmartSpaceCard
    ) : AsyncTask<Bitmap?, Void?, Bitmap>() {
        private val blurRadius: Float
        private val providerReference: WeakReference<KeyguardSliceProviderGoogle>
        private val weatherCard: SmartSpaceCard

        private fun applyShadow(@NonNull result: Bitmap): Bitmap {
            val blur = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            val blurPaint = Paint()
            blurPaint.maskFilter = blur
            val offset = IntArray(2)
            val alpha = result.extractAlpha(blurPaint, offset)
            val bitmap = Bitmap.createBitmap(
                result.width,
                result.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.alpha = 70
            canvas.drawBitmap(
                alpha,
                offset[0].toFloat(),
                offset[1].toFloat().plus(blurRadius / 2.0f),
                paint
            )
            alpha.recycle()
            paint.alpha = 255
            canvas.drawBitmap(
                result,
                0.0f,
                0.0f,
                paint
            )
            return bitmap
        }

        @Deprecated("Deprecated in Java")
        override fun doInBackground(@NonNull vararg bitmaps: Bitmap?): Bitmap {
            return applyShadow(bitmaps[0]!!)
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(@Nullable result: Bitmap) {
            var provider: KeyguardSliceProviderGoogle?
            synchronized(this) {
                weatherCard.icon = result
                provider = providerReference.get()
            }
            if (provider != null) {
                provider!!.notifyChange()
            }
        }

        init {
            blurRadius = provider.context!!.resources.getDimension(R.dimen.smartspace_icon_shadow)
            providerReference = WeakReference(provider)
            weatherCard = card
        }
    }
}

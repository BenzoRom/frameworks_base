package com.benzorom.systemui.keyguard;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.ListBuilder.HeaderBuilder;
import androidx.slice.builders.ListBuilder.RowBuilder;
import androidx.slice.builders.SliceAction;

import com.android.systemui.R;
import com.android.systemui.SystemUIFactory;
import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.google.android.systemui.smartspace.SmartSpaceCard;
import com.google.android.systemui.smartspace.SmartSpaceController;
import com.google.android.systemui.smartspace.SmartSpaceData;
import com.google.android.systemui.smartspace.SmartSpaceUpdateListener;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

public class KeyguardSliceProviderGoogle extends KeyguardSliceProvider
        implements SmartSpaceUpdateListener {

    private static final String TAG = "KeyguardSliceProvider";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final Uri mCalendarUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/calendar");
    private final Uri mWeatherUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/weather");

    @Inject
    public SmartSpaceController mSmartSpaceController;
    private SmartSpaceData mSmartSpaceData;
    private boolean mHideSensitiveContent;
    private boolean mHideWorkContent = true;

    private static class AddShadowTask extends AsyncTask<Bitmap, Void, Bitmap> {
        private final float mBlurRadius;
        private final WeakReference<KeyguardSliceProviderGoogle> mProviderReference;
        private final SmartSpaceCard mWeatherCard;

        AddShadowTask(
                KeyguardSliceProviderGoogle keyguardSliceProviderGoogle,
                SmartSpaceCard smartSpaceCard
        ) {
            mProviderReference = new WeakReference<>(keyguardSliceProviderGoogle);
            mWeatherCard = smartSpaceCard;
            mBlurRadius = keyguardSliceProviderGoogle.getContext().getResources().getDimension(
                    R.dimen.smartspace_icon_shadow
            );
        }

        @Override
        protected Bitmap doInBackground(Bitmap... bitmapArr) {
            return applyShadow(bitmapArr[0]);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            KeyguardSliceProviderGoogle keyguardSliceProviderGoogle;
            synchronized (this) {
                mWeatherCard.setIcon(bitmap);
                keyguardSliceProviderGoogle = (KeyguardSliceProviderGoogle) mProviderReference.get();
            }
            if (keyguardSliceProviderGoogle != null) {
                keyguardSliceProviderGoogle.notifyChange();
            }
        }

        private Bitmap applyShadow(Bitmap bitmap) {
            BlurMaskFilter blurMaskFilter = new BlurMaskFilter(mBlurRadius, Blur.NORMAL);
            Paint paint = new Paint();
            paint.setMaskFilter(blurMaskFilter);
            int[] iArr = new int[2];
            Bitmap extractAlpha = bitmap.extractAlpha(paint, iArr);
            Bitmap createBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(createBitmap);
            Paint paint2 = new Paint();
            paint2.setAlpha(70);
            canvas.drawBitmap(extractAlpha, (float) iArr[0], ((float) iArr[1]) + (mBlurRadius / 2.0f), paint2);
            extractAlpha.recycle();
            paint2.setAlpha(255);
            canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint2);
            return createBitmap;
        }
    }

    @Override
    public boolean onCreateSliceProvider() {
        boolean sliceProvider = super.onCreateSliceProvider();
        mSmartSpaceData = new SmartSpaceData();
        mSmartSpaceController.addListener(this);
        return sliceProvider;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSmartSpaceController.removeListener(this);
    }

    @Override
    public Slice onBindSlice(Uri sliceUri) {
        Trace.beginSection("KeyguardSliceProviderGoogle#onBindSlice");
        ListBuilder sliceBuilder = new ListBuilder(getContext(), mSliceUri, -1);
        synchronized (this) {
            SmartSpaceCard currentCard = mSmartSpaceData.getCurrentCard();
            boolean hasAction = false;
            if (currentCard != null && !currentCard.isExpired() &&
                    !TextUtils.isEmpty(currentCard.getTitle())) {
                boolean isSensitive = currentCard.isSensitive();
                if (!isSensitive || isSensitive &&
                        (!mHideSensitiveContent && !currentCard.isWorkProfile()
                        || !mHideWorkContent && currentCard.isWorkProfile())) {
                    hasAction = true;
                }
            }
            if (hasAction) {
                Bitmap icon = currentCard.getIcon();
                SliceAction sliceAction = null;
                IconCompat createWithBitmap = icon == null ? null : IconCompat.createWithBitmap(icon);
                PendingIntent pendingIntent = currentCard.getPendingIntent();
                if (createWithBitmap != null || pendingIntent != null) {
                    sliceAction = SliceAction.create(pendingIntent, createWithBitmap, 1, currentCard.getTitle());
                }
                HeaderBuilder headerBuilder = new HeaderBuilder(mHeaderUri);
                headerBuilder.setTitle(currentCard.getFormattedTitle());
                if (sliceAction != null) {
                    headerBuilder.setPrimaryAction(sliceAction);
                }
                sliceBuilder.setHeader(headerBuilder);
                String subtitle = currentCard.getSubtitle();
                if (subtitle != null) {
                    RowBuilder calendarBuilder = new RowBuilder(mCalendarUri).setTitle(subtitle);
                    if (createWithBitmap != null) {
                        calendarBuilder.addEndItem(createWithBitmap, 1);
                    }
                    if (sliceAction != null) {
                        calendarBuilder.setPrimaryAction(sliceAction);
                    }
                    sliceBuilder.addRow(calendarBuilder);
                }
                addZenModeLocked(sliceBuilder);
                addPrimaryActionLocked(sliceBuilder);
                Trace.endSection();
                return sliceBuilder.build();
            }
            if (needsMediaLocked()) {
                addMediaLocked(sliceBuilder);
            } else {
                sliceBuilder.addRow(new RowBuilder(mDateUri).setTitle(getFormattedDateLocked()));
            }
            addWeather(sliceBuilder);
            addNextAlarmLocked(sliceBuilder);
            addZenModeLocked(sliceBuilder);
            addPrimaryActionLocked(sliceBuilder);
            Slice slice = sliceBuilder.build();
            if (DEBUG) Log.d(TAG, "Binding slice: " + slice);
            Trace.endSection();
            return slice;
        }
    }

    private void addWeather(ListBuilder sliceBuilder) {
        SmartSpaceCard weatherCard = mSmartSpaceData.getWeatherCard();
        if (weatherCard != null && !weatherCard.isExpired()) {
            RowBuilder weatherBuilder = new RowBuilder(mWeatherUri);
            weatherBuilder.setTitle(weatherCard.getTitle());
            Bitmap icon = weatherCard.getIcon();
            if (icon != null) {
                IconCompat createWithBitmap = IconCompat.createWithBitmap(icon);
                createWithBitmap.setTintMode(Mode.DST);
                weatherBuilder.addEndItem(createWithBitmap, 1);
            }
            sliceBuilder.addRow(weatherBuilder);
        }
    }

    @Override
    public void onSmartSpaceUpdated(SmartSpaceData smartSpaceData) {
        synchronized (this) {
            mSmartSpaceData = smartSpaceData;
        }
        SmartSpaceCard weatherCard = smartSpaceData.getWeatherCard();
        if (weatherCard == null || weatherCard.getIcon() == null || weatherCard.isIconProcessed()) {
            notifyChange();
            return;
        }
        weatherCard.setIconProcessed(true);
        new AddShadowTask(this, weatherCard).execute(new Bitmap[]{weatherCard.getIcon()});
    }

    @Override
    public void onSensitiveModeChanged(boolean hideSensitiveContent, boolean hideWorkContent) {
        synchronized (this) {
            boolean changed = false;
            if (mHideSensitiveContent != hideSensitiveContent) {
                mHideSensitiveContent = hideSensitiveContent;
                if (DEBUG) Log.d(TAG, "Public mode changed, hide data: " + hideSensitiveContent);
                changed = true;
            }
            if (mHideWorkContent != hideWorkContent) {
                mHideWorkContent = hideWorkContent;
                if (DEBUG) Log.d(TAG, "Public work mode changed, hide data: " + hideWorkContent);
                changed = true;
            }
            if (changed) {
                notifyChange();
            }
        }
    }

    @Override
    public void updateClockLocked() {
        notifyChange();
    }
}

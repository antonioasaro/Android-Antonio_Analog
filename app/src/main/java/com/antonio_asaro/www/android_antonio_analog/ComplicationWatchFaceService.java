package com.antonio_asaro.www.android_antonio_analog;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.SystemProviders;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class ComplicationWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "ComplicationWatchFaceService";

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(33); // TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    private static final int LEFT_COMPLICATION_ID = 100;
    private static final int CENTER_COMPLICATION_ID = 101;
    private static final int RIGHT_COMPLICATION_ID = 102;
    private static final int[] COMPLICATION_IDS = {LEFT_COMPLICATION_ID, CENTER_COMPLICATION_ID, RIGHT_COMPLICATION_ID};
    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            }
    };


    // Used by {@link ComplicationConfigActivity} to retrieve id for complication locations and
    // to check if complication location is supported.
    static int getComplicationId(
            com.antonio_asaro.www.android_antonio_analog.ComplicationConfigActivity.ComplicationLocation complicationLocation) {
        switch (complicationLocation) {
            case LEFT:
                return LEFT_COMPLICATION_ID;
            case CENTER:
                return CENTER_COMPLICATION_ID;
            case RIGHT:
                return RIGHT_COMPLICATION_ID;
            default:
                return -1;
        }
    }

    // Used by {@link ComplicationConfigActivity} to retrieve all complication ids.
    static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    // Used by {@link ComplicationConfigActivity} to retrieve complication types supported by
    // location.
    static int[] getSupportedComplicationTypes(
            com.antonio_asaro.www.android_antonio_analog.ComplicationConfigActivity.ComplicationLocation complicationLocation) {

        switch (complicationLocation) {
            case LEFT:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case CENTER:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case RIGHT:
                return COMPLICATION_SUPPORTED_TYPES[2];
            default:
                return new int[] {};
        }
    }

    public static boolean mWearableConnected;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ComplicationWatchFaceService.Engine> mWeakReference;

        public EngineHandler(ComplicationWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            ComplicationWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 12f;
        private static final float MINUTE_STROKE_WIDTH = 10f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;
        private static final int FORGOT_PHONE_NOTIFICATION_ID = 1;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private int mWatchHandColor;
        private int mWatchTickColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mDayDatePaint;
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private SparseArray<ComplicationData> mComplicationDataSparseArray;
        private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;
        private boolean mDimHands;
        float mBatteryLevel;
        Intent mBatteryStatus;
        long mBatteryChecked;
        Drawable mMarvinDrawable;
        Bitmap mMarvinBitmap;
        Drawable mEarthDrawable;
        Bitmap mEarthBitmap;
        Drawable mStarDrawable;
        Bitmap mStarBitmap;
        Drawable mCometDrawable;
        Bitmap mCometBitmap;
        Drawable mSaturnDrawable;
        Bitmap mSaturnBitmap;
        Drawable mDisconnectDrawable;
        Bitmap mDisonnectBitmap;

        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        SimpleDateFormat mDayDateFormat;
        java.text.DateFormat mDateFormat;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ComplicationWatchFaceService.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mWearableConnected = true;
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mDimHands = false;
            mBatteryLevel = -1;
            mBatteryChecked = System.currentTimeMillis();
            mMarvinDrawable = getResources().getDrawable(R.drawable.marvin, null);
            mMarvinBitmap = ((BitmapDrawable) mMarvinDrawable).getBitmap();
            mEarthDrawable = getResources().getDrawable(R.drawable.earth, null);
            mEarthBitmap = ((BitmapDrawable) mEarthDrawable).getBitmap();
            mStarDrawable = getResources().getDrawable(R.drawable.star, null);
            mStarBitmap = ((BitmapDrawable) mStarDrawable).getBitmap();
            mCometDrawable = getResources().getDrawable(R.drawable.comet, null);
            mCometBitmap = ((BitmapDrawable) mCometDrawable).getBitmap();
            mSaturnDrawable = getResources().getDrawable(R.drawable.saturn, null);
            mSaturnBitmap = ((BitmapDrawable) mSaturnDrawable).getBitmap();
            mDisconnectDrawable = getResources().getDrawable(R.drawable.disconnect, null);
            mDisonnectBitmap = ((BitmapDrawable) mDisconnectDrawable).getBitmap();

            initFormats();
            initializeBackground();
            initialComplications();
            initializeWatchFace();
        }


        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getDateFormat(ComplicationWatchFaceService.this);
            mDateFormat.setCalendar(mCalendar);
            mDayDateFormat = new SimpleDateFormat("EEE MMM d", Locale.getDefault());
            mDayDateFormat.setCalendar(mCalendar);
        }

        private void initialComplications() {
            mComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            ComplicationDrawable leftComplicationDrawable = new ComplicationDrawable(getApplicationContext());
            ComplicationDrawable centerComplicationDrawable = new ComplicationDrawable(getApplicationContext());
            ComplicationDrawable rightComplicationDrawable = new ComplicationDrawable(getApplicationContext());

            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            mComplicationDrawableSparseArray.put(LEFT_COMPLICATION_ID, leftComplicationDrawable);
            mComplicationDrawableSparseArray.put(CENTER_COMPLICATION_ID, centerComplicationDrawable);
            mComplicationDrawableSparseArray.put(RIGHT_COMPLICATION_ID, rightComplicationDrawable);

            leftComplicationDrawable.setBackgroundColorActive(Color.BLACK);
            centerComplicationDrawable.setBackgroundColorActive(Color.BLACK);
            rightComplicationDrawable.setBackgroundColorActive(Color.BLACK);

            leftComplicationDrawable.setTextColorActive(Color.WHITE);
            centerComplicationDrawable.setTextColorActive(Color.WHITE);
            rightComplicationDrawable.setTextColorActive(Color.WHITE);

            leftComplicationDrawable.setBorderColorActive(Color.parseColor("#FFE200"));
            centerComplicationDrawable.setBorderColorActive(Color.parseColor("#FFFFFF"));
            rightComplicationDrawable.setBorderColorActive(Color.parseColor("#673AB7"));

            int diameter = 96;
            Rect leftBounds =   new Rect (64,  144, 64+diameter,  144+diameter);
            Rect centerBounds = new Rect (153, 232, 153+diameter, 232+diameter);
            Rect rightBounds =  new Rect (241, 144, 241+diameter, 144+diameter);

            leftComplicationDrawable.setBounds(leftBounds);
            centerComplicationDrawable.setBounds(centerBounds);
            rightComplicationDrawable.setBounds(rightBounds);

            setDefaultSystemComplicationProvider(LEFT_COMPLICATION_ID, SystemProviders.UNREAD_NOTIFICATION_COUNT, ComplicationData.TYPE_SHORT_TEXT);
            setDefaultSystemComplicationProvider(CENTER_COMPLICATION_ID, SystemProviders.STEP_COUNT, ComplicationData.TYPE_SHORT_TEXT);
            setDefaultSystemComplicationProvider(RIGHT_COMPLICATION_ID, SystemProviders.WATCH_BATTERY, ComplicationData.TYPE_SHORT_TEXT);

            setActiveComplications(COMPLICATION_IDS);
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.parseColor("#00BFFF");
            mWatchTickColor = Color.parseColor("#808080");
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchTickColor);
            mTickAndCirclePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mTickAndCirclePaint.setTextSize(48);

            mDayDatePaint = new Paint();
            mDayDatePaint.setColor(mWatchTickColor);
            mDayDatePaint.setTextSize(32);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            ComplicationDrawable complicationDrawable;
            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_IDS[i]);
                complicationDrawable.setLowBitAmbient(mLowBitAmbient);
                complicationDrawable.setBurnInProtection(mBurnInProtection);
            }
        }

        @Override
        public void onComplicationDataUpdate(int complicationId, ComplicationData complicationData) {
            mComplicationDataSparseArray.put(complicationId, complicationData);
            ComplicationDrawable complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);
            invalidate();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (mDimHands) { mDimHands = false; }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            ComplicationDrawable complicationDrawable;
            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_IDS[i]);
                complicationDrawable.setInAmbientMode(mAmbient);
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchTickColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.70);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    mDimHands = !mDimHands;
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
////                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT).show();
                    int tappedComplicationId = getTappedComplicationId(x, y);
                    if (tappedComplicationId != -1) {
                        onComplicationTap(tappedComplicationId);
                    }
                    break;
            }
            invalidate();
        }

        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private int getTappedComplicationId(int x, int y) {
            int complicationId;
            ComplicationData complicationData;
            ComplicationDrawable complicationDrawable;

            long currentTimeMillis = System.currentTimeMillis();

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationData = mComplicationDataSparseArray.get(complicationId);

                if ((complicationData != null)
                        && (complicationData.isActive(currentTimeMillis))
                        && (complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED)
                        && (complicationData.getType() != ComplicationData.TYPE_EMPTY)) {

                    complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);
                    Rect complicationBoundingRect = complicationDrawable.getBounds();

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationId;
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        // Fires PendingIntent associated with complication (if it has one).
        private void onComplicationTap(int complicationId) {
            ComplicationData complicationData =
                    mComplicationDataSparseArray.get(complicationId);

            if (complicationData != null) {
                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "onComplicationTap() tap action error: " + e);
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {

                    // Watch face does not have permission to receive complication data, so launch
                    // permission request.
                    ComponentName componentName = new ComponentName(
                            getApplicationContext(),
                            ComplicationWatchFaceService.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    getApplicationContext(), componentName);

                    startActivity(permissionRequestIntent);
                }

            } else {
                Log.d(TAG, "No PendingIntent for complication " + complicationId + ".");
            }
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();

            drawBackground(canvas);
            drawBattery(canvas, now);
            drawComplications(canvas, now);
            checkWearableBT(canvas);
            drawWatchFace(canvas);
        }

        private void checkWearableBT(Canvas canvas) {
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);

            if (!mWearableConnected) {
                if (!mAmbient) {
                    canvas.drawRect(176, 60, 176+48, 68+48, paint);
                    canvas.drawBitmap(mDisonnectBitmap, 182, 68, null);
                }
            }
        }


        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }
//            canvas.drawBitmap(mStarBitmap,  124,72, null);

            if (!mAmbient) {
                canvas.drawBitmap(mSaturnBitmap, 124, 74, null);
                canvas.drawBitmap(mEarthBitmap, 240, 62, null);
                canvas.drawBitmap(mMarvinBitmap, 72, 252, null);
                canvas.drawBitmap(mCometBitmap, 272, 276, null);
            }
        }

        private void drawBattery(Canvas canvas, long currentTimeMillis) {
            Paint mBatteryPaint =  new Paint();
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            int minute = mCalendar.get(Calendar.MINUTE);
            long currentTime = System.currentTimeMillis();

            if ((mBatteryLevel == -1) || ((minute % 15) == 0) || ((currentTime - mBatteryChecked) > 30 * 60 * 1000)) {
                mBatteryChecked = currentTime;
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                mBatteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                mBatteryLevel = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            }

            //// Draw battery indicator
            if (!isInAmbientMode()) {
////                mBatteryLevel = 15;
                int b_xoff, b_yoff;
                b_xoff = 171; b_yoff = 2;
                mBatteryPaint.setARGB(0xFF, 0x00, 0xFF, 0x00);
                if (mBatteryLevel <= 75) { mBatteryPaint.setARGB(0xFF, 0xFF, 0xFF, 0x00); }
                if (mBatteryLevel <= 50) { mBatteryPaint.setARGB(0xFF, 0xFF, 0xA5, 0x00); }
                if (mBatteryLevel <= 25) { mBatteryPaint.setARGB(0xFF, 0xFF, 0x00, 0x00); }
                canvas.drawRect(20 + b_xoff, 63 + b_yoff, 20 + b_xoff + 16, 63 + b_yoff + 10, mBatteryPaint);
                canvas.drawRect(17 + b_xoff, 68 + b_yoff, 17 + b_xoff + 24, 68 + b_yoff + 40, mBatteryPaint);
                mBatteryPaint.setARGB(0xFF, 0x00, 0x00, 0x00);
                canvas.drawRect(19 + b_xoff, 72 + b_yoff, 19 + b_xoff + 20, 72 + b_yoff + 36 * (100 - mBatteryLevel) / 100, mBatteryPaint);
            }

        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            int complicationId;
            ComplicationDrawable complicationDrawable;
            Paint complicationPaint;

            complicationPaint = new Paint();
            complicationPaint.setColor(Color.WHITE);
            complicationPaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);
                complicationDrawable.draw(canvas, currentTimeMillis);
                if (mAmbient) {
                    canvas.drawCircle(complicationDrawable.getBounds().centerX(), complicationDrawable.getBounds().centerY(), complicationDrawable.getBounds().width() / 2, complicationPaint);
                }
            }
        }

        private void drawWatchFace(Canvas canvas) {
            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */

            float innerTickRadius;
            float outerTickRadius = mCenterX;
            mTickAndCirclePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                innerTickRadius = mCenterX - 32;
                if ((tickIndex == 0) || (tickIndex == 3) || (tickIndex == 6) || (tickIndex == 9)) { innerTickRadius = mCenterX - 12; }
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            innerTickRadius = mCenterX - 10;
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds = (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();
            canvas.drawText("12", mCenterX-26,  mCenterY-148, mTickAndCirclePaint);
            canvas.drawText("6",  mCenterX-14,  mCenterY+180, mTickAndCirclePaint);
            canvas.drawText("3",  mCenterX+154, mCenterY+16,  mTickAndCirclePaint);
            canvas.drawText("9",  mCenterX-176, mCenterY+16,  mTickAndCirclePaint);

            long now = System.currentTimeMillis();
            mDate.setTime(now);
            mCalendar.setTimeInMillis(now);
            int hour = mCalendar.get(Calendar.HOUR);
            int minute = mCalendar.get(Calendar.MINUTE);
            Paint mTimePaint = new Paint();
            mTimePaint.setTextSize(36);
            mTimePaint.setARGB(0xFF, 0xFF, 0xFF, 0xFF);

            int time_xoff = 4;
            int time_yoff = 0;
            if (hour == 0) {hour = 12; }
            if (hour > 9) { time_xoff = 19; }
            String time_str = String.format("%d:%02d", hour, minute);
            if (mAmbient) { time_yoff = 20; }
            canvas.drawText(time_str, 175 - time_xoff, 154 - time_yoff, mTimePaint);
////            canvas.drawText(mDayDateFormat.format(mDate), 128, 112, mDayDatePaint);

            if (mDimHands) {
                mHourPaint.setAlpha(0x60); mMinutePaint.setAlpha(0x60);
            } else {
                mHourPaint.setAlpha(0xFF); mMinutePaint.setAlpha(0xFF);
            }
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX,mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS-16, mCenterX,mCenterY - sHourHandLength, mHourPaint);
            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX,mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS-16, mCenterX,mCenterY - sMinuteHandLength, mMinutePaint);

            mMinutePaint.setStrokeWidth(4);
            mMinutePaint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(mCenterX, mCenterY - 2, CENTER_GAP_AND_CIRCLE_RADIUS * 3, mMinutePaint);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setStyle(Paint.Style.FILL);


            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX,mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS+24, mCenterX,mCenterY - mSecondHandLength, mSecondPaint);
            }

            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS * 2,
                    mSecondPaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ComplicationWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ComplicationWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}

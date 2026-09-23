package com.translator.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE =
            "result_code";

    public static final String EXTRA_RESULT_DATA =
            "result_data";

    private static final String CHANNEL_ID =
            "screen_translator";

    private static final int NOTIFICATION_ID = 77;

    /*
     * فحص الشاشة كل ثانيتين
     */
    private static final long SCAN_INTERVAL = 2000L;

    private WindowManager windowManager;

    private MediaProjection mediaProjection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private Bitmap latestBitmap;

    private TextView floatingButton;

    private TextView translationView;

    private LinearLayout menuView;

    private SelectionOverlayView selectionView;

    private TextRecognizer recognizer;

    private Translator translator;

    private int screenWidth;

    private int screenHeight;

    private int screenDensity;

    /*
     * منطقة الترجمة
     */
    private int selectedLeft;

    private int selectedTop;

    private int selectedRight;

    private int selectedBottom;

    private boolean hasSelection = false;

    private boolean translatorRunning = false;

    private String lastEnglishText = "";

    private String translatingText = "";

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    /*
     * فحص المنطقة كل ثانيتين
     */
    private final Runnable scanRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (translatorRunning &&
                            hasSelection) {

                        scanSelectedArea();
                    }

                    if (translatorRunning) {

                        handler.postDelayed(
                                this,
                                SCAN_INTERVAL
                        );
                    }
                }
            };

    /*
     * مراقبة إيقاف بث الشاشة
     */
    private final MediaProjection.Callback
            projectionCallback =
            new MediaProjection.Callback() {

                @Override
                public void onStop() {

                    stopScreenCapture();
                }
            };

    @Override
    public void onCreate() {

        super.onCreate();

        try {

            /*
             * مهم:
             * تشغيل Foreground Service أولًا.
             */
            createNotificationChannel();

            startTranslatorForeground();

            /*
             * تجهيز WindowManager
             */
            windowManager =
                    (WindowManager)
                            getSystemService(
                                    WINDOW_SERVICE
                            );

            android.util.DisplayMetrics metrics =
                    new android.util.DisplayMetrics();

            windowManager
                    .getDefaultDisplay()
                    .getRealMetrics(
                            metrics
                    );

            screenWidth =
                    metrics.widthPixels;

            screenHeight =
                    metrics.heightPixels;

            screenDensity =
                    metrics.densityDpi;

            /*
             * OCR
             */
            recognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions
                                    .DEFAULT_OPTIONS
                    );

            /*
             * English -> Arabic
             */
            TranslatorOptions options =
                    new TranslatorOptions.Builder()
                            .setSourceLanguage(
                                    TranslateLanguage.ENGLISH
                            )
                            .setTargetLanguage(
                                    TranslateLanguage.ARABIC
                            )
                            .build();

            translator =
                    Translation.getClient(
                            options
                    );

            /*
             * تنزيل نموذج الترجمة
             */
            DownloadConditions conditions =
                    new DownloadConditions.Builder()
                            .build();

            translator
                    .downloadModelIfNeeded(
                            conditions
                    )
                    .addOnFailureListener(
                            error -> {

                                ErrorLogger.save(
                                        getApplicationContext(),
                                        error
                                );
                            }
                    );

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );

            stopSelf();
        }
    }

    /*
     * إنشاء Notification Channel
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < 26) {

            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "مترجم الشاشة",
                        NotificationManager
                                .IMPORTANCE_LOW
                );

        channel.setDescription(
                "تشغيل مترجم الشاشة"
        );

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    /*
     * تشغيل Foreground Service
     */
    private void startTranslatorForeground() {

        Notification notification;

        if (Build.VERSION.SDK_INT >= 26) {

            notification =
                    new Notification.Builder(
                            this,
                            CHANNEL_ID
                    )
                            .setContentTitle(
                                    "مترجم الشاشة"
                            )
                            .setContentText(
                                    "المترجم يعمل الآن"
                            )
                            .setSmallIcon(
                                    android.R.drawable
                                            .ic_menu_search
                            )
                            .setOngoing(true)
                            .build();

        } else {

            notification =
                    new Notification.Builder(
                            this
                    )
                            .setContentTitle(
                                    "مترجم الشاشة"
                            )
                            .setContentText(
                                    "المترجم يعمل الآن"
                            )
                            .setSmallIcon(
                                    android.R.drawable
                                            .ic_menu_search
                            )
                            .setOngoing(true)
                            .build();
        }

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        try {

            if (intent == null) {

                return START_NOT_STICKY;
            }

            int resultCode =
                    intent.getIntExtra(
                            EXTRA_RESULT_CODE,
                            0
                    );

            Intent resultData;

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU) {

                resultData =
                        intent.getParcelableExtra(
                                EXTRA_RESULT_DATA,
                                Intent.class
                        );

            } else {

                resultData =
                        intent.getParcelableExtra(
                                EXTRA_RESULT_DATA
                        );
            }

            if (resultData == null) {

                throw new IllegalStateException(
                        "Screen capture data is missing"
                );
            }

            startScreenCapture(
                    resultCode,
                    resultData
            );

            return START_STICKY;

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );

            stopSelf();

            return START_NOT_STICKY;
        }
    }

    /*
     * بدء التقاط الشاشة
     */
    private void startScreenCapture(
            int resultCode,
            Intent resultData
    ) {

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        if (manager == null) {

            throw new IllegalStateException(
                    "MediaProjectionManager is unavailable"
            );
        }

        mediaProjection =
                manager.getMediaProjection(
                        resultCode,
                        resultData
                );

        if (mediaProjection == null) {

            throw new IllegalStateException(
                    "MediaProjection could not be created"
            );
        }

        /*
         * تسجيل Callback قبل VirtualDisplay
         */
        mediaProjection.registerCallback(
                projectionCallback,
                handler
        );

        imageReader =
                ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        2
                );

        imageReader.setOnImageAvailableListener(
                reader -> readScreen(reader),
                handler
        );

        virtualDisplay =
                mediaProjection.createVirtualDisplay(
                        "ScreenTranslator",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager
                                .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null
                );

        /*
         * إظهار زر 文
         */
        showFloatingButton();
    }

    /*
     * قراءة آخر صورة من الشاشة
     */
    private void readScreen(
            ImageReader reader
    ) {

        Image image = null;

        try {

            image =
                    reader.acquireLatestImage();

            if (image == null) {

                return;
            }

            Bitmap bitmap =
                    imageToBitmap(
                            image
                    );

            if (bitmap == null) {

                return;
            }

            if (latestBitmap != null &&
                    !latestBitmap.isRecycled()) {

                latestBitmap.recycle();
            }

            latestBitmap =
                    bitmap;

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );

        } finally {

            if (image != null) {

                image.close();
            }
        }
    }

    /*
     * تحويل Image إلى Bitmap
     */
    private Bitmap imageToBitmap(
            Image image
    ) {

        Image.Plane plane =
                image.getPlanes()[0];

        ByteBuffer buffer =
                plane.getBuffer();

        int pixelStride =
                plane.getPixelStride();

        int rowStride =
                plane.getRowStride();

        int rowPadding =
                rowStride -
                        pixelStride *
                                screenWidth;

        Bitmap bitmap =
                Bitmap.createBitmap(
                        screenWidth +
                                rowPadding /
                                        pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                );

        buffer.rewind();

        bitmap.copyPixelsFromBuffer(
                buffer
        );

        if (bitmap.getWidth() !=
                screenWidth) {

            Bitmap result =
                    Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            screenWidth,
                            screenHeight
                    );

            bitmap.recycle();

            return result;
        }

        return bitmap;
    }

    /*
     * زر 文
     */
    private void showFloatingButton() {

        if (floatingButton != null) {

            return;
        }

        floatingButton =
                new TextView(this);

        floatingButton.setText(
                "文"
        );

        floatingButton.setTextSize(
                22
        );

        floatingButton.setTextColor(
                Color.WHITE
        );

        floatingButton.setGravity(
                Gravity.CENTER
        );

        floatingButton.setBackground(
                createBackground(
                        0xDD2563EB,
                        100
                )
        );

        floatingButton.setElevation(
                10
        );

        floatingButton.setOnClickListener(
                view -> showMenu()
        );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        60,
                        60,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams
                                        .TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams
                                        .TYPE_PHONE,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.RIGHT |
                        Gravity.CENTER_VERTICAL;

        params.x = 20;

        params.y = 0;

        try {

            windowManager.addView(
                    floatingButton,
                    params
            );

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );
        }
    }

    /*
     * قائمة زر 文
     */
    private void showMenu() {

        if (menuView != null) {

            hideMenu();

            return;
        }

        menuView =
                new LinearLayout(this);

        menuView.setOrientation(
                LinearLayout.VERTICAL
        );

        menuView.setPadding(
                6,
                6,
                6,
                6
        );

        menuView.setBackground(
                createBackground(
                        0xF2FFFFFF,
                        18
                )
        );

        menuView.setElevation(
                15
        );

        TextView selectButton =
                new TextView(this);

        selectButton.setText(
                "تحديد منطقة جديدة"
        );

        selectButton.setTextSize(
                15
        );

        selectButton.setTextColor(
                Color.DKGRAY
        );

        selectButton.setGravity(
                Gravity.CENTER
        );

        selectButton.setPadding(
                20,
                18,
                20,
                18
        );

        TextView closeButton =
                new TextView(this);

        closeButton.setText(
                "إغلاق المترجم"
        );

        closeButton.setTextSize(
                15
        );

        closeButton.setTextColor(
                Color.rgb(
                        190,
                        40,
                        40
                )
        );

        closeButton.setGravity(
                Gravity.CENTER
        );

        closeButton.setPadding(
                20,
                18,
                20,
                18
        );

        menuView.addView(
                selectButton
        );

        menuView.addView(
                closeButton
        );

        selectButton.setOnClickListener(
                view -> {

                    hideMenu();

                    showSelection();
                }
        );

        closeButton.setOnClickListener(
                view -> {

                    hideMenu();

                    stopTranslator();
                }
        );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        270,
                        WindowManager.LayoutParams
                                .WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams
                                        .TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams
                                        .TYPE_PHONE,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.RIGHT |
                        Gravity.CENTER_VERTICAL;

        params.x = 20;

        params.y = 80;

        try {

            windowManager.addView(
                    menuView,
                    params
            );

        } catch (Throwable error) {

            menuView = null;

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );
        }
    }

    private void hideMenu() {

        if (menuView == null) {

            return;
        }

        try {

            windowManager.removeView(
                    menuView
            );

        } catch (Exception ignored) {
        }

        menuView = null;
    }

    /*
     * خلفية الأزرار والترجمة
     */
    private android.graphics.drawable.GradientDrawable
    createBackground(
            int color,
            int radius
    ) {

        android.graphics.drawable
                .GradientDrawable drawable =
                new android.graphics.drawable
                        .GradientDrawable();

        drawable.setColor(
                color
        );

        drawable.setCornerRadius(
                radius
        );

        return drawable;
    }

    /*
     * تحديد منطقة الشاشة
     */
    private void showSelection() {

        if (selectionView != null) {

            return;
        }

        hideTranslation();

        selectionView =
                new SelectionOverlayView(
                        this,
                        (
                                left,
                                top,
                                right,
                                bottom
                        ) -> {

                            selectedLeft =
                                    left;

                            selectedTop =
                                    top;

                            selectedRight =
                                    right;

                            selectedBottom =
                                    bottom;

                            hasSelection =
                                    true;

                            lastEnglishText =
                                    "";

                            translatingText =
                                    "";

                            removeSelection();

                            translatorRunning =
                                    true;

                            handler.removeCallbacks(
                                    scanRunnable
                            );

                            handler.post(
                                    scanRunnable
                            );
                        }
                );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams
                                        .TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams
                                        .TYPE_PHONE,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.LEFT;

        try {

            windowManager.addView(
                    selectionView,
                    params
            );

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );

            selectionView = null;
        }
    }

    private void removeSelection() {

        if (selectionView == null) {

            return;
        }

        try {

            windowManager.removeView(
                    selectionView
            );

        } catch (Exception ignored) {
        }

        selectionView = null;
    }

    /*
     * فحص المنطقة المحددة
     */
    private void scanSelectedArea() {

        if (!hasSelection ||
                latestBitmap == null ||
                latestBitmap.isRecycled()) {

            return;
        }

        try {

            int safeLeft =
                    Math.max(
                            0,
                            Math.min(
                                    selectedLeft,
                                    screenWidth - 1
                            )
                    );

            int safeTop =
                    Math.max(
                            0,
                            Math.min(
                                    selectedTop,
                                    screenHeight - 1
                            )
                    );

            int safeRight =
                    Math.max(
                            safeLeft + 1,
                            Math.min(
                                    selectedRight,
                                    screenWidth
                            )
                    );

            int safeBottom =
                    Math.max(
                            safeTop + 1,
                            Math.min(
                                    selectedBottom,
                                    screenHeight
                            )
                    );

            Bitmap crop =
                    Bitmap.createBitmap(
                            latestBitmap,
                            safeLeft,
                            safeTop,
                            safeRight - safeLeft,
                            safeBottom - safeTop
                    );

            InputImage input =
                    InputImage.fromBitmap(
                            crop,
                            0
                    );

            recognizer
                    .process(input)
                    .addOnSuccessListener(
                            result -> {

                                String text =
                                        result
                                                .getText()
                                                .trim();

                                try {

                                    crop.recycle();

                                } catch (Exception ignored) {
                                }

                                /*
                                 * النص اختفى
                                 */
                                if (text.isEmpty()) {

                                    lastEnglishText =
                                            "";

                                    translatingText =
                                            "";

                                    hideTranslation();

                                    return;
                                }

                                /*
                                 * النص نفسه
                                 */
                                if (text.equals(
                                        lastEnglishText
                                )) {

                                    return;
                                }

                                /*
                                 * نص جديد
                                 */
                                lastEnglishText =
                                        text;

                                translateText(
                                        text
                                );
                            }
                    )
                    .addOnFailureListener(
                            error -> {

                                try {

                                    crop.recycle();

                                } catch (Exception ignored) {
                                }

                                ErrorLogger.save(
                                        getApplicationContext(),
                                        error
                                );
                            }
                    );

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );
        }
    }

    /*
     * ترجمة النص
     */
    private void translateText(
            String text
    ) {

        if (text == null ||
                text.trim().isEmpty()) {

            hideTranslation();

            return;
        }

        if (text.equals(
                translatingText
        )) {

            return;
        }

        translatingText =
                text;

        DownloadConditions conditions =
                new DownloadConditions.Builder()
                        .build();

        translator
                .downloadModelIfNeeded(
                        conditions
                )
                .addOnSuccessListener(
                        unused -> {

                            translator
                                    .translate(
                                            text
                                    )
                                    .addOnSuccessListener(
                                            translated -> {

                                                /*
                                                 * إذا تغير النص
                                                 * أثناء الترجمة
                                                 */
                                                if (!text.equals(
                                                        lastEnglishText
                                                )) {

                                                    return;
                                                }

                                                showTranslationAt(
                                                        translated
                                                );
                                            }
                                    )
                                    .addOnFailureListener(
                                            error -> {

                                                ErrorLogger.save(
                                                        getApplicationContext(),
                                                        error
                                                );
                                            }
                                    );
                        }
                )
                .addOnFailureListener(
                        error -> {

                            ErrorLogger.save(
                                    getApplicationContext(),
                                    error
                            );
                        }
                );
    }

    /*
     * عرض الترجمة
     * خط صغير + خلفية شفافة
     */
    private void showTranslationAt(
            String text
    ) {

        if (text == null ||
                text.trim().isEmpty()) {

            hideTranslation();

            return;
        }

        if (translationView == null) {

            translationView =
                    new TextView(this);

            translationView.setTextColor(
                    Color.WHITE
            );

            translationView.setTextSize(
                    14
            );

            translationView.setGravity(
                    Gravity.CENTER
            );

            translationView.setPadding(
                    8,
                    4,
                    8,
                    4
            );

            translationView.setBackground(
                    createBackground(
                            0x99000000,
                            8
                    )
            );

            translationView.setSingleLine(
                    false
            );

            int width =
                    Math.max(
                            180,
                            Math.min(
                                    600,
                                    selectedRight -
                                            selectedLeft
                            )
                    );

            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams(
                            width,
                            WindowManager.LayoutParams
                                    .WRAP_CONTENT,
                            Build.VERSION.SDK_INT >= 26
                                    ? WindowManager.LayoutParams
                                            .TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams
                                            .TYPE_PHONE,
                            WindowManager.LayoutParams
                                    .FLAG_NOT_FOCUSABLE |
                                    WindowManager.LayoutParams
                                    .FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT
                    );

            params.gravity =
                    Gravity.TOP |
                            Gravity.LEFT;

            params.x =
                    selectedLeft;

            params.y =
                    selectedBottom + 5;

            try {

                windowManager.addView(
                        translationView,
                        params
                );

            } catch (Throwable error) {

                ErrorLogger.save(
                        getApplicationContext(),
                        error
                );

                translationView =
                        null;

                return;
            }

        } else {

            WindowManager.LayoutParams params =
                    (WindowManager.LayoutParams)
                            translationView
                                    .getLayoutParams();

            params.x =
                    selectedLeft;

            params.y =
                    selectedBottom + 5;

            try {

                windowManager.updateViewLayout(
                        translationView,
                        params
                );

            } catch (Exception ignored) {
            }
        }

        translationView.setText(
                text.trim()
        );

        translationView.setVisibility(
                TextView.VISIBLE
        );
    }

    /*
     * إخفاء الترجمة
     */
    private void hideTranslation() {

        if (translationView == null) {

            return;
        }

        try {

            windowManager.removeView(
                    translationView
            );

        } catch (Exception ignored) {
        }

        translationView = null;
    }

    /*
     * إغلاق المترجم
     */
    private void stopTranslator() {

        translatorRunning =
                false;

        hasSelection =
                false;

        lastEnglishText =
                "";

        translatingText =
                "";

        handler.removeCallbacks(
                scanRunnable
        );

        hideMenu();

        removeSelection();

        hideTranslation();

        stopScreenCapture();

        stopSelf();
    }

    /*
     * إيقاف بث الشاشة
     */
    private void stopScreenCapture() {

        translatorRunning =
                false;

        handler.removeCallbacks(
                scanRunnable
        );

        removeSelection();

        hideTranslation();

        if (floatingButton != null) {

            try {

                windowManager.removeView(
                        floatingButton
                );

            } catch (Exception ignored) {
            }

            floatingButton = null;
        }

        if (virtualDisplay != null) {

            try {

                virtualDisplay.release();

            } catch (Exception ignored) {
            }

            virtualDisplay = null;
        }

        if (imageReader != null) {

            try {

                imageReader.close();

            } catch (Exception ignored) {
            }

            imageReader = null;
        }

        if (latestBitmap != null &&
                !latestBitmap.isRecycled()) {

            try {

                latestBitmap.recycle();

            } catch (Exception ignored) {
            }

            latestBitmap = null;
        }

        if (mediaProjection != null) {

            try {

                mediaProjection.unregisterCallback(
                        projectionCallback
                );

            } catch (Exception ignored) {
            }

            try {

                mediaProjection.stop();

            } catch (Exception ignored) {
            }

            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {

        hideMenu();

        stopScreenCapture();

        if (recognizer != null) {

            try {

                recognizer.close();

            } catch (Exception ignored) {
            }
        }

        if (translator != null) {

            try {

                translator.close();

            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
                }

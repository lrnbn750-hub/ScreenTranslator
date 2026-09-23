package com.translator.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
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
     * كل كم ملي ثانية نفحص منطقة الترجمة.
     * 800ms مناسب للفيديو بدون ضغط كبير على الجهاز.
     */
    private static final long AUTO_TRANSLATE_INTERVAL = 800;

    private WindowManager windowManager;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Bitmap latestBitmap;

    private TextView floatingButton;
    private TextView translationView;
    private SelectionOverlayView selectionView;

    private TextRecognizer recognizer;
    private Translator translator;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    /*
     * منطقة الترجمة المحفوظة.
     */
    private int selectedLeft;
    private int selectedTop;
    private int selectedRight;
    private int selectedBottom;

    private boolean autoTranslationEnabled = false;

    /*
     * يمنع تشغيل OCR عدة مرات بنفس الوقت.
     */
    private boolean ocrRunning = false;

    /*
     * آخر نص تم التعرف عليه.
     */
    private String lastDetectedText = "";

    /*
     * آخر نص تمت ترجمته.
     */
    private String lastTranslatedText = "";

    private final Handler autoHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable autoTranslationRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!autoTranslationEnabled) {
                        return;
                    }

                    scanSelectedArea();

                    autoHandler.postDelayed(
                            this,
                            AUTO_TRANSLATE_INTERVAL
                    );
                }
            };

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

            windowManager =
                    (WindowManager)
                            getSystemService(
                                    WINDOW_SERVICE
                            );

            android.util.DisplayMetrics metrics =
                    new android.util.DisplayMetrics();

            windowManager
                    .getDefaultDisplay()
                    .getRealMetrics(metrics);

            screenWidth =
                    metrics.widthPixels;

            screenHeight =
                    metrics.heightPixels;

            screenDensity =
                    metrics.densityDpi;

            recognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                    );

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

            createNotificationChannel();

            startTranslatorForeground();

        } catch (Throwable error) {

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );

            stopSelf();
        }
    }

    private void startTranslatorForeground() {

        Notification notification =
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

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
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

            Intent resultData =
                    intent.getParcelableExtra(
                            EXTRA_RESULT_DATA
                    );

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
         * مهم:
         * Callback قبل createVirtualDisplay.
         */
        mediaProjection.registerCallback(
                projectionCallback,
                null
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
                null
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

        showFloatingButton();
    }

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
                    imageToBitmap(image);

            if (latestBitmap != null &&
                    !latestBitmap.isRecycled()) {

                latestBitmap.recycle();
            }

            latestBitmap = bitmap;

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

    private void showFloatingButton() {

        if (floatingButton != null) {
            return;
        }

        floatingButton =
                new TextView(this);

        floatingButton.setText("文");

        floatingButton.setTextSize(22);

        floatingButton.setTextColor(
                Color.WHITE
        );

        floatingButton.setGravity(
                Gravity.CENTER
        );

        floatingButton.setBackgroundColor(
                Color.rgb(
                        37,
                        99,
                        235
                )
        );

        floatingButton.setOnClickListener(
                view -> showSelection()
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

    private void showSelection() {

        /*
         * إذا الترجمة التلقائية تعمل وضغط المستخدم 文،
         * نوقفها مؤقتًا ونسمح له باختيار منطقة جديدة.
         */
        stopAutoTranslation();

        if (selectionView != null) {
            return;
        }

        selectionView =
                new SelectionOverlayView(
                        this,
                        (
                                left,
                                top,
                                right,
                                bottom
                        ) -> {

                            if (latestBitmap != null &&
                                    !latestBitmap
                                            .isRecycled()) {

                                /*
                                 * حفظ المنطقة.
                                 */
                                selectedLeft = left;
                                selectedTop = top;
                                selectedRight = right;
                                selectedBottom = bottom;

                                /*
                                 * نبدأ الترجمة التلقائية.
                                 */
                                startAutoTranslation();

                            } else {

                                showTranslation(
                                        "جاري تجهيز الشاشة..."
                                );
                            }

                            removeSelection();
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
     * تشغيل الترجمة التلقائية للمنطقة المحددة.
     */
    private void startAutoTranslation() {

        autoTranslationEnabled = true;

        ocrRunning = false;

        lastDetectedText = "";

        lastTranslatedText = "";

        autoHandler.removeCallbacks(
                autoTranslationRunnable
        );

        /*
         * نفحص مباشرة.
         */
        autoHandler.post(
                autoTranslationRunnable
        );
    }

    /*
     * إيقاف الترجمة التلقائية.
     */
    private void stopAutoTranslation() {

        autoTranslationEnabled = false;

        autoHandler.removeCallbacks(
                autoTranslationRunnable
        );

        ocrRunning = false;
    }

    /*
     * يفحص المنطقة المحفوظة.
     */
    private void scanSelectedArea() {

        if (!autoTranslationEnabled) {
            return;
        }

        if (ocrRunning) {
            return;
        }

        if (latestBitmap == null ||
                latestBitmap.isRecycled()) {
            return;
        }

        ocrRunning = true;

        translateArea(
                selectedLeft,
                selectedTop,
                selectedRight,
                selectedBottom,
                true
        );
    }

    /*
     * الترجمة اليدوية/التلقائية.
     */
    private void translateArea(
            int left,
            int top,
            int right,
            int bottom,
            boolean automatic
    ) {

        try {

            if (latestBitmap == null ||
                    latestBitmap.isRecycled()) {

                ocrRunning = false;

                return;
            }

            int safeLeft =
                    Math.max(
                            0,
                            Math.min(
                                    left,
                                    screenWidth - 1
                            )
                    );

            int safeTop =
                    Math.max(
                            0,
                            Math.min(
                                    top,
                                    screenHeight - 1
                            )
                    );

            int safeRight =
                    Math.max(
                            safeLeft + 1,
                            Math.min(
                                    right,
                                    screenWidth
                            )
                    );

            int safeBottom =
                    Math.max(
                            safeTop + 1,
                            Math.min(
                                    bottom,
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

            /*
             * تكبير المنطقة حتى يساعد OCR
             * مع النصوص الصغيرة داخل الفيديو.
             */
            int enlargedWidth =
                    Math.min(
                            1600,
                            Math.max(
                                    1,
                                    crop.getWidth() * 2
                            )
                    );

            int enlargedHeight =
                    Math.min(
                            1600,
                            Math.max(
                                    1,
                                    crop.getHeight() * 2
                            )
                    );

            Bitmap enlarged =
                    Bitmap.createScaledBitmap(
                            crop,
                            enlargedWidth,
                            enlargedHeight,
                            true
                    );

            crop.recycle();

            InputImage input =
                    InputImage.fromBitmap(
                            enlarged,
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

                                enlarged.recycle();

                                ocrRunning = false;

                                if (text.isEmpty()) {
                                    return;
                                }

                                /*
                                 * تنظيف الفراغات حتى لا يعتبر
                                 * نفس الجملة مختلفة بسبب OCR.
                                 */
                                String normalized =
                                        normalizeText(text);

                                if (normalized.isEmpty()) {
                                    return;
                                }

                                /*
                                 * إذا النص نفسه لم يتغير،
                                 * لا نعيد الترجمة.
                                 */
                                if (normalized.equals(
                                        lastDetectedText
                                )) {

                                    return;
                                }

                                lastDetectedText =
                                        normalized;

                                translateText(
                                        text,
                                        left,
                                        top,
                                        right,
                                        bottom
                                );
                            }
                    )
                    .addOnFailureListener(
                            error -> {

                                enlarged.recycle();

                                ocrRunning = false;

                                ErrorLogger.save(
                                        getApplicationContext(),
                                        error
                                );
                            }
                    );

        } catch (Throwable error) {

            ocrRunning = false;

            ErrorLogger.save(
                    getApplicationContext(),
                    error
            );
        }
    }

    private String normalizeText(
            String text
    ) {

        return text
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim()
                .toLowerCase();
    }

    private void translateText(
            String text,
            int left,
            int top,
            int right,
            int bottom
    ) {

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
                                    .translate(text)
                                    .addOnSuccessListener(
                                            translated -> {

                                                if (!autoTranslationEnabled) {
                                                    return;
                                                }

                                                String normalized =
                                                        normalizeText(
                                                                text
                                                        );

                                                if (normalized.equals(
                                                        lastTranslatedText
                                                )) {

                                                    return;
                                                }

                                                lastTranslatedText =
                                                        normalized;

                                                showTranslationAt(
                                                        translated,
                                                        left,
                                                        top,
                                                        right,
                                                        bottom
                                                );
                                            }
                                    )
                                    .addOnFailureListener(
                                            error -> {

                                                ocrRunning = false;

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

                            ocrRunning = false;

                            ErrorLogger.save(
                                    getApplicationContext(),
                                    error
                            );
                        }
                );
    }

    private void showTranslation(
            String text
    ) {

        showTranslationAt(
                text,
                100,
                200,
                700,
                350
        );
    }

    private void showTranslationAt(
            String text,
            int left,
            int top,
            int right,
            int bottom
    ) {

        removeTranslation();

        translationView =
                new TextView(this);

        translationView.setText(text);

        translationView.setTextColor(
                Color.BLACK
        );

        translationView.setTextSize(18);

        translationView.setGravity(
                Gravity.CENTER
        );

        translationView.setPadding(
                20,
                12,
                20,
                12
        );

        translationView.setBackgroundColor(
                Color.WHITE
        );

        int width =
                Math.max(
                        200,
                        Math.min(
                                700,
                                right - left
                        )
                );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        width,
                        130,
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

        params.x = left;

        params.y =
                Math.max(
                        50,
                        top - 140
                );

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
        }
    }

    private void removeTranslation() {

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

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        manager.createNotificationChannel(
                channel
        );
    }

    private void stopScreenCapture() {

        stopAutoTranslation();

        removeSelection();
        removeTranslation();

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

            virtualDisplay.release();

            virtualDisplay = null;
        }

        if (imageReader != null) {

            imageReader.close();

            imageReader = null;
        }

        if (latestBitmap != null &&
                !latestBitmap.isRecycled()) {

            latestBitmap.recycle();

            latestBitmap = null;
        }

        if (mediaProjection != null) {

            try {

                mediaProjection.unregisterCallback(
                        projectionCallback
                );

            } catch (Exception ignored) {
            }

            mediaProjection.stop();

            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {

        stopScreenCapture();

        if (recognizer != null) {
            recognizer.close();
        }

        if (translator != null) {
            translator.close();
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

package com.translator.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

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
import java.util.HashSet;
import java.util.Set;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE =
            "result_code";

    public static final String EXTRA_RESULT_DATA =
            "result_data";

    private static final String CHANNEL_ID =
            "screen_translator";

    private static final long SCAN_INTERVAL =
            2000L;

    private MediaProjection mediaProjection;

    private ImageReader imageReader;

    private WindowManager windowManager;

    private TextView floatingButton;

    private TextView translationView;

    private LinearLayout menuView;

    private SelectionOverlayView selectionView;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Translator translator;

    private TextRecognizer recognizer;

    private int screenWidth;

    private int screenHeight;

    private int selectedLeft;

    private int selectedTop;

    private int selectedRight;

    private int selectedBottom;

    private boolean hasSelection = false;

    private boolean running = false;

    private boolean selecting = false;

    private boolean projectionReady = false;

    private String lastEnglishText = "";

    private final Set<String> translatingTexts =
            new HashSet<>();

    private final Runnable scanRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (running &&
                            hasSelection &&
                            projectionReady) {

                        captureAndTranslate();
                    }

                    if (running) {

                        handler.postDelayed(
                                this,
                                SCAN_INTERVAL
                        );
                    }
                }
            };

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        recognizer =
                TextRecognition
                        .getClient(
                                TextRecognizerOptions
                                        .DEFAULT_OPTIONS
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

        DownloadConditions conditions =
                new DownloadConditions.Builder()
                        .build();

        translator
                .downloadModelIfNeeded(
                        conditions
                )
                .addOnFailureListener(
                        error ->
                                ErrorLogger.save(
                                        this,
                                        error
                                )
                );

        createFloatingButton();

        createTranslationView();
    }

    private void startForegroundForProjection() {

        Notification notification =
                createNotification();

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            startForeground(
                    1001,
                    notification,
                    ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );

        } else {

            startForeground(
                    1001,
                    notification
            );
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

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
    }

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            return new Notification.Builder(
                    this,
                    CHANNEL_ID
            )
                    .setContentTitle(
                            "مترجم الشاشة"
                    )
                    .setContentText(
                            "ترجمة الشاشة تعمل"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_menu_search
                    )
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(
                this
        )
                .setContentTitle(
                        "مترجم الشاشة"
                )
                .setContentText(
                        "ترجمة الشاشة تعمل"
                )
                .setSmallIcon(
                        android.R.drawable
                                .ic_menu_search
                )
                .setOngoing(true)
                .build();
    }

    private void createFloatingButton() {

        floatingButton =
                new TextView(this);

        floatingButton.setText("文");

        floatingButton.setTextSize(20);

        floatingButton.setTextColor(
                android.graphics.Color.WHITE
        );

        floatingButton.setGravity(
                Gravity.CENTER
        );

        floatingButton.setBackground(
                createRoundBackground(
                        0xDD2563EB,
                        100
                )
        );

        floatingButton.setElevation(10);

        floatingButton.setOnClickListener(
                view -> {

                    if (!selecting) {

                        showMenu();
                    }
                }
        );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        58,
                        58,
                        getOverlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.END;

        params.x = 20;

        params.y = 250;

        try {

            windowManager.addView(
                    floatingButton,
                    params
            );

        } catch (Exception error) {

            ErrorLogger.save(
                    this,
                    error
            );
        }
    }

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
                createRoundBackground(
                        0xF2FFFFFF,
                        18
                )
        );

        menuView.setElevation(12);

        TextView selectButton =
                new TextView(this);

        selectButton.setText(
                "تحديد منطقة جديدة"
        );

        selectButton.setTextSize(15);

        selectButton.setTextColor(
                android.graphics.Color.DKGRAY
        );

        selectButton.setGravity(
                Gravity.CENTER
        );

        selectButton.setPadding(
                18,
                17,
                18,
                17
        );

        TextView closeButton =
                new TextView(this);

        closeButton.setText(
                "إغلاق المترجم"
        );

        closeButton.setTextSize(15);

        closeButton.setTextColor(
                android.graphics.Color.rgb(
                        190,
                        35,
                        35
                )
        );

        closeButton.setGravity(
                Gravity.CENTER
        );

        closeButton.setPadding(
                18,
                17,
                18,
                17
        );

        menuView.addView(
                selectButton,
                new LinearLayout.LayoutParams(
                        240,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT
                )
        );

        menuView.addView(
                closeButton,
                new LinearLayout.LayoutParams(
                        240,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT
                )
        );

        selectButton.setOnClickListener(
                view -> {

                    hideMenu();

                    startSelection();
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
                        260,
                        WindowManager.LayoutParams
                                .WRAP_CONTENT,
                        getOverlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.END;

        params.x = 20;

        params.y = 315;

        try {

            windowManager.addView(
                    menuView,
                    params
            );

        } catch (Exception error) {

            menuView = null;

            ErrorLogger.save(
                    this,
                    error
            );
        }
    }

    private void hideMenu() {

        if (menuView != null) {

            try {

                windowManager.removeView(
                        menuView
                );

            } catch (Exception ignored) {
            }

            menuView = null;
        }
    }

    private void createTranslationView() {

        translationView =
                new TextView(this);

        translationView.setTextSize(14);

        translationView.setTextColor(
                android.graphics.Color.WHITE
        );

        translationView.setGravity(
                Gravity.CENTER
        );

        translationView.setPadding(
                10,
                5,
                10,
                5
        );

        translationView.setSingleLine(false);

        translationView.setBackground(
                createRoundBackground(
                        0xA8000000,
                        12
                )
        );

        translationView.setVisibility(
                TextView.GONE
        );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        420,
                        WindowManager.LayoutParams
                                .WRAP_CONTENT,
                        getOverlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams
                                .FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.START;

        try {

            windowManager.addView(
                    translationView,
                    params
            );

        } catch (Exception error) {

            ErrorLogger.save(
                    this,
                    error
            );
        }
    }

    private android.graphics.drawable.GradientDrawable
    createRoundBackground(
            int color,
            int radius
    ) {

        android.graphics.drawable
                .GradientDrawable drawable =
                new android.graphics.drawable
                        .GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(radius);

        return drawable;
    }

    private int getOverlayType() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            return WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY;
        }

        return WindowManager.LayoutParams
                .TYPE_PHONE;
    }

    private void startSelection() {

        if (!Settings.canDrawOverlays(
                this
        )) {

            return;
        }

        if (!projectionReady) {

            return;
        }

        selecting = true;

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

                            hasSelection = true;

                            selecting = false;

                            removeSelectionView();

                            lastEnglishText = "";

                            hideTranslation();

                            if (!running) {

                                running = true;

                                handler.post(
                                        scanRunnable
                                );
                            }
                        }
                );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        -1,
                        -1,
                        getOverlayType(),
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP |
                        Gravity.START;

        try {

            windowManager.addView(
                    selectionView,
                    params
            );

        } catch (Exception error) {

            ErrorLogger.save(
                    this,
                    error
            );

            selectionView = null;

            selecting = false;
        }
    }

    private void removeSelectionView() {

        if (selectionView != null) {

            try {

                windowManager.removeView(
                        selectionView
                );

            } catch (Exception ignored) {
            }

            selectionView = null;
        }
    }

    private void captureAndTranslate() {

        if (mediaProjection == null ||
                imageReader == null ||
                !projectionReady ||
                !hasSelection) {

            return;
        }

        Image image =
                imageReader
                        .acquireLatestImage();

        if (image == null) {

            return;
        }

        try {

            Bitmap bitmap =
                    imageToBitmap(
                            image
                    );

            if (bitmap == null) {

                return;
            }

            int left =
                    Math.max(
                            0,
                            selectedLeft
                    );

            int top =
                    Math.max(
                            0,
                            selectedTop
                    );

            int right =
                    Math.min(
                            bitmap.getWidth(),
                            selectedRight
                    );

            int bottom =
                    Math.min(
                            bitmap.getHeight(),
                            selectedBottom
                    );

            if (right <= left ||
                    bottom <= top) {

                bitmap.recycle();

                return;
            }

            Bitmap cropped =
                    Bitmap.createBitmap(
                            bitmap,
                            left,
                            top,
                            right - left,
                            bottom - top
                    );

            bitmap.recycle();

            InputImage input =
                    InputImage.fromBitmap(
                            cropped,
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

                                    cropped.recycle();

                                } catch (Exception ignored) {
                                }

                                if (text.isEmpty()) {

                                    lastEnglishText = "";

                                    hideTranslation();

                                    return;
                                }

                                if (text.equals(
                                        lastEnglishText
                                )) {

                                    return;
                                }

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

                                    cropped.recycle();

                                } catch (Exception ignored) {
                                }

                                ErrorLogger.save(
                                        this,
                                        error
                                );
                            }
                    );

        } catch (Exception error) {

            ErrorLogger.save(
                    this,
                    error
            );

        } finally {

            image.close();
        }
    }

    private void translateText(
            String english
    ) {

        if (english == null ||
                english.trim().isEmpty()) {

            hideTranslation();

            return;
        }

        if (translatingTexts.contains(
                english
        )) {

            return;
        }

        translatingTexts.add(
                english
        );

        translator
                .translate(
                        english
                )
                .addOnSuccessListener(
                        arabic -> {

                            translatingTexts.remove(
                                    english
                            );

                            if (!running) {

                                return;
                            }

                            if (!english.equals(
                                    lastEnglishText
                            )) {

                                return;
                            }

                            showTranslation(
                                    arabic
                            );
                        }
                )
                .addOnFailureListener(
                        error -> {

                            translatingTexts.remove(
                                    english
                            );

                            ErrorLogger.save(
                                    this,
                                    error
                            );
                        }
                );
    }

    private void showTranslation(
            String text
    ) {

        if (translationView == null ||
                text == null ||
                text.trim().isEmpty()) {

            return;
        }

        translationView.setText(
                text.trim()
        );

        translationView.setTextSize(
                14
        );

        translationView.setVisibility(
                TextView.VISIBLE
        );

        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams)
                        translationView
                                .getLayoutParams();

        params.x =
                selectedLeft;

        params.y =
                selectedBottom + 8;

        try {

            windowManager.updateViewLayout(
                    translationView,
                    params
            );

        } catch (Exception ignored) {
        }
    }

    private void hideTranslation() {

        if (translationView != null) {

            translationView.setText("");

            translationView.setVisibility(
                    TextView.GONE
            );
        }
    }

    private Bitmap imageToBitmap(
            Image image
    ) {

        Image.Plane[] planes =
                image.getPlanes();

        if (planes.length == 0) {

            return null;
        }

        ByteBuffer buffer =
                planes[0].getBuffer();

        buffer.rewind();

        int pixelStride =
                planes[0].getPixelStride();

        int rowStride =
                planes[0].getRowStride();

        if (pixelStride <= 0) {

            return null;
        }

        int rowPadding =
                rowStride -
                        pixelStride *
                                screenWidth;

        int bitmapWidth =
                screenWidth +
                        rowPadding /
                                pixelStride;

        Bitmap bitmap =
                Bitmap.createBitmap(
                        bitmapWidth,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                );

        bitmap.copyPixelsFromBuffer(
                buffer
        );

        if (bitmapWidth !=
                screenWidth) {

            Bitmap cropped =
                    Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            screenWidth,
                            screenHeight
                    );

            bitmap.recycle();

            return cropped;
        }

        return bitmap;
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        try {

            if (intent == null) {

                stopSelf();

                return START_NOT_STICKY;
            }

            int resultCode =
                    intent.getIntExtra(
                            EXTRA_RESULT_CODE,
                            -1
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

            if (resultCode == -1 ||
                    resultData == null) {

                ErrorLogger.save(
                        this,
                        new Exception(
                                "MediaProjection data missing"
                        )
                );

                stopSelf();

                return START_NOT_STICKY;
            }

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            if (manager == null) {

                stopSelf();

                return START_NOT_STICKY;
            }

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            resultData
                    );

            if (mediaProjection == null) {

                ErrorLogger.save(
                        this,
                        new Exception(
                                "MediaProjection is null"
                        )
                );

                stopSelf();

                return START_NOT_STICKY;
            }

            startForegroundForProjection();

            android.util.DisplayMetrics metrics =
                    getResources()
                            .getDisplayMetrics();

            screenWidth =
                    metrics.widthPixels;

            screenHeight =
                    metrics.heightPixels;

            imageReader =
                    ImageReader.newInstance(
                            screenWidth,
                            screenHeight,
                            PixelFormat.RGBA_8888,
                            2
                    );

            mediaProjection
                    .createVirtualDisplay(
                            "ScreenTranslator",
                            screenWidth,
                            screenHeight,
                            metrics.densityDpi,
                            0,
                            imageReader.getSurface(),
                            null,
                            handler
                    );

            projectionReady = true;

        } catch (Exception error) {

            projectionReady = false;

            ErrorLogger.save(
                    this,
                    error
            );

            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void stopTranslator() {

        hideMenu();

        running = false;

        projectionReady = false;

        hasSelection = false;

        selecting = false;

        lastEnglishText = "";

        translatingTexts.clear();

        handler.removeCallbacks(
                scanRunnable
        );

        hideTranslation();

        removeSelectionView();

        if (floatingButton != null) {

            try {

                windowManager.removeView(
                        floatingButton
                );

            } catch (Exception ignored) {
            }

            floatingButton = null;
        }

        if (translationView != null) {

            try {

                windowManager.removeView(
                        translationView
                );

            } catch (Exception ignored) {
            }

            translationView = null;
        }

        if (imageReader != null) {

            try {

                imageReader.close();

            } catch (Exception ignored) {
            }

            imageReader = null;
        }

        if (mediaProjection != null) {

            try {

                mediaProjection.stop();

            } catch (Exception ignored) {
            }

            mediaProjection = null;
        }

        stopSelf();
    }

    @Override
    public void onDestroy() {

        running = false;

        projectionReady = false;

        handler.removeCallbacks(
                scanRunnable
        );

        hideMenu();

        hideTranslation();

        removeSelectionView();

        if (recognizer != null) {

            recognizer.close();
        }

        if (translator != null) {

            translator.close();
        }

        if (imageReader != null) {

            try {

                imageReader.close();

            } catch (Exception ignored) {
            }
        }

        if (mediaProjection != null) {

            try {

                mediaProjection.stop();

            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
                }

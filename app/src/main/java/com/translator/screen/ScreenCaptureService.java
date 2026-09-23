package com.translator.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.Translation;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
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
            2000;

    private MediaProjection mediaProjection;

    private ImageReader imageReader;

    private WindowManager windowManager;

    private TextView floatingButton;

    private TextView translationView;

    private SelectionOverlayView selectionView;

    private int screenWidth;
    private int screenHeight;

    private int selectedLeft;
    private int selectedTop;
    private int selectedRight;
    private int selectedBottom;

    private boolean hasSelection = false;

    private boolean running = false;

    private boolean selecting = false;

    private Handler handler =
            new Handler(Looper.getMainLooper());

    private Translator translator;

    private TextRecognizer recognizer;

    private String lastEnglishText = "";

    private final Set<String> translatingTexts =
            new HashSet<>();

    private final Runnable scanRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (running &&
                            hasSelection) {

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

        startForeground(
                1001,
                createNotification()
        );

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
                Translation
                        .getClient(options);

        DownloadConditions conditions =
                new DownloadConditions.Builder()
                        .requireWifi()
                        .build();

        translator
                .downloadModelIfNeeded(
                        conditions
                )
                .addOnFailureListener(
                        error -> {

                            ErrorLogger.save(
                                    this,
                                    error
                            );
                        }
                );

        createFloatingButton();

        createTranslationView();
    }

    private Notification createNotification() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

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
                            "المترجم يعمل"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_menu_search
                    )
                    .build();

        } else {

            return new Notification.Builder(
                    this
            )
                    .setContentTitle(
                            "مترجم الشاشة"
                    )
                    .setContentText(
                            "المترجم يعمل"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_menu_search
                    )
                    .build();
        }
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

                    if (selecting) {

                        return;
                    }

                    showMenu();
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

        windowManager.addView(
                floatingButton,
                params
        );
    }

    private void showMenu() {

        final String[] items = {
                "تحديد منطقة جديدة",
                "إغلاق المترجم"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("مترجم الشاشة")
                .setItems(
                        items,
                        (dialog, which) -> {

                            if (which == 0) {

                                startSelection();

                            } else {

                                stopTranslator();
                            }
                        }
                )
                .show();
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

        translationView.setVisibility(
                TextView.GONE
        );

        translationView.setBackground(
                createRoundBackground(
                        0xA8000000,
                        12
                )
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

        windowManager.addView(
                translationView,
                params
        );
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

        } else {

            return WindowManager.LayoutParams
                    .TYPE_PHONE;
        }
    }

    private void startSelection() {

        if (!Settings.canDrawOverlays(this)) {

            return;
        }

        selecting = true;

        selectionView =
                new SelectionOverlayView(
                        this,
                        (
                                left,
                                top,
                                right,
                                bottom
                        ) -> {

                            selectedLeft = left;
                            selectedTop = top;
                            selectedRight = right;
                            selectedBottom = bottom;

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

        windowManager.addView(
                selectionView,
                params
        );
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
                !hasSelection) {

            return;
        }

        Image image =
                imageReader.acquireLatestImage();

        if (image == null) {

            return;
        }

        try {

            Bitmap bitmap =
                    imageToBitmap(image);

            if (bitmap == null) {

                image.close();

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

                image.close();

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

                                lastEnglishText = text;

                                translateText(text);
                            }
                    )
                    .addOnFailureListener(
                            error -> {

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

        if (english.isEmpty()) {

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
                .translate(english)
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

        if (params.y < 0) {

            params.y = selectedTop;
        }

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

        int pixelStride =
                planes[0].getPixelStride();

        int rowStride =
                planes[0].getRowStride();

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

    private void stopTranslator() {

        running = false;

        hasSelection = false;

        selecting = false;

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

        stopSelf();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        try {

            int resultCode =
                    intent.getIntExtra(
                            EXTRA_RESULT_CODE,
                            -1
                    );

            Intent resultData =
                    intent.getParcelableExtra(
                            EXTRA_RESULT_DATA
                    );

            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    MEDIA_PROJECTION_SERVICE
                            );

            mediaProjection =
                    manager.getMediaProjection(
                            resultCode,
                            resultData
                    );

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

            mediaProjection.createVirtualDisplay(
                    "ScreenTranslator",
                    screenWidth,
                    screenHeight,
                    metrics.densityDpi,
                    0,
                    imageReader.getSurface(),
                    null,
                    handler
            );

        } catch (Exception error) {

            ErrorLogger.save(
                    this,
                    error
            );
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        running = false;

        handler.removeCallbacks(
                scanRunnable
        );

        hideTranslation();

        removeSelectionView();

        if (recognizer != null) {

            recognizer.close();
        }

        if (translator != null) {

            translator.close();
        }

        if (imageReader != null) {

            imageReader.close();
        }

        if (mediaProjection != null) {

            mediaProjection.stop();
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

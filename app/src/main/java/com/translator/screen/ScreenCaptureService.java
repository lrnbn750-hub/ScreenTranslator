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
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "screen_translator";
    private static final int NOTIFICATION_ID = 77;

    private WindowManager windowManager;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Bitmap latestBitmap;

    private View floatingButton;
    private SelectionOverlayView selectionView;
    private TextView translationView;

    private Translator translator;
    private TextRecognizer recognizer;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);

        android.util.DisplayMetrics metrics =
                new android.util.DisplayMetrics();

        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        recognizer =
                TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                );

        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.ARABIC)
                        .build();

        translator = Translation.getClient(options);

        createNotificationChannel();

        Notification notification =
                new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("مترجم الشاشة")
                        .setContentText("المترجم يعمل فوق التطبيقات")
                        .setSmallIcon(android.R.drawable.ic_menu_search)
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
            int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        int resultCode =
                intent.getIntExtra(
                        EXTRA_RESULT_CODE,
                        0
                );

        Intent resultData =
                intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultData == null) {
            return START_NOT_STICKY;
        }

        startProjection(resultCode, resultData);

        return START_STICKY;
    }

    private void startProjection(
            int resultCode,
            Intent resultData) {

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

        imageReader =
                ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        2
                );

        imageReader.setOnImageAvailableListener(
                reader -> {

                    Image image = null;

                    try {

                        image = reader.acquireLatestImage();

                        if (image == null) {
                            return;
                        }

                        latestBitmap =
                                imageToBitmap(image);

                    } catch (Exception ignored) {

                    } finally {

                        if (image != null) {
                            image.close();
                        }
                    }

                },
                null
        );

        virtualDisplay =
                mediaProjection.createVirtualDisplay(
                        "ScreenTranslator",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null
                );

        mediaProjection.registerCallback(
                new MediaProjection.Callback() {

                    @Override
                    public void onStop() {

                        stopProjection();
                    }

                },
                null
        );

        showFloatingButton();
    }

    private Bitmap imageToBitmap(Image image) {

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

        bitmap.copyPixelsFromBuffer(buffer);

        if (bitmap.getWidth() != screenWidth) {

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

    private void showFloatingButton() {

        if (floatingButton != null) {
            return;
        }

        TextView button =
                new TextView(this);

        button.setText("文");
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(
                Color.rgb(37, 99, 235)
        );

        button.setOnClickListener(
                v -> showSelection()
        );

        floatingButton = button;

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        58,
                        58,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.RIGHT | Gravity.CENTER_VERTICAL;

        params.x = 20;
        params.y = 0;

        try {
            windowManager.addView(
                    floatingButton,
                    params
            );
        } catch (Exception ignored) {
        }
    }

    private void showSelection() {

        if (selectionView != null) {
            return;
        }

        selectionView =
                new SelectionOverlayView(
                        this,
                        (left, top, right, bottom) -> {

                            if (latestBitmap == null) {
                                removeSelection();
                                return;
                            }

                            translateRegion(
                                    left,
                                    top,
                                    right,
                                    bottom
                            );

                            removeSelection();
                        }
                );

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity = Gravity.TOP | Gravity.LEFT;

        try {
            windowManager.addView(
                    selectionView,
                    params
            );
        } catch (Exception ignored) {
            selectionView = null;
        }
    }

    private void removeSelection() {

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

    private void translateRegion(
            int left,
            int top,
            int right,
            int bottom) {

        int l =
                Math.max(
                        0,
                        Math.min(left, screenWidth - 1)
                );

        int t =
                Math.max(
                        0,
                        Math.min(top, screenHeight - 1)
                );

        int r =
                Math.max(
                        l + 1,
                        Math.min(right, screenWidth)
                );

        int b =
                Math.max(
                        t + 1,
                        Math.min(bottom, screenHeight)
                );

        Bitmap cropped =
                Bitmap.createBitmap(
                        latestBitmap,
                        l,
                        t,
                        r - l,
                        b - t
                );

        InputImage image =
                InputImage.fromBitmap(
                        cropped,
                        0
                );

        recognizer.process(image)
                .addOnSuccessListener(
                        result -> {

                            String text =
                                    result.getText().trim();

                            cropped.recycle();

                            if (text.isEmpty()) {
                                showTranslation(
                                        "لم يتم العثور على نص"
                                );
                                return;
                            }

                            downloadAndTranslate(
                                    text,
                                    l,
                                    t,
                                    r,
                                    b
                            );
                        }
                )
                .addOnFailureListener(
                        e -> {

                            cropped.recycle();

                            showTranslation(
                                    "تعذر قراءة النص"
                            );
                        }
                );
    }

    private void downloadAndTranslate(
            String text,
            int left,
            int top,
            int right,
            int bottom) {

        DownloadConditions conditions =
                new DownloadConditions.Builder()
                        .build();

        translator
                .downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        unused -> {

                            translator
                                    .translate(text)
                                    .addOnSuccessListener(
                                            translated -> {

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
                                            e ->
                                                    showTranslation(
                                                            "تعذر الترجمة"
                                                    )
                                    );
                        }
                )
                .addOnFailureListener(
                        e ->
                                showTranslation(
                                        "يجب تنزيل نموذج الترجمة أولاً"
                                )
                );
    }

    private void showTranslation(
            String text) {

        showTranslationAt(
                text,
                100,
                150,
                700,
                300
        );
    }

    private void showTranslationAt(
            String text,
            int left,
            int top,
            int right,
            int bottom) {

        removeTranslation();

        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextColor(Color.BLACK);
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER);
        view.setPadding(20, 12, 20, 12);
        view.setBackgroundColor(
                Color.WHITE
        );

        translationView = view;

        int width =
                Math.max(
                        180,
                        Math.min(
                                700,
                                right - left
                        )
                );

        int height = 120;

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        width,
                        height,
                        Build.VERSION.SDK_INT >= 26
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity =
                Gravity.TOP | Gravity.LEFT;

        params.x = left;
        params.y = Math.max(
                0,
                top - height - 10
        );

        try {
            windowManager.addView(
                    translationView,
                    params
            );
        } catch (Exception ignored) {
        }
    }

    private void removeTranslation() {

        if (translationView != null) {

            try {
                windowManager.removeView(
                        translationView
                );
            } catch (Exception ignored) {
            }

            translationView = null;
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "مترجم الشاشة",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    private void stopProjection() {

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

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {

        stopProjection();

        if (recognizer != null) {
            recognizer.close();
        }

        if (translator != null) {
            translator.close();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
                                           }

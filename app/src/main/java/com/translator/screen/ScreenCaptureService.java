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

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "screen_translator";
    private static final int NOTIFICATION_ID = 77;

    private WindowManager windowManager;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Bitmap latestBitmap;

    private TextView floatingButton;
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
                        .setSourceLanguage(
                                TranslateLanguage.ENGLISH
                        )
                        .setTargetLanguage(
                                TranslateLanguage.ARABIC
                        )
                        .build();

        translator =
                Translation.getClient(options);

        createNotificationChannel();

        Notification notification =
                new Notification.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setContentTitle("مترجم الشاشة")
                        .setContentText(
                                "المترجم يعمل فوق التطبيقات"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_search
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
                intent.getParcelableExtra(
                        EXTRA_RESULT_DATA
                );

        if (resultData == null) {
            return START_NOT_STICKY;
        }

        startProjection(
                resultCode,
                resultData
        );

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

                        image =
                                reader.acquireLatestImage();

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
                Color.rgb(
                        37,
                        99,
                       

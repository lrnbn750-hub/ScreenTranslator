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
import com.google.mlkit.translation.Translation;
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

    /*
     * فحص الشاشة كل ثانيتين
     */
    private static final long SCAN_INTERVAL =
            2000L;

    private MediaProjection mediaProjection;

    private ImageReader imageReader;

    private WindowManager windowManager;

    private TextView floatingButton;

    private TextView translationView;

    private SelectionOverlayView selectionView;

    private Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    private Translator translator;

    private TextRecognizer recognizer;

    private int screenWidth;

    private int screenHeight;

    private int selectedLeft;

    private int selectedTop;

    private int selectedRight;

    private int selectedBottom;

    private boolean hasSelection =
            false;

    private boolean running =
            false;

    private boolean selecting =
            false;

    private String lastEnglishText =
            "";

    private final Set<String>
            translatingTexts =
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

        /*
         * إنشاء قناة الإشعارات
         */
        createNotificationChannel();

        /*
         * تشغيل الخدمة في المقدمة
         */
        startForeground(
                1001,
                createNotification()
        );

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        /*
         * OCR
         */
        recognizer =
                TextRecognition
                        .getClient(
                                TextRecognizerOptions
                                        .DEFAULT_OPTIONS
                        );

        /*
         * English

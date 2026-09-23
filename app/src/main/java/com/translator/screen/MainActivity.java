package com.translator.screen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int SCREEN_CAPTURE_REQUEST = 1001;

    private TextView statusText;
    private TextView errorText;
    private Button startButton;
    private Button errorButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        installCrashHandler();

        setContentView(R.layout.activity_main);

        statusText =
                findViewById(R.id.statusText);

        startButton =
                findViewById(R.id.startButton);

        errorText =
                findViewById(R.id.errorText);

        errorButton =
                findViewById(R.id.errorButton);

        showSavedError();

        startButton.setOnClickListener(
                v -> startTranslator()
        );

        errorButton.setOnClickListener(
                v -> {

                    String error =
                            ErrorLogger.get(this);

                    if (error.isEmpty()) {

                        errorText.setText(
                                "لا توجد أخطاء محفوظة."
                        );

                    } else {

                        errorText.setVisibility(
                                View.VISIBLE
                        );

                        errorText.setText(error);
                    }
                }
        );
    }

    private void installCrashHandler() {

        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {

                    ErrorLogger.save(
                            getApplicationContext(),
                            throwable
                    );

                    android.os.Process
                            .killProcess(
                                    android.os.Process.myPid()
                            );

                    System.exit(10);
                }
        );
    }

    private void showSavedError() {

        String error =
                ErrorLogger.get(this);

        if (error.isEmpty()) {

            errorText.setVisibility(
                    View.GONE
            );

            return;
        }

        errorText.setVisibility(
                View.VISIBLE
        );

        errorText.setText(
                "آخر خطأ محفوظ:\n\n" +
                        error
        );

        statusText.setText(
                "تم العثور على خطأ سابق"
        );
    }

    private void startTranslator() {

        if (!Settings.canDrawOverlays(this)) {

            statusText.setText(
                    "اسمح للتطبيق بالظهور فوق التطبيقات"
            );

            Intent intent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                    "package:" +
                                            getPackageName()
                            )
                    );

            startActivity(intent);

            return;
        }

        requestScreenCapture();
    }

    private void requestScreenCapture() {

        statusText.setText(
                "اختر السماح بالتقاط الشاشة..."
        );

        android.media.projection.MediaProjectionManager manager =
                (android.media.projection.MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        Intent captureIntent =
                manager.createScreenCaptureIntent();

        startActivityForResult(
                captureIntent,
                SCREEN_CAPTURE_REQUEST
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != SCREEN_CAPTURE_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK ||
                data == null) {

            statusText.setText(
                    "تم إلغاء إذن التقاط الشاشة"
            );

            return;
        }

        try {

            Intent serviceIntent =
                    new Intent(
                            this,
                            ScreenCaptureService.class
                    );

            serviceIntent.putExtra(
                    ScreenCaptureService.EXTRA_RESULT_CODE,
                    resultCode
            );

            serviceIntent.putExtra(
                    ScreenCaptureService.EXTRA_RESULT_DATA,
                    data
            );

            if (android.os.Build.VERSION.SDK_INT >= 26) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }

            statusText.setText(
                    "مترجم الشاشة يعمل الآن"
            );

            startButton.setText(
                    "المترجم يعمل"
            );

        } catch (Throwable error) {

            ErrorLogger.save(
                    this,
                    error
            );

            statusText.setText(
                    "حدث خطأ. افتح سجل الأخطاء."
            );
        }
    }
}

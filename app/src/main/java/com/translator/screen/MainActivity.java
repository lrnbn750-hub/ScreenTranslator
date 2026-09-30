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
    private Button startButton;
    private Button privacyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
        );

        statusText =
                findViewById(R.id.statusText);

        startButton =
                findViewById(R.id.startButton);

        privacyButton =
                findViewById(R.id.privacyButton);

        startButton.setOnClickListener(
                view -> startTranslator()
        );

        privacyButton.setOnClickListener(
                view -> {

                    Intent intent =
                            new Intent(
                                    MainActivity.this,
                                    PrivacyPolicyActivity.class
                            );

                    startActivity(intent);
                }
        );
    }

    private void startTranslator() {

        try {

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

        } catch (Throwable error) {

            statusText.setText(
                    "حدث خطأ أثناء تشغيل المترجم"
            );
        }
    }

    private void requestScreenCapture() {

        try {

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

        } catch (Throwable error) {

            statusText.setText(
                    "تعذر طلب إذن التقاط الشاشة"
            );
        }
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

        if (requestCode !=
                SCREEN_CAPTURE_REQUEST) {

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

            statusText.setText(
                    "تعذر تشغيل مترجم الشاشة"
            );
        }
    }
}

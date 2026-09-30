package com.translator.screen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int SCREEN_CAPTURE_REQUEST = 1001;

    private TextView statusText;
    private Button startButton;
    private Button privacyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {

            setContentView(R.layout.activity_main);

            statusText =
                    findViewById(R.id.statusText);

            startButton =
                    findViewById(R.id.startButton);

            privacyButton =
                    findViewById(R.id.privacyButton);

            if (startButton != null) {

                startButton.setOnClickListener(
                        view -> startTranslator()
                );
            }

            if (privacyButton != null) {

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

        } catch (Throwable error) {

            showStartupError(error);
        }
    }


    private void showStartupError(Throwable error) {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setGravity(
                Gravity.CENTER
        );

        layout.setPadding(
                30,
                30,
                30,
                30
        );

        layout.setBackgroundColor(
                Color.rgb(5, 11, 24)
        );


        TextView title =
                new TextView(this);

        title.setText(
                "حدث خطأ عند تشغيل التطبيق"
        );

        title.setTextColor(
                Color.WHITE
        );

        title.setTextSize(22);

        title.setGravity(
                Gravity.CENTER
        );


        TextView details =
                new TextView(this);

        details.setText(
                "\n\n" +
                error.getClass().getName() +
                "\n\n" +
                String.valueOf(
                        error.getMessage()
                )
        );

        details.setTextColor(
                Color.rgb(255, 120, 120)
        );

        details.setTextSize(13);

        details.setGravity(
                Gravity.CENTER
        );


        layout.addView(title);

        layout.addView(details);


        setContentView(layout);
    }


    private void startTranslator() {

        try {

            if (!Settings.canDrawOverlays(this)) {

                if (statusText != null) {

                    statusText.setText(
                            "اسمح للتطبيق بالظهور فوق التطبيقات"
                    );
                }

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

            if (statusText != null) {

                statusText.setText(
                        "حدث خطأ أثناء تشغيل المترجم"
                );
            }
        }
    }


    private void requestScreenCapture() {

        try {

            if (statusText != null) {

                statusText.setText(
                        "اختر السماح بالتقاط الشاشة..."
                );
            }

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

            if (statusText != null) {

                statusText.setText(
                        "تعذر طلب إذن التقاط الشاشة"
                );
            }
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

            if (statusText != null) {

                statusText.setText(
                        "تم إلغاء إذن التقاط الشاشة"
                );
            }

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

            if (statusText != null) {

                statusText.setText(
                        "مترجم الشاشة يعمل الآن"
                );
            }

            if (startButton != null) {

                startButton.setText(
                        "المترجم يعمل"
                );
            }

        } catch (Throwable error) {

            if (statusText != null) {

                statusText.setText(
                        "تعذر تشغيل مترجم الشاشة"
                );
            }
        }
    }
}

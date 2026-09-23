package com.translator.screen;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ErrorLogger {

    private static final String PREFS_NAME = "translator_errors";
    private static final String ERROR_KEY = "last_error";

    private ErrorLogger() {
    }

    public static void save(
            Context context,
            Throwable throwable
    ) {

        try {

            StringWriter stringWriter =
                    new StringWriter();

            PrintWriter printWriter =
                    new PrintWriter(stringWriter);

            throwable.printStackTrace(printWriter);

            printWriter.flush();

            String error =
                    stringWriter.toString();

            SharedPreferences preferences =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            // commit مهم هنا لأنه يحفظ الخطأ فورًا
            preferences.edit()
                    .putString(
                            ERROR_KEY,
                            error
                    )
                    .commit();

        } catch (Exception ignored) {
        }
    }

    public static String get(
            Context context
    ) {

        try {

            SharedPreferences preferences =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            return preferences.getString(
                    ERROR_KEY,
                    ""
            );

        } catch (Exception ignored) {

            return "";
        }
    }

    public static void clear(
            Context context
    ) {

        try {

            context.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
            )
                    .edit()
                    .remove(ERROR_KEY)
                    .commit();

        } catch (Exception ignored) {
        }
    }
}

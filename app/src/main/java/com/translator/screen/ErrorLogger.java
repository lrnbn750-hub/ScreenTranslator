package com.translator.screen;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ErrorLogger {

    private static final String PREFS = "error_log";
    private static final String KEY_ERROR = "last_error";

    private ErrorLogger() {
    }

    public static void save(
            Context context,
            Throwable error
    ) {

        try {

            StringWriter writer =
                    new StringWriter();

            PrintWriter printWriter =
                    new PrintWriter(writer);

            error.printStackTrace(printWriter);

            printWriter.flush();

            SharedPreferences preferences =
                    context.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    );

            preferences.edit()
                    .putString(
                            KEY_ERROR,
                            writer.toString()
                    )
                    .apply();

        } catch (Exception ignored) {
        }
    }

    public static String get(Context context) {

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return preferences.getString(
                KEY_ERROR,
                ""
        );
    }

    public static void clear(Context context) {

        context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        )
                .edit()
                .remove(KEY_ERROR)
                .apply();
    }
}

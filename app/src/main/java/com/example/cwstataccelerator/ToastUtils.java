package com.example.cwstataccelerator;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

public class ToastUtils {

    public static void showCustomToast(Context context, String message, int durationInMillis) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        toast.show();

        // Cancel the toast after the specified duration
        new Handler().postDelayed(toast::cancel, durationInMillis);
    }
}

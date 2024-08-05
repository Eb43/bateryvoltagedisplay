package com.example.timenotification;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;


public class BitmapUtils {

    public static Bitmap textToBitmap(String text) {
        int textColor = Color.BLACK;
        int textSize = 130;
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        // Use setARGB to explicitly define the color
        //paint.setARGB(255, 0, 0, 0); // Equivalent to Color.BLACK
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.LEFT); // Align text center
        paint.setTypeface(Typeface.DEFAULT_BOLD);


        // Create a 190x190 pixel bitmap
        int width = 190;
        int height = 190;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        //canvas.drawColor(Color.argb(200, 255, 255, 255)); //was canvas.drawColor(Color.TRANSPARENT);

        // Check Android version and set canvas color accordingly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 and above
            canvas.drawColor(Color.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) { // Android 8.0 and 8.1
            canvas.drawColor(Color.argb(200, 200, 200, 200)); // Light gray for Android 8
        } else {
            canvas.drawColor(Color.argb(255, 255, 255, 255)); // Fully opaque white for other versions
        }

        // Calculate the x and y positions to center the text
        float x = (width - paint.measureText(text)) / 2;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (height - metrics.ascent - metrics.descent) / 2;

        canvas.drawText(text, x, y, paint);

        return bitmap;
    }
}
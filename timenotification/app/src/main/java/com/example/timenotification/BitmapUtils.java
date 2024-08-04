package com.example.timenotification;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;


public class BitmapUtils {

    public static Bitmap textToBitmap(String text) {
        int textColor = Color.BLACK;
        int textSize = 130;
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);


        // Create a 190x190 pixel bitmap
        int width = 190;
        int height = 190;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.argb(200, 255, 255, 255)); //was canvas.drawColor(Color.TRANSPARENT);

        // Calculate the x and y positions to center the text
        float x = (width - paint.measureText(text)) / 2;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (height - metrics.ascent - metrics.descent) / 2;

        canvas.drawText(text, x, y, paint);

        return bitmap;
    }
}
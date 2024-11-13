package com.example.timenotification;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;


public class BitmapUtils {

    public static Bitmap textToBitmap(String text, int textColor) {
        //int textColor = Color.BLACK;
        int textSize = 120;
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        // Use setARGB to explicitly define the color
        //paint.setARGB(255, 0, 0, 0); // Equivalent to Color.BLACK
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.LEFT); // Align text center
        //paint.setTypeface(Typeface.DEFAULT_BOLD);
        // Use a more compact font: MONOSPACE is generally more compact for numbers
        //paint.setTypeface(Typeface.MONOSPACE);
        // Set the typeface to bold monospace
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setLetterSpacing(-0.2f); // Adjust letter spacing for tighter character fit
        //paint.setSubpixelText(true);



        // Create a 190x190 pixel bitmap
        int width = 190;
        int height = 190;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        //canvas.drawColor(Color.argb(200, 255, 255, 255)); //was canvas.drawColor(Color.TRANSPARENT);

        if (textColor == Color.BLACK) {
            canvas.drawColor(Color.WHITE);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }

        // Stretch the canvas vertically
        canvas.save(); // Save the current canvas state
        canvas.scale(1.0f, 1.4f, width / 2f, height / 2f); // Stretch height by 40%

        // Calculate the x and y positions to center the text
        float x = (width - paint.measureText(text)) / 2;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (height - metrics.ascent - metrics.descent) / 2;

        // Draw the text with the vertically scaled canvas
        canvas.drawText(text, x, y, paint);
        canvas.restore(); // Restore the original canvas scale

        return bitmap;
    }
}
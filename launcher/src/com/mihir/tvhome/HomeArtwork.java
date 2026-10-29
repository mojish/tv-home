package com.mihir.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Decode raster art near its displayed size; retain a bounded copy of vector/adaptive icons. */
final class HomeArtwork {
    private HomeArtwork() { }

    static Drawable load(Resources res, int id, int width, int height) {
        if (id == 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.inScaled = false;
            BitmapFactory.decodeResource(res, id, options);
            if (options.outWidth > 0 && options.outHeight > 0) {
                int sample = 1;
                while (options.outWidth / (sample * 2) >= width && options.outHeight / (sample * 2) >= height) sample *= 2;
                options.inSampleSize = sample;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeResource(res, id, options);
                if (bitmap != null) {
                    float scale = Math.min(1f, Math.min(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight()));
                    if (scale < 1f) {
                        Bitmap smaller = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                        if (smaller != bitmap) bitmap.recycle();
                        bitmap = smaller;
                    }
                    return new BitmapDrawable(res, bitmap);
                }
            }
            return bounded(res, res.getDrawable(id, null), width, height);
        } catch (Resources.NotFoundException | IllegalArgumentException e) { return null; }
    }

    static Drawable bounded(Resources res, Drawable source, int width, int height) {
        if (source == null) return null;
        int w = source.getIntrinsicWidth() > 0 ? source.getIntrinsicWidth() : width;
        int h = source.getIntrinsicHeight() > 0 ? source.getIntrinsicHeight() : height;
        float scale = Math.min(1f, Math.min(width / (float) w, height / (float) h));
        w = Math.max(1, Math.round(w * scale)); h = Math.max(1, Math.round(h * scale));
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Rect oldBounds = new Rect(source.getBounds());
        source.setBounds(0, 0, w, h);
        source.draw(new Canvas(bitmap));
        source.setBounds(oldBounds);
        return new BitmapDrawable(res, bitmap);
    }
}

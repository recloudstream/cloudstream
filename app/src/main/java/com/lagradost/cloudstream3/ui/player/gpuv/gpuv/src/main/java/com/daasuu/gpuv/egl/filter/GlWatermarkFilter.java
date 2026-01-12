package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public class GlWatermarkFilter extends GlOverlayFilter {

    private Bitmap bitmap;
    private Position position = Position.LEFT_TOP;

    public GlWatermarkFilter(Bitmap bitmap) {
        this.bitmap = bitmap;
    }


    public GlWatermarkFilter(Bitmap bitmap, Position position) {
        this.bitmap = bitmap;
        this.position = position;
    }

    @Override
    protected void drawCanvas(Canvas canvas) {
        if (bitmap != null && !bitmap.isRecycled()) {
            switch (position) {
                case LEFT_TOP:
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    break;
                case LEFT_BOTTOM:
                    canvas.drawBitmap(bitmap, 0, canvas.getHeight() - bitmap.getHeight(), null);
                    break;
                case RIGHT_TOP:
                    canvas.drawBitmap(bitmap, canvas.getWidth() - bitmap.getWidth(), 0, null);
                    break;
                case RIGHT_BOTTOM:
                    canvas.drawBitmap(bitmap, canvas.getWidth() - bitmap.getWidth(), canvas.getHeight() - bitmap.getHeight(), null);
                    break;
            }
        }
    }

    public enum Position {
        LEFT_TOP,
        LEFT_BOTTOM,
        RIGHT_TOP,
        RIGHT_BOTTOM
    }
}

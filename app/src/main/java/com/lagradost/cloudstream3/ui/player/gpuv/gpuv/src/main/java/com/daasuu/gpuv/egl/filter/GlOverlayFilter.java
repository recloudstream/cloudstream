package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Size;



public abstract class GlOverlayFilter extends GlFilter {

    private int[] textures = new int[1];

    private Bitmap bitmap = null;

    protected Size inputResolution = new Size(1280, 720);

    public GlOverlayFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    private final static String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform lowp sampler2D sTexture;\n" +
                    "uniform lowp sampler2D oTexture;\n" +
                    "void main() {\n" +
                    "   lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
                    "   lowp vec4 textureColor2 = texture2D(oTexture, vTextureCoord);\n" +
                    "   \n" +
                    "   gl_FragColor = mix(textureColor, textureColor2, textureColor2.a);\n" +
                    "}\n";

    public void setResolution(Size resolution) {
        this.inputResolution = resolution;
    }

    @Override
    public void setFrameSize(int width, int height) {
        super.setFrameSize(width, height);
        setResolution(new Size(width, height));
    }

    private void createBitmap() {
        releaseBitmap(bitmap);
        bitmap = Bitmap.createBitmap(inputResolution.getWidth(), inputResolution.getHeight(), Bitmap.Config.ARGB_8888);
    }

    @Override
    public void setup() {
        super.setup();// 1
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        createBitmap();
    }

    @Override
    public void onDraw() {
        if (bitmap == null) {
            createBitmap();
        }
        if (bitmap.getWidth() != inputResolution.getWidth() || bitmap.getHeight() != inputResolution.getHeight()) {
            createBitmap();
        }

        bitmap.eraseColor(Color.argb(0, 0, 0, 0));
        Canvas bitmapCanvas = new Canvas(bitmap);
        bitmapCanvas.scale(1, -1, bitmapCanvas.getWidth() / 2, bitmapCanvas.getHeight() / 2);
        drawCanvas(bitmapCanvas);

        int offsetDepthMapTextureUniform = getHandle("oTexture");// 3

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        if (bitmap != null && !bitmap.isRecycled()) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0);
        }

        GLES20.glUniform1i(offsetDepthMapTextureUniform, 3);
    }

    protected abstract void drawCanvas(Canvas canvas);

    public static void releaseBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}

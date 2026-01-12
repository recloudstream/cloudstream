package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlPixelationFilter extends GlFilter {

    private static final String PIXELATION_FRAGMENT_SHADER = "" +
            "precision highp float;\n" +

            "varying highp vec2 vTextureCoord;\n" +

            "uniform float imageWidthFactor;\n" +
            "uniform float imageHeightFactor;\n" +
            "uniform lowp sampler2D sTexture;\n" +
            "uniform float pixel;\n" +

            "void main()\n" +
            "{\n" +
            "  vec2 uv  = vTextureCoord.xy;\n" +
            "  float dx = pixel * imageWidthFactor;\n" +
            "  float dy = pixel * imageHeightFactor;\n" +
            "  vec2 coord = vec2(dx * floor(uv.x / dx), dy * floor(uv.y / dy));\n" +
            "  vec3 tc = texture2D(sTexture, coord).xyz;\n" +
            "  gl_FragColor = vec4(tc, 1.0);\n" +
            "}";

    public GlPixelationFilter() {
        super(DEFAULT_VERTEX_SHADER, PIXELATION_FRAGMENT_SHADER);
    }

    private float pixel = 1f;
    private float imageWidthFactor = 1f / 720;
    private float imageHeightFactor = 1f / 720;

    @Override
    public void setFrameSize(int width, int height) {
        super.setFrameSize(width, height);
        imageWidthFactor = 1f / width;
        imageHeightFactor = 1f / height;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("pixel"), pixel);
        GLES20.glUniform1f(getHandle("imageWidthFactor"), imageWidthFactor);
        GLES20.glUniform1f(getHandle("imageHeightFactor"), imageHeightFactor);
    }

    public void setPixel(final float pixel) {
        this.pixel = pixel;
    }
}

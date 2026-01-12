package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlGaussianBlurFilter extends GlFilter {

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;" +
                    "attribute vec4 aTextureCoord;" +

                    "const lowp int GAUSSIAN_SAMPLES = 9;" +

                    "uniform highp float texelWidthOffset;" +
                    "uniform highp float texelHeightOffset;" +
                    "uniform highp float blurSize;" +

                    "varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];" +

                    "void main() {" +
                    "gl_Position = aPosition;" +
                    "highp vec2 vTextureCoord = aTextureCoord.xy;" +

                    // Calculate the positions for the blur
                    "int multiplier = 0;" +
                    "highp vec2 blurStep;" +
                    "highp vec2 singleStepOffset = vec2(texelHeightOffset, texelWidthOffset) * blurSize;" +

                    "for (lowp int i = 0; i < GAUSSIAN_SAMPLES; i++) {" +
                    "multiplier = (i - ((GAUSSIAN_SAMPLES - 1) / 2));" +
                    // Blur in x (horizontal)
                    "blurStep = float(multiplier) * singleStepOffset;" +
                    "blurCoordinates[i] = vTextureCoord.xy + blurStep;" +
                    "}" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "const lowp int GAUSSIAN_SAMPLES = 9;" +
                    "varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];" +

                    "uniform lowp sampler2D sTexture;" +

                    "void main() {" +
                    "lowp vec4 sum = vec4(0.0);" +

                    "sum += texture2D(sTexture, blurCoordinates[0]) * 0.05;" +
                    "sum += texture2D(sTexture, blurCoordinates[1]) * 0.09;" +
                    "sum += texture2D(sTexture, blurCoordinates[2]) * 0.12;" +
                    "sum += texture2D(sTexture, blurCoordinates[3]) * 0.15;" +
                    "sum += texture2D(sTexture, blurCoordinates[4]) * 0.18;" +
                    "sum += texture2D(sTexture, blurCoordinates[5]) * 0.15;" +
                    "sum += texture2D(sTexture, blurCoordinates[6]) * 0.12;" +
                    "sum += texture2D(sTexture, blurCoordinates[7]) * 0.09;" +
                    "sum += texture2D(sTexture, blurCoordinates[8]) * 0.05;" +

                    "gl_FragColor = sum;" +
                    "}";

    private float texelWidthOffset = 0.01f;
    private float texelHeightOffset = 0.01f;
    private float blurSize = 0.2f;

    public GlGaussianBlurFilter() {
        super(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public float getTexelWidthOffset() {
        return texelWidthOffset;
    }

    public void setTexelWidthOffset(final float texelWidthOffset) {
        this.texelWidthOffset = texelWidthOffset;
    }

    public float getTexelHeightOffset() {
        return texelHeightOffset;
    }

    public void setTexelHeightOffset(final float texelHeightOffset) {
        this.texelHeightOffset = texelHeightOffset;
    }

    public float getBlurSize() {
        return blurSize;
    }

    public void setBlurSize(final float blurSize) {
        this.blurSize = blurSize;
    }


    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("texelWidthOffset"), texelWidthOffset);
        GLES20.glUniform1f(getHandle("texelHeightOffset"), texelHeightOffset);
        GLES20.glUniform1f(getHandle("blurSize"), blurSize);
    }

}

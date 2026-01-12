package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;


public class GlBoxBlurFilter extends GlFilter {

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;" +
                    "attribute vec4 aTextureCoord;" +

                    "uniform highp float texelWidthOffset;" +
                    "uniform highp float texelHeightOffset;" +
                    "uniform highp float blurSize;" +

                    "varying highp vec2 centerTextureCoordinate;" +
                    "varying highp vec2 oneStepLeftTextureCoordinate;" +
                    "varying highp vec2 twoStepsLeftTextureCoordinate;" +
                    "varying highp vec2 oneStepRightTextureCoordinate;" +
                    "varying highp vec2 twoStepsRightTextureCoordinate;" +

                    "void main() {" +
                    "gl_Position = aPosition;" +

                    "vec2 firstOffset = vec2(1.5 * texelWidthOffset, 1.5 * texelHeightOffset) * blurSize;" +
                    "vec2 secondOffset = vec2(3.5 * texelWidthOffset, 3.5 * texelHeightOffset) * blurSize;" +

                    "centerTextureCoordinate = aTextureCoord.xy;" +
                    "oneStepLeftTextureCoordinate = centerTextureCoordinate - firstOffset;" +
                    "twoStepsLeftTextureCoordinate = centerTextureCoordinate - secondOffset;" +
                    "oneStepRightTextureCoordinate = centerTextureCoordinate + firstOffset;" +
                    "twoStepsRightTextureCoordinate = centerTextureCoordinate + secondOffset;" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "uniform lowp sampler2D sTexture;" +

                    "varying highp vec2 centerTextureCoordinate;" +
                    "varying highp vec2 oneStepLeftTextureCoordinate;" +
                    "varying highp vec2 twoStepsLeftTextureCoordinate;" +
                    "varying highp vec2 oneStepRightTextureCoordinate;" +
                    "varying highp vec2 twoStepsRightTextureCoordinate;" +

                    "void main() {" +
                    "lowp vec4 color = texture2D(sTexture, centerTextureCoordinate) * 0.2;" +
                    "color += texture2D(sTexture, oneStepLeftTextureCoordinate) * 0.2;" +
                    "color += texture2D(sTexture, oneStepRightTextureCoordinate) * 0.2;" +
                    "color += texture2D(sTexture, twoStepsLeftTextureCoordinate) * 0.2;" +
                    "color += texture2D(sTexture, twoStepsRightTextureCoordinate) * 0.2;" +
                    "gl_FragColor = color;" +
                    "}";

    private float texelWidthOffset = 0.003f;
    private float texelHeightOffset = 0.003f;
    private float blurSize = 1.0f;


    public GlBoxBlurFilter() {
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

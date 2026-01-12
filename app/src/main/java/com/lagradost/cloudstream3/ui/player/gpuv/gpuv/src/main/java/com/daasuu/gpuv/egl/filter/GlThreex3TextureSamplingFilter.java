package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlThreex3TextureSamplingFilter extends GlFilter {
    private static final String THREE_X_THREE_TEXTURE_SAMPLING_VERTEX_SHADER =
            "attribute vec4 aPosition;" +
                    "attribute vec4 aTextureCoord;" +

                    "uniform highp float texelWidth;" +
                    "uniform highp float texelHeight;" +

                    "varying highp vec2 textureCoordinate;" +
                    "varying highp vec2 leftTextureCoordinate;" +
                    "varying highp vec2 rightTextureCoordinate;" +

                    "varying highp vec2 topTextureCoordinate;" +
                    "varying highp vec2 topLeftTextureCoordinate;" +
                    "varying highp vec2 topRightTextureCoordinate;" +

                    "varying highp vec2 bottomTextureCoordinate;" +
                    "varying highp vec2 bottomLeftTextureCoordinate;" +
                    "varying highp vec2 bottomRightTextureCoordinate;" +

                    "void main() {" +
                    "gl_Position = aPosition;" +

                    "vec2 widthStep = vec2(texelWidth, 0.0);" +
                    "vec2 heightStep = vec2(0.0, texelHeight);" +
                    "vec2 widthHeightStep = vec2(texelWidth, texelHeight);" +
                    "vec2 widthNegativeHeightStep = vec2(texelWidth, -texelHeight);" +

                    "textureCoordinate = aTextureCoord.xy;" +
                    "leftTextureCoordinate = textureCoordinate - widthStep;" +
                    "rightTextureCoordinate = textureCoordinate + widthStep;" +

                    "topTextureCoordinate = textureCoordinate - heightStep;" +
                    "topLeftTextureCoordinate = textureCoordinate - widthHeightStep;" +
                    "topRightTextureCoordinate = textureCoordinate + widthNegativeHeightStep;" +

                    "bottomTextureCoordinate = textureCoordinate + heightStep;" +
                    "bottomLeftTextureCoordinate = textureCoordinate - widthNegativeHeightStep;" +
                    "bottomRightTextureCoordinate = textureCoordinate + widthHeightStep;" +
                    "}";

    private float texelWidth;
    private float texelHeight;

    public GlThreex3TextureSamplingFilter(String fragmentShaderSource) {
        super(THREE_X_THREE_TEXTURE_SAMPLING_VERTEX_SHADER, fragmentShaderSource);
    }

    public float getTexelWidth() {
        return texelWidth;
    }

    public void setTexelWidth(float texelWidth) {
        this.texelWidth = texelWidth;
    }

    public float getTexelHeight() {
        return texelHeight;
    }

    public void setTexelHeight(float texelHeight) {
        this.texelHeight = texelHeight;
    }

    @Override
    public void setFrameSize(final int width, final int height) {
        texelWidth = 1f / width;
        texelHeight = 1f / height;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("texelWidth"), texelWidth);
        GLES20.glUniform1f(getHandle("texelHeight"), texelHeight);
    }

}

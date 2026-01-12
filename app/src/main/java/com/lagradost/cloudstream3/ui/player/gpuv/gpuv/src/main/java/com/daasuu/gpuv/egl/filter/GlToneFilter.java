package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlToneFilter extends GlThreex3TextureSamplingFilter {

    private static final String FRAGMENT_SHADER =
            "precision highp float;\n" +

                    "uniform lowp sampler2D sTexture;\n" +

                    "varying vec2 textureCoordinate;\n" +
                    "varying vec2 leftTextureCoordinate;\n" +
                    "varying vec2 rightTextureCoordinate;\n" +

                    "varying vec2 topTextureCoordinate;\n" +
                    "varying vec2 topLeftTextureCoordinate;\n" +
                    "varying vec2 topRightTextureCoordinate;\n" +

                    "varying vec2 bottomTextureCoordinate;\n" +
                    "varying vec2 bottomLeftTextureCoordinate;\n" +
                    "varying vec2 bottomRightTextureCoordinate;\n" +

//			"uniform highp float intensity;" +
                    "uniform highp float threshold;" +
                    "uniform highp float quantizationLevels;" +

                    "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);" +

                    "void main() {\n" +
                    "vec4 textureColor = texture2D(sTexture, textureCoordinate);" +

                    "float bottomLeftIntensity = texture2D(sTexture, bottomLeftTextureCoordinate).r;" +
                    "float topRightIntensity = texture2D(sTexture, topRightTextureCoordinate).r;" +
                    "float topLeftIntensity = texture2D(sTexture, topLeftTextureCoordinate).r;" +
                    "float bottomRightIntensity = texture2D(sTexture, bottomRightTextureCoordinate).r;" +
                    "float leftIntensity = texture2D(sTexture, leftTextureCoordinate).r;" +
                    "float rightIntensity = texture2D(sTexture, rightTextureCoordinate).r;" +
                    "float bottomIntensity = texture2D(sTexture, bottomTextureCoordinate).r;" +
                    "float topIntensity = texture2D(sTexture, topTextureCoordinate).r;" +
                    "float h = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;" +
                    "float v = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;" +

                    "float mag = length(vec2(h, v));" +
                    "vec3 posterizedImageColor = floor((textureColor.rgb * quantizationLevels) + 0.5) / quantizationLevels;" +
                    "float thresholdTest = 1.0 - step(threshold, mag);" +
                    "gl_FragColor = vec4(posterizedImageColor * thresholdTest, textureColor.a);" +
                    "}";

    private float threshold = 0.2f;
    private float quantizationLevels = 10f;


    public GlToneFilter() {
        super(FRAGMENT_SHADER);
    }

    //////////////////////////////////////////////////////////////////////////

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(final float threshold) {
        this.threshold = threshold;
    }

    public float getQuantizationLevels() {
        return quantizationLevels;
    }

    public void setQuantizationLevels(final float quantizationLevels) {
        this.quantizationLevels = quantizationLevels;
    }

    //////////////////////////////////////////////////////////////////////////

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("threshold"), threshold);
        GLES20.glUniform1f(getHandle("quantizationLevels"), quantizationLevels);
    }
}

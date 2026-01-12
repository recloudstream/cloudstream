package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import static android.opengl.GLES20.glUniform1f;


public class GlBilateralFilter extends GlFilter {

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;" +
                    "attribute vec4 aTextureCoord;" +

                    "const lowp int GAUSSIAN_SAMPLES = 9;" +

                    "uniform highp float texelWidthOffset;" +
                    "uniform highp float texelHeightOffset;" +
                    "uniform highp float blurSize;" +

                    "varying highp vec2 vTextureCoord;" +
                    "varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];" +

                    "void main() {" +
                    "gl_Position = aPosition;" +
                    "vTextureCoord = aTextureCoord.xy;" +

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

                    "uniform lowp sampler2D sTexture;" +

                    "const lowp int GAUSSIAN_SAMPLES = 9;" +
                    "varying highp vec2 vTextureCoord;" +
                    "varying highp vec2 blurCoordinates[GAUSSIAN_SAMPLES];" +

                    "const mediump float distanceNormalizationFactor = 1.5;" +

                    "void main() {" +
                    "lowp vec4 centralColor = texture2D(sTexture, blurCoordinates[4]);" +
                    "lowp float gaussianWeightTotal = 0.18;" +
                    "lowp vec4 sum = centralColor * 0.18;" +

                    "lowp vec4 sampleColor = texture2D(sTexture, blurCoordinates[0]);" +
                    "lowp float distanceFromCentralColor;" +

                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +

                    "lowp float gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[1]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[2]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[3]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[5]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.15 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[6]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.12 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[7]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.09 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "sampleColor = texture2D(sTexture, blurCoordinates[8]);" +
                    "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);" +
                    "gaussianWeight = 0.05 * (1.0 - distanceFromCentralColor);" +
                    "gaussianWeightTotal += gaussianWeight;" +
                    "sum += sampleColor * gaussianWeight;" +

                    "gl_FragColor = sum / gaussianWeightTotal;" +
                    "}";

    private float texelWidthOffset = 0.004f;
    private float texelHeightOffset = 0.004f;
    private float blurSize = 1.0f;

    public GlBilateralFilter() {
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
        glUniform1f(getHandle("texelWidthOffset"), texelWidthOffset);
        glUniform1f(getHandle("texelHeightOffset"), texelHeightOffset);
        glUniform1f(getHandle("blurSize"), blurSize);
    }


}

package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlHalftoneFilter extends GlFilter {

    private static final String HALFTONE_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +

            " uniform lowp sampler2D sTexture;\n" +

            "uniform highp float fractionalWidthOfPixel;\n" +
            "uniform highp float aspectRatio;\n" +

            "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +

            "void main()\n" +
            "{\n" +
            "  highp vec2 sampleDivisor = vec2(fractionalWidthOfPixel, fractionalWidthOfPixel / aspectRatio);\n" +
            "  highp vec2 samplePos = vTextureCoord - mod(vTextureCoord, sampleDivisor) + 0.5 * sampleDivisor;\n" +
            "  highp vec2 textureCoordinateToUse = vec2(vTextureCoord.x, (vTextureCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));\n" +
            "  highp vec2 adjustedSamplePos = vec2(samplePos.x, (samplePos.y * aspectRatio + 0.5 - 0.5 * aspectRatio));\n" +
            "  highp float distanceFromSamplePoint = distance(adjustedSamplePos, textureCoordinateToUse);\n" +
            "  lowp vec3 sampledColor = texture2D(sTexture, samplePos).rgb;\n" +
            "  highp float dotScaling = 1.0 - dot(sampledColor, W);\n" +
            "  lowp float checkForPresenceWithinDot = 1.0 - step(distanceFromSamplePoint, (fractionalWidthOfPixel * 0.5) * dotScaling);\n" +
            "  gl_FragColor = vec4(vec3(checkForPresenceWithinDot), 1.0);\n" +
            "}";

    public GlHalftoneFilter() {
        super(DEFAULT_VERTEX_SHADER, HALFTONE_FRAGMENT_SHADER);
    }

    private float fractionalWidthOfPixel = 0.01f;
    private float aspectRatio = 1f;

    public void setFractionalWidthOfAPixel(float fractionalWidthOfAPixel) {
        this.fractionalWidthOfPixel = fractionalWidthOfAPixel;
    }

    @Override
    public void setFrameSize(int width, int height) {
        super.setFrameSize(width, height);
        aspectRatio = (float) height / (float) width;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("fractionalWidthOfPixel"), fractionalWidthOfPixel);
        GLES20.glUniform1f(getHandle("aspectRatio"), aspectRatio);
    }
}

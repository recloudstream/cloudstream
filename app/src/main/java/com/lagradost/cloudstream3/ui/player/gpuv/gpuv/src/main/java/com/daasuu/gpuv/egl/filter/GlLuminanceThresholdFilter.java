package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlLuminanceThresholdFilter extends GlFilter {

    private static final String LUMINANCE_THRESHOLD_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            "varying highp vec2 vTextureCoord;\n" +
            "\n" +
            "uniform lowp sampler2D sTexture;\n" +
            "uniform highp float threshold;\n" +
            "\n" +
            "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    highp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "    highp float luminance = dot(textureColor.rgb, W);\n" +
            "    highp float thresholdResult = step(threshold, luminance);\n" +
            "    \n" +
            "    gl_FragColor = vec4(vec3(thresholdResult), textureColor.w);\n" +
            "}";

    public GlLuminanceThresholdFilter() {
        super(DEFAULT_VERTEX_SHADER, LUMINANCE_THRESHOLD_FRAGMENT_SHADER);
    }

    private float threshold = 0.5f;

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("threshold"), threshold);
    }
}

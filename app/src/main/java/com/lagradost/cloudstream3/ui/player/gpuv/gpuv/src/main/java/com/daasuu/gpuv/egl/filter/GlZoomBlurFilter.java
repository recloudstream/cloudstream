package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.graphics.PointF;
import android.opengl.GLES20;

public class GlZoomBlurFilter extends GlFilter {

    private static final String ZOOM_BLUR_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            "\n" +
            "uniform lowp sampler2D sTexture;\n" +
            "\n" +
            "uniform highp vec2 blurCenter;\n" +
            "uniform highp float blurSize;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    // TODO: Do a more intelligent scaling based on resolution here\n" +
            "    highp vec2 samplingOffset = 1.0/100.0 * (blurCenter - vTextureCoord) * blurSize;\n" +
            "    \n" +
            "    lowp vec4 fragmentColor = texture2D(sTexture, vTextureCoord) * 0.18;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord + samplingOffset) * 0.15;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord + (2.0 * samplingOffset)) *  0.12;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord + (3.0 * samplingOffset)) * 0.09;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord + (4.0 * samplingOffset)) * 0.05;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord - samplingOffset) * 0.15;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord - (2.0 * samplingOffset)) *  0.12;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord - (3.0 * samplingOffset)) * 0.09;\n" +
            "    fragmentColor += texture2D(sTexture, vTextureCoord - (4.0 * samplingOffset)) * 0.05;\n" +
            "    \n" +
            "    gl_FragColor = fragmentColor;\n" +
            "}\n";

    private PointF blurCenter = new PointF(0.5f, 0.5f);
    private float blurSize = 1f;

    public GlZoomBlurFilter() {
        super(DEFAULT_VERTEX_SHADER, ZOOM_BLUR_FRAGMENT_SHADER);
    }

    public void setBlurCenter(PointF blurCenter) {
        this.blurCenter = blurCenter;
    }

    public void setBlurSize(float blurSize) {
        this.blurSize = blurSize;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform2f(getHandle("blurCenter"), blurCenter.x, blurCenter.y);
        GLES20.glUniform1f(getHandle("blurSize"), blurSize);
    }

}

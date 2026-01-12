package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlCrosshatchFilter extends GlFilter {

    private static final String CROSSHATCH_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            " uniform lowp sampler2D sTexture;\n" +
            "uniform highp float crossHatchSpacing;\n" +
            "uniform highp float lineWidth;\n" +
            "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +
            "void main()\n" +
            "{\n" +
            "highp float luminance = dot(texture2D(sTexture, vTextureCoord).rgb, W);\n" +
            "lowp vec4 colorToDisplay = vec4(1.0, 1.0, 1.0, 1.0);\n" +
            "if (luminance < 1.00)\n" +
            "{\n" +
            "if (mod(vTextureCoord.x + vTextureCoord.y, crossHatchSpacing) <= lineWidth)\n" +
            "{\n" +
            "colorToDisplay = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "}\n" +
            "}\n" +
            "if (luminance < 0.75)\n" +
            "{\n" +
            "if (mod(vTextureCoord.x - vTextureCoord.y, crossHatchSpacing) <= lineWidth)\n" +
            "{\n" +
            "colorToDisplay = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "}\n" +
            "}\n" +
            "if (luminance < 0.50)\n" +
            "{\n" +
            "if (mod(vTextureCoord.x + vTextureCoord.y - (crossHatchSpacing / 2.0), crossHatchSpacing) <= lineWidth)\n" +
            "{\n" +
            "colorToDisplay = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "}\n" +
            "}\n" +
            "if (luminance < 0.3)\n" +
            "{\n" +
            "if (mod(vTextureCoord.x - vTextureCoord.y - (crossHatchSpacing / 2.0), crossHatchSpacing) <= lineWidth)\n" +
            "{\n" +
            "colorToDisplay = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "}\n" +
            "}\n" +
            "gl_FragColor = colorToDisplay;\n" +
            "}\n";

    public GlCrosshatchFilter() {
        super(DEFAULT_VERTEX_SHADER, CROSSHATCH_FRAGMENT_SHADER);
    }

    private float crossHatchSpacing = 0.03f;
    private float lineWidth = 0.003f;

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("crossHatchSpacing"), crossHatchSpacing);
        GLES20.glUniform1f(getHandle("lineWidth"), lineWidth);
    }

    public void setCrossHatchSpacing(float crossHatchSpacing) {
        this.crossHatchSpacing = crossHatchSpacing;
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    @Override
    public void setFrameSize(int width, int height) {
        super.setFrameSize(width, height);

        float singlePixelSpacing;
        if (width != 0) {
            singlePixelSpacing = 1.0f / (float) width;
        } else {
            singlePixelSpacing = 1.0f / 2048.0f;
        }
        if (crossHatchSpacing < singlePixelSpacing) {
            this.crossHatchSpacing = singlePixelSpacing;
        }

    }
}

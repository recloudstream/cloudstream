package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlVibranceFilter extends GlFilter {

    private static final String VIBRANCE_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            "\n" +
            " uniform lowp sampler2D sTexture;\n" +
            " uniform lowp float vibrance;\n" +
            "\n" +
            "void main() {\n" +
            "    lowp vec4 color = texture2D(sTexture, vTextureCoord);\n" +
            "    lowp float average = (color.r + color.g + color.b) / 3.0;\n" +
            "    lowp float mx = max(color.r, max(color.g, color.b));\n" +
            "    lowp float amt = (mx - average) * (-vibrance * 3.0);\n" +
            "    color.rgb = mix(color.rgb, vec3(mx), amt);\n" +
            "    gl_FragColor = color;\n" +
            "}";

    public GlVibranceFilter() {
        super(DEFAULT_VERTEX_SHADER, VIBRANCE_FRAGMENT_SHADER);
    }

    private float vibrance = 0f;

    public void setVibrance(float vibrance) {
        this.vibrance = vibrance;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("vibrance"), vibrance);
    }
}

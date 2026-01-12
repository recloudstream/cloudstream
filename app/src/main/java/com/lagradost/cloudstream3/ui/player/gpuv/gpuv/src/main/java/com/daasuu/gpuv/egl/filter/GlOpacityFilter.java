package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

/**
 * Adjusts the alpha channel of the incoming image
 * opacity: The value to multiply the incoming alpha channel for each pixel by (0.0 - 1.0, with 1.0 as the default)
 */
public class GlOpacityFilter extends GlFilter {

    private static final String OPACITY_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying highp vec2 vTextureCoord;\n" +
            "  \n" +
            " uniform lowp sampler2D sTexture;\n" +
            " uniform lowp float opacity;\n" +
            "  \n" +
            "  void main()\n" +
            "  {\n" +
            "      lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "      \n" +
            "      gl_FragColor = vec4(textureColor.rgb, textureColor.a * opacity);\n" +
            "  }\n";

    public GlOpacityFilter() {
        super(DEFAULT_VERTEX_SHADER, OPACITY_FRAGMENT_SHADER);
    }

    private float opacity = 1f;

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("opacity"), opacity);
    }

}

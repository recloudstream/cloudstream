package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

public class GlHighlightShadowFilter extends GlFilter {

    private static final String HIGHLIGHT_SHADOW_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " uniform lowp sampler2D sTexture;\n" +
            " varying vec2 vTextureCoord;\n" +
            "  \n" +
            " uniform lowp float shadows;\n" +
            " uniform lowp float highlights;\n" +
            " \n" +
            " const mediump vec3 luminanceWeighting = vec3(0.3, 0.3, 0.3);\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            " 	lowp vec4 source = texture2D(sTexture, vTextureCoord);\n" +
            " 	mediump float luminance = dot(source.rgb, luminanceWeighting);\n" +
            " \n" +
            " 	mediump float shadow = clamp((pow(luminance, 1.0/(shadows+1.0)) + (-0.76)*pow(luminance, 2.0/(shadows+1.0))) - luminance, 0.0, 1.0);\n" +
            " 	mediump float highlight = clamp((1.0 - (pow(1.0-luminance, 1.0/(2.0-highlights)) + (-0.8)*pow(1.0-luminance, 2.0/(2.0-highlights)))) - luminance, -1.0, 0.0);\n" +
            " 	lowp vec3 result = vec3(0.0, 0.0, 0.0) + ((luminance + shadow + highlight) - 0.0) * ((source.rgb - vec3(0.0, 0.0, 0.0))/(luminance - 0.0));\n" +
            " \n" +
            " 	gl_FragColor = vec4(result.rgb, source.a);\n" +
            " }";

    public GlHighlightShadowFilter() {
        super(DEFAULT_VERTEX_SHADER, HIGHLIGHT_SHADOW_FRAGMENT_SHADER);
    }

    private float shadows = 1f;
    private float highlights = 0f;

    public void setShadows(float shadows) {
        this.shadows = shadows;
    }

    public void setHighlights(float highlights) {
        this.highlights = highlights;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("shadows"), shadows);
        GLES20.glUniform1f(getHandle("highlights"), highlights);
    }
}

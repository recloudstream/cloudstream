package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;

/**
 * exposure: The adjusted exposure (-10.0 - 10.0, with 0.0 as the default)
 */
public class GlExposureFilter extends GlFilter {

    private static final String EXPOSURE_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            " \n" +
            " uniform lowp sampler2D sTexture;\n" +
            " uniform highp float exposure;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "     highp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "     \n" +
            "     gl_FragColor = vec4(textureColor.rgb * pow(2.0, exposure), textureColor.w);\n" +
            " } ";

    public GlExposureFilter() {
        super(DEFAULT_VERTEX_SHADER, EXPOSURE_FRAGMENT_SHADER);
    }

    private float exposure = 1f;

    public void setExposure(float exposure) {
        this.exposure = exposure;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("exposure"), exposure);
    }
}

package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;


import android.opengl.GLES20;

/**
 * Changes the contrast of the image.
 * contrast value ranges from 0.0 to 4.0, with 1.0 as the normal level
 */
public class GlContrastFilter extends GlFilter {

    private static final String CONTRAST_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            " \n" +
            " uniform lowp sampler2D sTexture;\n" +
            " uniform lowp float contrast;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "     lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "     \n" +
            "     gl_FragColor = vec4(((textureColor.rgb - vec3(0.5)) * contrast + vec3(0.5)), textureColor.w);\n" +
            " }";


    public GlContrastFilter() {
        super(DEFAULT_VERTEX_SHADER, CONTRAST_FRAGMENT_SHADER);
    }

    private float contrast = 1.2f;

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("contrast"), contrast);
    }
}

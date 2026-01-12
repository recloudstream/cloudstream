package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;


import android.opengl.GLES20;

/**
 * brightness value ranges from -1.0 to 1.0, with 0.0 as the normal level
 */
public class GlBrightnessFilter extends GlFilter {
    private static final String BRIGHTNESS_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            " \n" +
            " uniform lowp sampler2D sTexture;\n" +
            " uniform lowp float brightness;\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "     lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "     \n" +
            "     gl_FragColor = vec4((textureColor.rgb + vec3(brightness)), textureColor.w);\n" +
            " }";

    public GlBrightnessFilter() {
        super(DEFAULT_VERTEX_SHADER, BRIGHTNESS_FRAGMENT_SHADER);
    }

    private float brightness = 0f;

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("brightness"), brightness);
    }
}

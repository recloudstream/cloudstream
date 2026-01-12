package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlVignetteFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "varying vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +

                    "uniform lowp vec2 vignetteCenter;" +
                    "uniform highp float vignetteStart;" +
                    "uniform highp float vignetteEnd;" +

                    "void main() {" +
                    "lowp vec3 rgb = texture2D(sTexture, vTextureCoord).rgb;" +
                    "lowp float d = distance(vTextureCoord, vec2(vignetteCenter.x, vignetteCenter.y));" +
                    "lowp float percent = smoothstep(vignetteStart, vignetteEnd, d);" +
                    "gl_FragColor = vec4(mix(rgb.x, 0.0, percent), mix(rgb.y, 0.0, percent), mix(rgb.z, 0.0, percent), 1.0);" +
                    "}";

    private float vignetteCenterX = 0.5f;
    private float vignetteCenterY = 0.5f;
    private float vignetteStart = 0.2f;
    private float vignetteEnd = 0.85f;

    public GlVignetteFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }


    public float getVignetteStart() {
        return vignetteStart;
    }

    public void setVignetteStart(final float vignetteStart) {
        this.vignetteStart = vignetteStart;
    }

    public float getVignetteEnd() {
        return vignetteEnd;
    }

    public void setVignetteEnd(final float vignetteEnd) {
        this.vignetteEnd = vignetteEnd;
    }

    //////////////////////////////////////////////////////////////////////////

    @Override
    public void onDraw() {
        GLES20.glUniform2f(getHandle("vignetteCenter"), vignetteCenterX, vignetteCenterY);
        GLES20.glUniform1f(getHandle("vignetteStart"), vignetteStart);
        GLES20.glUniform1f(getHandle("vignetteEnd"), vignetteEnd);
    }

}

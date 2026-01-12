package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlHazeFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "varying highp vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +
                    "uniform lowp float distance;" +
                    "uniform highp float slope;" +

                    "void main() {" +
                    "highp vec4 color = vec4(1.0);" +

                    "highp float  d = vTextureCoord.y * slope  +  distance;" +

                    "highp vec4 c = texture2D(sTexture, vTextureCoord);" +
                    "c = (c - d * color) / (1.0 -d);" +
                    "gl_FragColor = c;" +    // consider using premultiply(c);
                    "}";

    private float distance = 0.2f;
    private float slope = 0.0f;

    public GlHazeFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(final float distance) {
        this.distance = distance;
    }

    public float getSlope() {
        return slope;
    }

    public void setSlope(final float slope) {
        this.slope = slope;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("distance"), distance);
        GLES20.glUniform1f(getHandle("slope"), slope);
    }
}

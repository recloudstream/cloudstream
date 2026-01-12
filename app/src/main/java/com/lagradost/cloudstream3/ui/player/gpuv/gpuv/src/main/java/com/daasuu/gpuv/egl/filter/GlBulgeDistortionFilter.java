package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;


public class GlBulgeDistortionFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "varying highp vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +

                    "uniform highp vec2 center;" +
                    "uniform highp float radius;" +
                    "uniform highp float scale;" +

                    "void main() {" +
                    "highp vec2 textureCoordinateToUse = vTextureCoord;" +
                    "highp float dist = distance(center, vTextureCoord);" +
                    "textureCoordinateToUse -= center;" +
                    "if (dist < radius) {" +
                    "highp float percent = 1.0 - ((radius - dist) / radius) * scale;" +
                    "percent = percent * percent;" +
                    "textureCoordinateToUse = textureCoordinateToUse * percent;" +
                    "}" +
                    "textureCoordinateToUse += center;" +

                    "gl_FragColor = texture2D(sTexture, textureCoordinateToUse);" +
                    "}";

    private float centerX = 0.5f;
    private float centerY = 0.5f;
    private float radius = 0.25f;
    private float scale = 0.5f;

    public GlBulgeDistortionFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public float getCenterX() {
        return centerX;
    }

    public void setCenterX(final float centerX) {
        this.centerX = centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public void setCenterY(final float centerY) {
        this.centerY = centerY;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(final float radius) {
        this.radius = radius;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(final float scale) {
        this.scale = scale;
    }

    //////////////////////////////////////////////////////////////////////////

    @Override
    public void onDraw() {
        GLES20.glUniform2f(getHandle("center"), centerX, centerY);
        GLES20.glUniform1f(getHandle("radius"), radius);
        GLES20.glUniform1f(getHandle("scale"), scale);
    }
}

package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.graphics.PointF;
import android.opengl.GLES20;

public class GlSwirlFilter extends GlFilter {

    private static final String SWIRL_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            " varying vec2 vTextureCoord;\n" +
            "\n" +
            " uniform lowp sampler2D sTexture;\n" +
            "\n" +
            "uniform highp vec2 center;\n" +
            "uniform highp float radius;\n" +
            "uniform highp float angle;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "highp vec2 textureCoordinateToUse = vTextureCoord;\n" +
            "highp float dist = distance(center, vTextureCoord);\n" +
            "if (dist < radius)\n" +
            "{\n" +
            "textureCoordinateToUse -= center;\n" +
            "highp float percent = (radius - dist) / radius;\n" +
            "highp float theta = percent * percent * angle * 8.0;\n" +
            "highp float s = sin(theta);\n" +
            "highp float c = cos(theta);\n" +
            "textureCoordinateToUse = vec2(dot(textureCoordinateToUse, vec2(c, -s)), dot(textureCoordinateToUse, vec2(s, c)));\n" +
            "textureCoordinateToUse += center;\n" +
            "}\n" +
            "\n" +
            "gl_FragColor = texture2D(sTexture, textureCoordinateToUse );\n" +
            "\n" +
            "}\n";

    public GlSwirlFilter() {
        super(DEFAULT_VERTEX_SHADER, SWIRL_FRAGMENT_SHADER);
    }

    private float angle = 1.0f;
    private float radius = 0.5f;
    private PointF center = new PointF(0.5f, 0.5f);

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public void setCenter(PointF center) {
        this.center = center;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform2f(getHandle("center"), center.x, center.y);
        GLES20.glUniform1f(getHandle("radius"), radius);
        GLES20.glUniform1f(getHandle("angle"), angle);
    }


}

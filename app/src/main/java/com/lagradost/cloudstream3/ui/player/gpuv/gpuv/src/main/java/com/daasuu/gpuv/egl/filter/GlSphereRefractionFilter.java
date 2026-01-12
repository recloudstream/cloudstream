package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.opengl.GLES20;



public class GlSphereRefractionFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "varying vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +
                    "uniform highp vec2 center;" +
                    "uniform highp float radius;" +
                    "uniform highp float aspectRatio;" +
                    "uniform highp float refractiveIndex;" +

                    "void main() {" +
                    "highp vec2 textureCoordinateToUse = vec2(vTextureCoord.x, (vTextureCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));" +
                    "highp float distanceFromCenter = distance(center, textureCoordinateToUse);" +
                    "lowp float checkForPresenceWithinSphere = step(distanceFromCenter, radius);" +

                    "distanceFromCenter = distanceFromCenter / radius;" +

                    "highp float normalizedDepth = radius * sqrt(1.0 - distanceFromCenter * distanceFromCenter);" +
                    "highp vec3 sphereNormal = normalize(vec3(textureCoordinateToUse - center, normalizedDepth));" +

                    "highp vec3 refractedVector = refract(vec3(0.0, 0.0, -1.0), sphereNormal, refractiveIndex);" +

                    "gl_FragColor = texture2D(sTexture, (refractedVector.xy + 1.0) * 0.5) * checkForPresenceWithinSphere;" +
                    "}";

    private float centerX = 0.5f;
    private float centerY = 0.5f;
    private float radius = 0.5f;
    private float aspectRatio = 1.0f;
    private float refractiveIndex = 0.71f;

    public GlSphereRefractionFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public void setCenterX(float centerX) {
        this.centerX = centerX;
    }

    public void setCenterY(float centerY) {
        this.centerY = centerY;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public void setRefractiveIndex(float refractiveIndex) {
        this.refractiveIndex = refractiveIndex;
    }

    //////////////////////////////////////////////////////////////////////////

    @Override
    public void onDraw() {
        GLES20.glUniform2f(getHandle("center"), centerX, centerY);
        GLES20.glUniform1f(getHandle("radius"), radius);
        GLES20.glUniform1f(getHandle("aspectRatio"), aspectRatio);
        GLES20.glUniform1f(getHandle("refractiveIndex"), refractiveIndex);
    }
}

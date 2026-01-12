package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;



public class GlGrayScaleFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "varying vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +
                    "const highp vec3 weight = vec3(0.2125, 0.7154, 0.0721);" +
                    "void main() {" +
                    "float luminance = dot(texture2D(sTexture, vTextureCoord).rgb, weight);" +
                    "gl_FragColor = vec4(vec3(luminance), 1.0);" +
                    "}";

    public GlGrayScaleFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

}

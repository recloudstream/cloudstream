package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;


public class GlSepiaFilter extends GlFilter {
    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "varying vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +
                    "const highp vec3 weight = vec3(0.2125, 0.7154, 0.0721);" +
                    "void main() {" +
                    "   vec4 FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "   gl_FragColor.r = dot(FragColor.rgb, vec3(.393, .769, .189));\n" +
                    "   gl_FragColor.g = dot(FragColor.rgb, vec3(.349, .686, .168));\n" +
                    "   gl_FragColor.b = dot(FragColor.rgb, vec3(.272, .534, .131));\n" +
                    "}";

    public GlSepiaFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }
}

package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

public class GlLuminanceFilter extends GlFilter {

    private static final String LUMINANCE_FRAGMENT_SHADER = "" +
            "precision mediump float;" +
            "\n" +
            " varying vec2 vTextureCoord;\n" +
            "\n" +
            " uniform lowp sampler2D sTexture;\n" +
            "\n" +
            "// Values from \"Graphics Shaders: Theory and Practice\" by Bailey and Cunningham\n" +
            "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
            "    float luminance = dot(textureColor.rgb, W);\n" +
            "    \n" +
            "    gl_FragColor = vec4(vec3(luminance), textureColor.a);\n" +
            "}";

    public GlLuminanceFilter() {
        super(DEFAULT_VERTEX_SHADER, LUMINANCE_FRAGMENT_SHADER);
    }
}

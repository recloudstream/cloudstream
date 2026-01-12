package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;



public class GlCGAColorspaceFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +

                    "varying vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +

                    "void main() {" +
                    "highp vec2 sampleDivisor = vec2(1.0 / 200.0, 1.0 / 320.0);" +

                    "highp vec2 samplePos = vTextureCoord - mod(vTextureCoord, sampleDivisor);" +
                    "highp vec4 color = texture2D(sTexture, samplePos);" +

                    "mediump vec4 colorCyan = vec4(85.0 / 255.0, 1.0, 1.0, 1.0);" +
                    "mediump vec4 colorMagenta = vec4(1.0, 85.0 / 255.0, 1.0, 1.0);" +
                    "mediump vec4 colorWhite = vec4(1.0, 1.0, 1.0, 1.0);" +
                    "mediump vec4 colorBlack = vec4(0.0, 0.0, 0.0, 1.0);" +

                    "mediump vec4 endColor;" +
                    "highp float blackDistance = distance(color, colorBlack);" +
                    "highp float whiteDistance = distance(color, colorWhite);" +
                    "highp float magentaDistance = distance(color, colorMagenta);" +
                    "highp float cyanDistance = distance(color, colorCyan);" +

                    "mediump vec4 finalColor;" +

                    "highp float colorDistance = min(magentaDistance, cyanDistance);" +
                    "colorDistance = min(colorDistance, whiteDistance);" +
                    "colorDistance = min(colorDistance, blackDistance);" +

                    "if (colorDistance == blackDistance) {" +
                    "finalColor = colorBlack;" +
                    "} else if (colorDistance == whiteDistance) {" +
                    "finalColor = colorWhite;" +
                    "} else if (colorDistance == cyanDistance) {" +
                    "finalColor = colorCyan;" +
                    "} else {" +
                    "finalColor = colorMagenta;" +
                    "}" +

                    "gl_FragColor = finalColor;" +
                    "}";


    public GlCGAColorspaceFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

}

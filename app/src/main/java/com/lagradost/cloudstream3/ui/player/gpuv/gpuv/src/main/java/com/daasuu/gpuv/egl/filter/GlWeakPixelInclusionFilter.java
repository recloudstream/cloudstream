package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;



public class GlWeakPixelInclusionFilter extends GlThreex3TextureSamplingFilter {

    private static final String FRAGMENT_SHADER =
            "precision lowp float;\n" +

                    "uniform lowp sampler2D sTexture;\n" +

                    "varying vec2 textureCoordinate;\n" +
                    "varying vec2 leftTextureCoordinate;\n" +
                    "varying vec2 rightTextureCoordinate;\n" +

                    "varying vec2 topTextureCoordinate;\n" +
                    "varying vec2 topLeftTextureCoordinate;\n" +
                    "varying vec2 topRightTextureCoordinate;\n" +

                    "varying vec2 bottomTextureCoordinate;\n" +
                    "varying vec2 bottomLeftTextureCoordinate;\n" +
                    "varying vec2 bottomRightTextureCoordinate;\n" +

                    "void main() {\n" +
                    "float bottomLeftIntensity = texture2D(sTexture, bottomLeftTextureCoordinate).r;" +
                    "float topRightIntensity = texture2D(sTexture, topRightTextureCoordinate).r;" +
                    "float topLeftIntensity = texture2D(sTexture, topLeftTextureCoordinate).r;" +
                    "float bottomRightIntensity = texture2D(sTexture, bottomRightTextureCoordinate).r;" +
                    "float leftIntensity = texture2D(sTexture, leftTextureCoordinate).r;" +
                    "float rightIntensity = texture2D(sTexture, rightTextureCoordinate).r;" +
                    "float bottomIntensity = texture2D(sTexture, bottomTextureCoordinate).r;" +
                    "float topIntensity = texture2D(sTexture, topTextureCoordinate).r;" +
                    "float centerIntensity = texture2D(sTexture, textureCoordinate).r;" +

                    "float pixelIntensitySum = bottomLeftIntensity + topRightIntensity + topLeftIntensity + bottomRightIntensity + leftIntensity + rightIntensity + bottomIntensity + topIntensity + centerIntensity;" +
                    "float sumTest = step(1.5, pixelIntensitySum);" +
                    "float pixelTest = step(0.01, centerIntensity);" +

                    "gl_FragColor = vec4(vec3(sumTest * pixelTest), 1.0);" +
                    "}";

    public GlWeakPixelInclusionFilter() {
        super(FRAGMENT_SHADER);
    }

}

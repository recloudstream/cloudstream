package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.EglUtil;



public class GlLookUpTableFilter extends GlFilter {

    private final static String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "uniform mediump sampler2D lutTexture; \n" +
                    "uniform lowp sampler2D sTexture; \n" +
                    "varying highp vec2 vTextureCoord; \n" +
                    "vec4 sampleAs3DTexture(vec3 uv) {\n" +
                    "    float width = 16.;\n" +
                    "    float sliceSize = 1.0 / width;\n" +
                    "    float slicePixelSize = sliceSize / width;\n" +
                    "    float sliceInnerSize = slicePixelSize * (width - 1.0);\n" +
                    "    float zSlice0 = min(floor(uv.z * width), width - 1.0);\n" +
                    "    float zSlice1 = min(zSlice0 + 1.0, width - 1.0);\n" +
                    "    float xOffset = slicePixelSize * 0.5 + uv.x * sliceInnerSize;\n" +
                    "    float s0 = xOffset + (zSlice0 * sliceSize);\n" +
                    "    float s1 = xOffset + (zSlice1 * sliceSize);\n" +
                    "    vec4 slice0Color = texture2D(lutTexture, vec2(s0, uv.y));\n" +
                    "    vec4 slice1Color = texture2D(lutTexture, vec2(s1, uv.y));\n" +
                    "    float zOffset = mod(uv.z * width, 1.0);\n" +
                    "    vec4 result = mix(slice0Color, slice1Color, zOffset);\n" +
                    "    return result;\n" +
                    "}\n" +
                    "void main() {\n" +
                    "   vec4 pixel = texture2D(sTexture, vTextureCoord);\n" +
                    "   vec4 gradedPixel = sampleAs3DTexture(pixel.rgb);\n" +
                    "   gradedPixel.a = pixel.a;\n" +
                    "   pixel = gradedPixel;\n" +
                    "   gl_FragColor = pixel;\n " +
                    "}";

    public GlLookUpTableFilter(Bitmap bitmap) {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
        this.lutTexture = bitmap;
        hTex = EglUtil.NO_TEXTURE;
    }


    public GlLookUpTableFilter(Resources resources, int fxID) {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
        this.lutTexture = BitmapFactory.decodeResource(resources, fxID);
        hTex = EglUtil.NO_TEXTURE;
    }

    private int hTex;

    private Bitmap lutTexture;

    @Override
    public void onDraw() {
        int offsetDepthMapTextureUniform = getHandle("lutTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hTex);
        GLES20.glUniform1i(offsetDepthMapTextureUniform, 3);
    }

    @Override
    public void setup() {
        super.setup();
        loadTexture();
    }

    private void loadTexture() {
        if (hTex == EglUtil.NO_TEXTURE) {
            hTex = EglUtil.loadTexture(lutTexture, EglUtil.NO_TEXTURE, false);
        }
    }

    public void releaseLutBitmap() {
        if (lutTexture != null && !lutTexture.isRecycled()) {
            lutTexture.recycle();
            lutTexture = null;
        }
    }

    public void reset() {
        hTex = EglUtil.NO_TEXTURE;
        hTex = EglUtil.loadTexture(lutTexture, EglUtil.NO_TEXTURE, false);
    }
}

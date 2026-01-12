package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl;

import android.graphics.SurfaceTexture;



public class GlSurfaceTexture implements SurfaceTexture.OnFrameAvailableListener {

    public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private SurfaceTexture surfaceTexture;
    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener;

    public GlSurfaceTexture(final int texName) {
        surfaceTexture = new SurfaceTexture(texName);
        surfaceTexture.setOnFrameAvailableListener(this);
    }


    public void setOnFrameAvailableListener(final SurfaceTexture.OnFrameAvailableListener l) {
        onFrameAvailableListener = l;
    }


    public int getTextureTarget() {
        return GL_TEXTURE_EXTERNAL_OES;
    }

    public void updateTexImage() {
        surfaceTexture.updateTexImage();
    }

    public void getTransformMatrix(final float[] mtx) {
        surfaceTexture.getTransformMatrix(mtx);
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
        if (onFrameAvailableListener != null) {
            onFrameAvailableListener.onFrameAvailable(this.surfaceTexture);
        }
    }

    public void release() {
        surfaceTexture.release();
    }
}


package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl;



public class GlConfigChooser extends DefaultConfigChooser {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 2;

    public GlConfigChooser(final boolean withDepthBuffer) {
        super(withDepthBuffer, EGL_CONTEXT_CLIENT_VERSION);
    }

}

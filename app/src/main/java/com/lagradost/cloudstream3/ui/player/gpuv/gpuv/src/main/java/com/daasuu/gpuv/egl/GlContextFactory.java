package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl;



public class GlContextFactory extends DefaultContextFactory {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 2;

    public GlContextFactory() {
        super(EGL_CONTEXT_CLIENT_VERSION);
    }

}

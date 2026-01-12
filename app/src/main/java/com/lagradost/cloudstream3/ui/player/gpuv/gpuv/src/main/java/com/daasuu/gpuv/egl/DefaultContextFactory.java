package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl;

import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;



public class DefaultContextFactory implements GLSurfaceView.EGLContextFactory {

    private static final String TAG = "DefaultContextFactory";

    private int EGLContextClientVersion;

    public DefaultContextFactory(final int version) {
        EGLContextClientVersion = version;
    }

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    @Override
    public EGLContext createContext(final EGL10 egl, final EGLDisplay display, final EGLConfig config) {
        final int[] attrib_list;
        if (EGLContextClientVersion != 0) {
            attrib_list = new int[]{EGL_CONTEXT_CLIENT_VERSION, EGLContextClientVersion, EGL_NONE};
        } else {
            attrib_list = null;
        }
        return egl.eglCreateContext(display, config, EGL_NO_CONTEXT, attrib_list);
    }

    @Override
    public void destroyContext(final EGL10 egl, final EGLDisplay display, final EGLContext context) {
        if (!egl.eglDestroyContext(display, context)) {
            Log.e(TAG, "display:" + display + " context: " + context);
            throw new RuntimeException("eglDestroyContext" + egl.eglGetError());
        }
    }

}

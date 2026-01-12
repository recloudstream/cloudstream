package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl;

import android.opengl.GLES20;

import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_DEPTH_ATTACHMENT;
import static android.opengl.GLES20.GL_DEPTH_COMPONENT16;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.GL_FRAMEBUFFER_COMPLETE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MAX_RENDERBUFFER_SIZE;
import static android.opengl.GLES20.GL_MAX_TEXTURE_SIZE;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_RENDERBUFFER;
import static android.opengl.GLES20.GL_RENDERBUFFER_BINDING;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_BINDING_2D;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;



public class GlFramebufferObject {
    private int width;
    private int height;
    private int framebufferName;
    private int renderBufferName;
    private int texName;

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTexName() {
        return texName;
    }

    public void setup(final int width, final int height) {
        final int[] args = new int[1];

        GLES20.glGetIntegerv(GL_MAX_TEXTURE_SIZE, args, 0);
        if (width > args[0] || height > args[0]) {
            throw new IllegalArgumentException("GL_MAX_TEXTURE_SIZE " + args[0]);
        }

        GLES20.glGetIntegerv(GL_MAX_RENDERBUFFER_SIZE, args, 0);
        if (width > args[0] || height > args[0]) {
            throw new IllegalArgumentException("GL_MAX_RENDERBUFFER_SIZE " + args[0]);
        }

        GLES20.glGetIntegerv(GL_FRAMEBUFFER_BINDING, args, 0);
        final int saveFramebuffer = args[0];
        GLES20.glGetIntegerv(GL_RENDERBUFFER_BINDING, args, 0);
        final int saveRenderbuffer = args[0];
        GLES20.glGetIntegerv(GL_TEXTURE_BINDING_2D, args, 0);
        final int saveTexName = args[0];

        release();

        try {
            this.width = width;
            this.height = height;

            GLES20.glGenFramebuffers(args.length, args, 0);
            framebufferName = args[0];
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, framebufferName);

            GLES20.glGenRenderbuffers(args.length, args, 0);
            renderBufferName = args[0];
            GLES20.glBindRenderbuffer(GL_RENDERBUFFER, renderBufferName);
            GLES20.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
            GLES20.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderBufferName);

            GLES20.glGenTextures(args.length, args, 0);
            texName = args[0];
            GLES20.glBindTexture(GL_TEXTURE_2D, texName);

            EglUtil.setupSampler(GL_TEXTURE_2D, GL_LINEAR, GL_NEAREST);

            GLES20.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            GLES20.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texName, 0);

            final int status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Failed to initialize framebuffer object " + status);
            }
        } catch (final RuntimeException e) {
            release();
            throw e;
        }

        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, saveFramebuffer);
        GLES20.glBindRenderbuffer(GL_RENDERBUFFER, saveRenderbuffer);
        GLES20.glBindTexture(GL_TEXTURE_2D, saveTexName);
    }

    public void release() {
        final int[] args = new int[1];
        args[0] = texName;
        GLES20.glDeleteTextures(args.length, args, 0);
        texName = 0;
        args[0] = renderBufferName;
        GLES20.glDeleteRenderbuffers(args.length, args, 0);
        renderBufferName = 0;
        args[0] = framebufferName;
        GLES20.glDeleteFramebuffers(args.length, args, 0);
        framebufferName = 0;
    }

    public void enable() {
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, framebufferName);
    }


}

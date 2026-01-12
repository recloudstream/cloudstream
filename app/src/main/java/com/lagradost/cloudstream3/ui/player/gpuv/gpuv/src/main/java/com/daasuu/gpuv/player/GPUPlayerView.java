package com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.player;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import com.lagradost.cloudstream3.ui.player.PlayerResize;
import com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.GlConfigChooser;
import com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.GlContextFactory;
import com.lagradost.cloudstream3.ui.player.gpuv.gpuv.src.main.java.com.daasuu.gpuv.egl.filter.GlFilter;

public class GPUPlayerView extends GLSurfaceView implements Player.Listener {

    private final static String TAG = GPUPlayerView.class.getSimpleName();

    private final GPUPlayerRenderer renderer;
    private ExoPlayer player;

    private float videoAspect = 1f;
    private PlayerResize playerScaleType = PlayerResize.Fit;

    public GPUPlayerView(Context context) {
        this(context, null);
    }

    public GPUPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setEGLContextFactory(new GlContextFactory());
        setEGLConfigChooser(new GlConfigChooser(false));

        renderer = new GPUPlayerRenderer(this);
        setRenderer(renderer);

    }

    public GPUPlayerView setExoPlayer(ExoPlayer player) {
        if (this.player != null) {
            this.player.release();
            this.player = null;
        }
        this.player = player;
        this.player.addListener(this);
        this.renderer.setExoPlayer(player);
        return this;
    }

    public void setGlFilter(GlFilter glFilter) {
        renderer.setGlFilter(glFilter);
    }

    public void setPlayerScaleType(PlayerResize playerScaleType) {
        this.playerScaleType = playerScaleType;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Compute measured width/height from MeasureSpecs respecting their modes.
        final int widthMode = android.view.View.MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = android.view.View.MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = android.view.View.MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = android.view.View.MeasureSpec.getSize(heightMeasureSpec);

        int measuredWidth = widthSize;
        int measuredHeight = heightSize;

        if (widthMode != android.view.View.MeasureSpec.EXACTLY || heightMode != android.view.View.MeasureSpec.EXACTLY) {
            // Fallback to defaults if parent didn't impose exact sizes
            measuredWidth = getSuggestedMinimumWidth();
            measuredHeight = getSuggestedMinimumHeight();
            if (measuredWidth <= 0) measuredWidth = widthSize > 0 ? widthSize : 1;
            if (measuredHeight <= 0) measuredHeight = heightSize > 0 ? heightSize : 1;
        }

        float measuredAspectRatio = measuredHeight == 0 ? 1f : (float) measuredWidth / measuredHeight;

        int viewWidth = measuredWidth;
        int viewHeight = measuredHeight;

        switch (playerScaleType) {
            case Fit:
                // Follows aspect ratio of the video and fits video within view, so no parts are cut off thus black bars may be visible
                if (measuredAspectRatio > videoAspect) {
                    // view is wider than video
                    viewWidth = (int) (measuredHeight * videoAspect);
                } else {
                    // view is taller than video
                    viewHeight = (int) (measuredWidth / videoAspect);
                }
                break;
            case Fill:
                // Doesn't follow aspect ratio of the video and stretches video's width and height to match view's width and height so no black bars are visible and no parts are cut off
                break;
            case Zoom:
                // Follows aspect ratio of the video and zooms in so that no black bars are visible but parts of the video may be cut off
                if (measuredAspectRatio > videoAspect) {
                    // view is wider than video
                    viewHeight = (int) (measuredWidth / videoAspect);
                } else {
                    // view is taller than video
                    viewWidth = (int) (measuredHeight * videoAspect);
                }
                break;
        }

       // Log.i(TAG, "onMeasure: scale: " + playerScaleType.name() + " videoAspect: " + videoAspect + " measuredAspectRatio: " + measuredAspectRatio + " viewWidth: " + viewWidth + " viewHeight: " + viewHeight + " measuredWidth: " + measuredWidth + " measuredHeight: " + measuredHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }

    @Override
    public void onPause() {
        super.onPause();
        renderer.release();
    }

    /// ///////////////////////////////////////////////////////////////////////
    // Player.Listener
    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        // Log.d(TAG, "width = " + width + " height = " + height + " unappliedRotationDegrees = " + unappliedRotationDegrees + " pixelWidthHeightRatio = " + pixelWidthHeightRatio);
        videoAspect = ((float) videoSize.width / videoSize.height) * videoSize.pixelWidthHeightRatio;
        // Log.d(TAG, "videoAspect = " + videoAspect);
        requestLayout();
    }

    @Override
    public void onRenderedFirstFrame() {
        // do nothing
    }
}


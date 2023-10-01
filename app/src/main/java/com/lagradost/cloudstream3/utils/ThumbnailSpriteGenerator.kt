package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import java.io.File

internal interface ThumbnailSpriteCallback {
	fun onThumbnailSpriteGenerated(spriteBitmap: Bitmap)
	fun onThumbnailSpriteGenerationError(error: Exception)
}

internal class ThumbnailSpriteGenerator(
	private val videoPath: String,
	private val callback: ThumbnailSpriteCallback
) {

	private val maxLines: Int = 10
	private val maxColumns: Int = 10

	private val minFrameIntervalSeconds: Int = 10

	// By setting this here we can use setDataSource once
	// This will improve performance, in particular for online videos
	// It means that we don't have to seek through the video multiple times, only once
	private val retriever: MediaMetadataRetriever = MediaMetadataRetriever()

	internal fun generateThumbnailSprite() {
		retriever.setDataSource(videoPath)

		val videoDuration = getVideoDuration()

		if (videoDuration <= 0) {
			callback.onThumbnailSpriteGenerationError(Exception("Invalid video duration"))
			return
		}

		val videoDimensions = getVideoDimensions()
		
		if (videoDimensions == null) {
			callback.onThumbnailSpriteGenerationError(Exception("Invalid video dimensions"))
			return
		}

		val minFrameIntervalMillis: Long = (minFrameIntervalSeconds * 1000).toLong()
		val maxFrameIntervalMillis: Long = videoDuration / (maxLines * maxColumns)
		val frameIntervalMillis: Long = if (minFrameIntervalMillis > maxFrameIntervalMillis) {
			minFrameIntervalMillis
		} else {
			maxFrameIntervalMillis
		}

		val videoWidth = videoDimensions.first
		val videoHeight = videoDimensions.second

		val thumbnailWidth = videoWidth / maxColumns
		val thumbnailHeight = videoHeight / maxLines

		val thumbnailList = ArrayList<Bitmap>()
		for (timeInMillis in 0 until videoDuration step frameIntervalMillis) {
			val thumbnail = generateThumbnail(timeInMillis, thumbnailWidth, thumbnailHeight)
			if (thumbnail != null) {
				thumbnailList.add(thumbnail)
			}
		}

		try {
			val spriteBitmap = createSpriteBitmap(thumbnailList)
			callback.onThumbnailSpriteGenerated(spriteBitmap)

			// Release the MediaMetadataRetriever
			retriever.release()
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)

			// Release the MediaMetadataRetriever
			retriever.release()
		}
	}

	private fun generateThumbnail(timeInMillis: Long, thumbnailWidth: Int, thumbnailHeight: Int): Bitmap? {
		return try {
			// Calculate the frame time based on timeInMillis
			val frameTimeMicros = timeInMillis * 1000

			// Retrieve the frame at the specified time
			val frameBitmap = retriever.getFrameAtTime(frameTimeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

			if (frameBitmap != null) {
				// Calculate aspect ratio
				val aspectRatio = frameBitmap.width.toFloat() / frameBitmap.height.toFloat()

				// Calculate new width or height based on aspect ratio
				val newWidth: Int
				val newHeight: Int

				if (thumbnailWidth / aspectRatio <= thumbnailHeight) {
					// Fit within the specified width
					newWidth = thumbnailWidth
					newHeight = (thumbnailWidth / aspectRatio).toInt()
				} else {
					// Fit within the specified height
					newWidth = (thumbnailHeight * aspectRatio).toInt()
					newHeight = thumbnailHeight
				}

				val scaledBitmap = Bitmap.createScaledBitmap(frameBitmap, newWidth, newHeight, false)

				scaledBitmap
			} else {
				null
			}
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			null
		}
	}

	private fun createSpriteBitmap(thumbnails: List<Bitmap>): Bitmap {
		val spriteWidth = thumbnails[0].width * maxColumns
		val spriteHeight = thumbnails[0].height * maxLines
		val sprite = Bitmap.createBitmap(spriteWidth, spriteHeight, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(sprite)

		for (i in thumbnails.indices) {
			val x = i % maxColumns * thumbnails[0].width
			val y = i / maxColumns * thumbnails[0].height
			canvas.drawBitmap(thumbnails[i], x.toFloat(), y.toFloat(), null)
		}

		return sprite
	}

	private fun getVideoDuration(): Long {
		return try {
			val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

			durationString?.toLong() ?: 0
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			0
		}
	}

	private fun getVideoDimensions(): Pair<Int, Int>? {
		return try {
			val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
			val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()

			if (width != null && height != null) {
				Pair(width, height)
			} else {
				null
			}
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			null
		}
	}
}

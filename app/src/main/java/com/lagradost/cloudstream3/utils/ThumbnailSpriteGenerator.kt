package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import java.io.File
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode

internal interface ThumbnailSpriteCallback {
	fun onThumbnailSpriteGenerated(spriteBitmap: Bitmap)
	fun onThumbnailSpriteGenerationError(error: Exception)
}

internal class ThumbnailSpriteGenerator(
	private val videoPath: String,
	private val callback: ThumbnailSpriteCallback
) {

	private val maxLines = 10
	private val maxColumns = 10

	// By setting this here we can use setDataSource once
	// This will improve performance, in particular for online videos
	// It means that we don't have to seek through the video multiple times, only once
	private val retriever = MediaMetadataRetriever()

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

		val frameIntervalMillis: Long = videoDuration / (maxLines * maxColumns)

		val videoWidth = videoDimensions.first
		val videoHeight = videoDimensions.second

		val thumbnailWidth = videoWidth / maxColumns
		val thumbnailHeight = videoHeight / maxLines

		val thumbnailList: ArrayList<Bitmap>

		if (videoPath.contains(".m3u8")) {
			// Using FFmpeg is only really needed for files like M3u8 files
			// MediaMetadataRetriever can handle most other things
			// If we don't want to support them, then this could really probably be removed
			thumbnailList = generateThumbnailsFFmpeg(thumbnailWidth, thumbnailHeight, frameIntervalMillis)
		} else {
			thumbnailList = ArrayList<Bitmap>()
			for (timeInMillis in 0 until videoDuration step frameIntervalMillis) {
				val thumbnail = generateThumbnail(timeInMillis, thumbnailWidth, thumbnailHeight)
				if (thumbnail != null) {
					thumbnailList.add(thumbnail)
				}
			}
		}

		try {
			val spriteBitmap = createSpriteBitmap(thumbnailList)
			callback.onThumbnailSpriteGenerated(spriteBitmap)
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)

			// Release the MediaMetadataRetriever
			retriever.release()
		}

		// Release the MediaMetadataRetriever
		retriever.release()
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
	
	private fun generateThumbnailsFFmpeg(thumbnailWidth: Int, thumbnailHeight: Int, frameIntervalMillis: Long): ArrayList<Bitmap> {
		return try {
			// Use FFmpeg to generate thumbnail sprites for online videos
			val frameIntervalSeconds = (frameIntervalMillis / 1000.0).toDouble()

			val context: Context = MainActivity.instance.applicationContext
			val cacheDir = context.cacheDir
			val outputFilePathPattern = File(cacheDir, "thumbnail%d.jpg").absolutePath

			val ffmpegCommand: Array<String> = arrayOf(
				"-i", videoPath,
				"fps=1/$frameIntervalSeconds,scale=$thumbnailWidth:$thumbnailHeight",
				"-preset", "ultrafast",
				"-an",
				"-y",
				outputFilePathPattern
			)

			val session = FFmpegKit.executeWithArguments(ffmpegCommand)
			val returnCode: ReturnCode = session.returnCode

			if (ReturnCode.isSuccess(returnCode)) {
				// Read the image into a Bitmap
				// Determine the number of generated frames dynamically
				var frameNumber = 1

				val generatedThumbnails = ArrayList<Bitmap>()

				while (true) {
					val filePath = outputFilePathPattern.replace("%d", frameNumber.toString())
					
					val thumbnail: Bitmap?
					if (File(filePath).exists()) {
						thumbnail = BitmapFactory.decodeFile(filePath)

						// Clean up: Delete the temporary file
						File(filePath).delete()
					} else {
						thumbnail = null
					}

					if (thumbnail != null) {
						generatedThumbnails.add(thumbnail)
						
						val spriteBitmap = createSpriteBitmap(generatedThumbnails)
						callback.onThumbnailSpriteGenerated(spriteBitmap)
						frameNumber++
					} else {
						// No more frames found, exit the loop
						break
					}
				}

				generatedThumbnails
			} else {
				val errorMessage = "FFmpeg execution failed with return code: $returnCode, ${session.getOutput()}"
				callback.onThumbnailSpriteGenerationError(Exception(errorMessage))
				ArrayList<Bitmap>()
			}
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			ArrayList<Bitmap>()
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
			if (videoPath.contains(".m3u8")) {
				getVideoDurationFFprobe()
			} else {
				val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

				durationString?.toLong() ?: 0
			}
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			0
		}
	}

	private fun getVideoDurationFFprobe(): Long {
		val session = FFprobeKit.getMediaInformation(videoPath)
		val mediaInformation = session.mediaInformation
		val returnCode: ReturnCode = session.returnCode

		return if (ReturnCode.isSuccess(returnCode) && mediaInformation.duration != null) {
			(mediaInformation.duration.toDouble() * 1000.0).toLong()
		} else {
			val errorMessage = "FFprobe execution failed with return code: $returnCode, ${session.getOutput()}"
			callback.onThumbnailSpriteGenerationError(Exception(errorMessage))
			0
		}
	}

	private fun getVideoDimensions(): Pair<Int, Int>? {
		return try {
			if (videoPath.contains(".m3u8")) {
				getVideoDimensionsFFprobe()
			} else {
				val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
				val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()

				if (width != null && height != null) {
					Pair(width, height)
				} else {
					null
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			callback.onThumbnailSpriteGenerationError(e)
			null
		}
    }

	private fun getVideoDimensionsFFprobe(): Pair<Int, Int>? {
		val session = FFprobeKit.getMediaInformation(videoPath)
		val mediaInformation = session.mediaInformation
		val returnCode: ReturnCode = session.returnCode

		return if (ReturnCode.isSuccess(returnCode) && mediaInformation.streams.isNotEmpty()) {
			val videoStream = mediaInformation.streams.find { it.width != null && it.height != null }
			if (videoStream != null) {
				val width = videoStream.width
				val height = videoStream.height

				if (width != null && height != null) {
					Pair(width.toInt(), height.toInt())
				} else {
					null
				}
			} else {
				null
			}
		} else {
			val errorMessage = "FFprobe execution failed with return code: $returnCode, ${session.getOutput()}"
			callback.onThumbnailSpriteGenerationError(Exception(errorMessage))
			null
		}
	}
}

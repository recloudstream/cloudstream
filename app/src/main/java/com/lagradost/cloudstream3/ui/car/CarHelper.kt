package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Pane
import androidx.car.app.model.Row
import android.text.SpannableString
import android.text.Spanned
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.FavoritesData

object CarHelper {
    
    fun toggleFavorite(
        carContext: CarContext,
        fullDetails: LoadResponse?,
        isFavorite: Boolean,
        onFavoriteChanged: (Boolean) -> Unit
    ) {
        val details = fullDetails ?: return
        val api = getApiFromNameNull(details.apiName) ?: return
        val id = details.url.replace(api.mainUrl, "").replace("/", "").hashCode()

        if (isFavorite) {
            DataStoreHelper.removeFavoritesData(id)
            onFavoriteChanged(false)
            androidx.car.app.CarToast.makeText(carContext, CarStrings.get(R.string.car_removed_from_favorites), androidx.car.app.CarToast.LENGTH_SHORT).show()
        } else {
            val favoritesData = FavoritesData(
                favoritesTime = System.currentTimeMillis(),
                id = id,
                latestUpdatedTime = System.currentTimeMillis(),
                name = details.name,
                url = details.url,
                apiName = details.apiName,
                type = details.type,
                posterUrl = details.posterUrl,
                year = details.year,
                quality = null,
                posterHeaders = details.posterHeaders,
                plot = details.plot,
                score = details.score,
                tags = details.tags
            )
            DataStoreHelper.setFavoritesData(id, favoritesData)
            onFavoriteChanged(true)
            androidx.car.app.CarToast.makeText(carContext, CarStrings.get(R.string.car_added_to_favorites), androidx.car.app.CarToast.LENGTH_SHORT).show()
        }
    }

    fun addPlotAndCast(paneBuilder: Pane.Builder, details: LoadResponse) {
        // Plot Row
        if (!details.plot.isNullOrEmpty()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(CarStrings.get(R.string.car_plot))
                    .addText(details.plot!!)
                    .build()
            )
        }

        // Cast Row
        if (!details.actors.isNullOrEmpty()) {
            val castList = details.actors!!.groupBy { it.roleString }.flatMap { it.value }.take(5).joinToString(", ") { it.actor.name }
            if (castList.isNotEmpty()) {
                val s = SpannableString(castList)
                s.setSpan(
                    ForegroundCarColorSpan.create(CarColor.SECONDARY),
                    0,
                    s.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle(CarStrings.get(R.string.car_cast))
                        .addText(s)
                        .build()
                )
            }
        }
    }

    fun ensureSoftwareBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        return if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
            bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    fun generateSquareImageWithLogo(poster: android.graphics.Bitmap, logo: android.graphics.Bitmap?): android.graphics.Bitmap {
        return try {
            val dimension = minOf(poster.width, poster.height)
            val squareBitmap = android.graphics.Bitmap.createBitmap(dimension, dimension, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(squareBitmap)

            // 1. Draw Poster (Center Crop)
            val sourceRect = if (poster.width > poster.height) {
                // Wide image: crop center horizontally
                val left = (poster.width - poster.height) / 2
                android.graphics.Rect(left, 0, left + poster.height, poster.height)
            } else {
                // Tall image: crop center vertically
                val top = (poster.height - poster.width) / 2
                android.graphics.Rect(0, top, poster.width, top + poster.width)
            }
            val destRect = android.graphics.Rect(0, 0, dimension, dimension)
            canvas.drawBitmap(poster, sourceRect, destRect, null)

            // 2. Draw Gradient (Bottom Dark Shade)
            val gradientPaint = android.graphics.Paint()
            val gradient = android.graphics.LinearGradient(
                0f, dimension * 0.4f,  // Start transparent higher up
                0f, dimension.toFloat(),
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.parseColor("#E6000000"), // 90% Black
                android.graphics.Shader.TileMode.CLAMP
            )
            gradientPaint.shader = gradient
            canvas.drawRect(0f, dimension * 0.4f, dimension.toFloat(), dimension.toFloat(), gradientPaint)

            // 3. Draw Logo (if available) safely at bottom center
            if (logo != null) {
                val logoWidth = logo.width
                val logoHeight = logo.height
                
                // Max width 60% of square, Max height 25% of square
                val maxWidth = dimension * 0.6f
                val maxHeight = dimension * 0.25f

                val scaleX = maxWidth / logoWidth
                val scaleY = maxHeight / logoHeight
                val scale = minOf(scaleX, scaleY, 1.0f) // Don't upscale, only downscale

                val newWidth = (logoWidth * scale).toInt()
                val newHeight = (logoHeight * scale).toInt()

                val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logo, newWidth, newHeight, true)
                
                // Position: Bottom Center with margin
                val bottomMargin = dimension * 0.05f // 5% margin
                val left = (dimension - scaledLogo.width) / 2f
                val top = dimension - scaledLogo.height - bottomMargin
                
                // --- DROP SHADOW EFFECT ---
                // Draw a black, semi-transparent silhouette slightly offset to make the white text pop
                val shadowPaint = android.graphics.Paint()
                // Tint the bitmap BLACK using SRC_IN (keeps alpha channel but changes color)
                shadowPaint.colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
                shadowPaint.alpha = 160 // 0-255 transparency (approx 60% opacity)
                
                val shadowOffset = dimension * 0.005f // Dynamic offset based on size (approx 3-5px)
                canvas.drawBitmap(scaledLogo, left + shadowOffset, top + shadowOffset, shadowPaint)
                // --------------------------

                // Draw Original Logo
                canvas.drawBitmap(scaledLogo, left, top, null)
            }

            squareBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            poster // Fallback to original poster if combined generation fails
        }
    }
}

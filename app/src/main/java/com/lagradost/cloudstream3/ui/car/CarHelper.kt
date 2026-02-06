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
}

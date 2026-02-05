package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Precision
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CategoryScreen(
    carContext: CarContext,
    private val homePageList: HomePageList
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val iconCache = mutableMapOf<String, CarIcon>()
    private val loadingUrls = mutableSetOf<String>()

    private fun loadIcon(imageUrl: String) {
        if (loadingUrls.contains(imageUrl)) return
        loadingUrls.add(imageUrl)

        scope.launch {
             try {
                 val request = ImageRequest.Builder(carContext)
                    .data(imageUrl)
                    .size(256, 256)
                    .build()
                 val result = SingletonImageLoader.get(carContext).execute(request)
                 val bitmap = result.image?.asDrawable(carContext.resources)?.toBitmap()
                 if (bitmap != null) {
                     val icon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                     synchronized(iconCache) {
                        iconCache[imageUrl] = icon
                     }
                     invalidate()
                 }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingUrls.remove(imageUrl)
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        homePageList.list.forEach { item ->
            val imageUrl = item.posterUrl
            
            // Check cache or trigger load
            val icon = if (!imageUrl.isNullOrEmpty()) {
                synchronized(iconCache) {
                    iconCache[imageUrl]
                } ?: run {
                    loadIcon(imageUrl)
                    CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()
                }
            } else {
                 CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)).build()
            }

            // Use Row with LARGE image for maximum visibility in a list
            val row = Row.Builder()
                .setTitle(if (item.name.isNullOrEmpty()) "Untitled" else item.name)
                .setImage(icon, Row.IMAGE_TYPE_LARGE)
                .setOnClickListener {
                     val type = item.type
                     if (type == com.lagradost.cloudstream3.TvType.TvSeries ||
                         type == com.lagradost.cloudstream3.TvType.Anime ||
                         type == com.lagradost.cloudstream3.TvType.Cartoon ||
                         type == com.lagradost.cloudstream3.TvType.OVA ||
                         type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                         type == com.lagradost.cloudstream3.TvType.Documentary) {
                         screenManager.push(TvSeriesDetailScreen(carContext, item))
                     } else if (type == com.lagradost.cloudstream3.TvType.Live) {
                         screenManager.push(PlayerCarScreen(carContext, item = item))
                     } else {
                         screenManager.push(DetailsScreen(carContext, item))
                     }
                }
                .build()
            listBuilder.addItem(row)
        }

        return ListTemplate.Builder()
            .setTitle(homePageList.name)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

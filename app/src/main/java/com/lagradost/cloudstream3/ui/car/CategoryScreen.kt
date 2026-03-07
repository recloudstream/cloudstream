package com.lagradost.cloudstream3.ui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.OnClickListener
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
import com.lagradost.cloudstream3.utils.DataStoreHelper
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
        val currentViewMode = DataStoreHelper.carCategoryViewMode // 0 = List, 1 = Grid

        val actionStrip = buildActionStrip(currentViewMode)

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

            val onClick = OnClickListener {
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

            if (currentViewMode == 0) {
                // Use Row with LARGE image for maximum visibility in a list
                val row = Row.Builder()
                    .setTitle(if (item.name.isNullOrEmpty()) "Untitled" else item.name)
                    .setImage(icon, Row.IMAGE_TYPE_LARGE)
                    .setOnClickListener(onClick)
                    .build()
                listBuilder.addItem(row)
            } else {
                val gridItem = GridItem.Builder()
                    .setTitle(if (item.name.isNullOrEmpty()) "Untitled" else item.name)
                    .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener(onClick)
                    .build()
                listBuilder.addItem(gridItem)
            }
        }

        return if (currentViewMode == 0) {
            ListTemplate.Builder()
                .setTitle(homePageList.name)
                .setHeaderAction(Action.BACK)
                .setSingleList(listBuilder.build())
                .setActionStrip(actionStrip)
                .build()
        } else {
            GridTemplate.Builder()
                .setTitle(homePageList.name)
                .setHeaderAction(Action.BACK)
                .setSingleList(listBuilder.build())
                .setActionStrip(actionStrip)
                .build()
        }
    }

    private fun buildActionStrip(currentViewMode: Int): ActionStrip {
        return ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                if (currentViewMode == 0) R.drawable.baseline_grid_view_24 else R.drawable.baseline_list_alt_24
                            )
                        ).build()
                    )
                    .setOnClickListener {
                        DataStoreHelper.carCategoryViewMode = if (currentViewMode == 0) 1 else 0
                        invalidate()
                    }
                    .build()
            )
            .build()
    }
}

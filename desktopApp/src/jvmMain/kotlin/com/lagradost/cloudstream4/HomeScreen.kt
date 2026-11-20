package com.lagradost.cloudstream4

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream4.compose.ImmutableHomePageList
import com.lagradost.cloudstream4.compose.ImmutableSearchResponse
import com.lagradost.cloudstream4.compose.Screen
import com.lagradost.cloudstream4.compose.SearchResponseAction
import com.lagradost.cloudstream4.compose.SearchResponseDialog
import com.lagradost.cloudstream4.compose.SearchResponseItem
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_forward
import com.lagradost.cloudstream4.viewmodels.HomeAction
import com.lagradost.cloudstream4.viewmodels.HomeRepository
import com.lagradost.cloudstream4.viewmodels.HomeState
import com.lagradost.cloudstream4.viewmodels.HomeViewModel
import com.lagradost.cloudstream4.viewmodels.ImmutableAPI
import com.mihon.material.padding
import org.jetbrains.compose.resources.painterResource

object TestPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(object : TmdbProvider() {
            override var name = "TMDB"
        })
    }
}

object NoWaitHomeRepository : HomeRepository {
    override suspend fun getApi(name: String): MainAPI? =
        getApiFromNameNull(name)

    override suspend fun getAllResumeWatching(): ArrayList<ImmutableSearchResponse> = arrayListOf()

    override suspend fun getAllBookmarks(): Map<Int, ArrayList<ImmutableSearchResponse>> = mapOf()
    override fun getAllApis(database: Database): List<ImmutableAPI> = emptyList()
}

object HomeScreen : Screen {
    @Composable
    override fun Content() {
        val db = rememberDatabase()
        val viewModel = viewModel<HomeViewModel> { HomeViewModel(NoWaitHomeRepository, db = db) }
        /** Testing */
        LaunchedEffect(Unit) {
            TestPlugin.load()
            viewModel.onAction(HomeAction.SelectApi("TMDB"))
        }

        val state by viewModel.state.collectAsState()
        Content(state, viewModel::onAction)
    }

    @Composable
    fun Content(state: HomeState, action: (HomeAction) -> Unit) {
        if (state.isLoading) {
            Text("Loading")
        } else if (state.error != null) {
            Text("Error: ${state.error}")
        } else {
            //Text("Loaded: ${state.rows.size}")
        }
        val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
            { item ->
                action(HomeAction.ResultAction(item))
            }
        }

        val shownRow = state.rows[state.rowDialog]
        if (shownRow != null) {
            SearchResponseDialog(dialog = shownRow, action = searchAction, dismiss = {
                action(HomeAction.DismissDialog)
            })
        }

        LazyColumn {
            items(items = state.rows.sorted, key = { name -> name }) { name ->
                SearchRow(
                    state.rows[name] ?: throw Error("This should never happen"),
                    action = action
                )
            }
        }
    }

    @Composable
    fun SearchRow(
        row: ImmutableHomePageList,
        action: (HomeAction) -> Unit
    ) {
        val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
            { item ->
                action(HomeAction.ResultAction(item))
            }
        }

        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(onClick = {
                        action(HomeAction.ShowRowDialog(rowName = row.name))
                    })
            ) {
                Text(
                    row.name,
                    modifier = Modifier.padding(MaterialTheme.padding.small),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    modifier = Modifier.padding(MaterialTheme.padding.small),
                    painter = painterResource(Res.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small)
            ) {
                items(items = row.items, key = { item -> item.uuid }) { response ->
                    SearchResponseItem(
                        response = response,
                        action = searchAction,
                        modifier = Modifier.width(120.dp),
                        isHorizontal = row.isHorizontalImages
                    )
                }
            }
        }
    }
}
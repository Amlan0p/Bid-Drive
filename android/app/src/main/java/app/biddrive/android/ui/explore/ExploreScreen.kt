@file:OptIn(ExperimentalMaterial3Api::class)

package app.biddrive.android.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.AuctionFilters
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.ImagePreloader
import app.biddrive.android.data.FilterStore
import app.biddrive.android.data.WatchlistRepository
import app.biddrive.android.ui.BaseViewModel
import app.biddrive.android.ui.CollectMessages
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.AuctionCard
import app.biddrive.android.ui.components.BdTextField
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.EmptyState
import app.biddrive.android.ui.components.ErrorState
import app.biddrive.android.ui.components.Load
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.PollWhileStarted
import app.biddrive.android.ui.theme.Bd
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ExploreViewModel(private val repo: AuctionRepository, private val store: FilterStore, watch: WatchlistRepository) : BaseViewModel(watch) {
    val filters: StateFlow<AuctionFilters> = store.filters
    private val _state = MutableStateFlow<Load<List<AuctionDto>>>(Load.Loading)
    val state: StateFlow<Load<List<AuctionDto>>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _queryText = MutableStateFlow(store.filters.value.query)
    val queryText: StateFlow<String> = _queryText.asStateFlow()

    init {
        // Reload whenever the shared filters change; a newer filter set cancels the older request.
        viewModelScope.launch { store.filters.collectLatest { load(it, userInitiated = false) } }
        viewModelScope.launch {
            _queryText.drop(1).debounce(400).collect { q -> if (q != store.filters.value.query) store.set(store.filters.value.copy(query = q)) }
        }
        // Keep the text box in sync when filters are changed from Search & Filters.
        viewModelScope.launch { store.filters.collect { if (it.query != _queryText.value) _queryText.value = it.query } }
    }

    fun setQuery(q: String) { _queryText.value = q }
    fun setStatus(status: String?) { store.set(store.filters.value.copy(statuses = if (status == null) emptySet() else setOf(status))) }
    fun setSort(sort: String) { store.set(store.filters.value.copy(sort = sort)) }
    fun clearFilters() { store.reset() }
    fun refresh(userInitiated: Boolean = false) { viewModelScope.launch { load(store.filters.value, userInitiated) } }

    private suspend fun load(f: AuctionFilters, userInitiated: Boolean) {
        val hadContent = _state.value is Load.Content
        if (userInitiated) _refreshing.value = true
        if (!hadContent) _state.value = Load.Loading
        when (val r = repo.list(f)) {
            is ApiResult.Success -> _state.value = Load.Content(r.value)
            is ApiResult.Failure -> if (!hadContent) _state.value = Load.Error(r.message) else if (userInitiated) say(r.message)
        }
        _refreshing.value = false
    }
}

private val sortOptions = listOf(
    "ending_soon" to "Ending soon", "newest" to "Newly listed", "price_low" to "Price: low to high",
    "price_high" to "Price: high to low", "most_bids" to "Most bids",
)

@Composable
fun ExploreScreen(onOpenAuction: (Int) -> Unit, onOpenFilters: () -> Unit) {
    val vm = appViewModel { c, _ -> ExploreViewModel(c.auctions, c.filters, c.watchlist) }
    val state by vm.state.collectAsState()
    val filters by vm.filters.collectAsState()
    val queryText by vm.queryText.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val watched by vm.watchedIds.collectAsState()
    CollectMessages(vm.messages)
    PollWhileStarted(30_000L, key = filters) { vm.refresh() }

    val context = LocalContext.current
    LaunchedEffect(state) {
        val s = state
        if (s is Load.Content) {
            val urls = s.data.flatMap { it.car?.images.orEmpty() }
            ImagePreloader.preload(context, urls)
        }
    }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("Explore Lots")
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
            BdTextField(queryText, vm::setQuery, "Search cars, brands or models", Modifier.weight(1f), leadingIcon = Icons.Filled.Search)
            IconButton(onClick = onOpenFilters) {
                Box {
                    Icon(Icons.Filled.Tune, "Search & filters", tint = Bd.Amber)
                    // Number of filter groups active (the search text counts too).
                    if (filters.activeCount > 0) Text("${filters.activeCount}", style = Bd.LabelCaps, color = Bd.OnAmber, modifier = Modifier.align(Alignment.TopEnd).background(Bd.Amber, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 4.dp))
                }
            }
        }
        val current = filters.statuses.singleOrNull()
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf<Pair<String?, String>>(null to "All", "live" to "Live", "upcoming" to "Upcoming", "ended" to "Ended")) { (key, label) ->
                FilterChip(
                    selected = if (key == null) filters.statuses.isEmpty() else current == key,
                    onClick = { vm.setStatus(key) }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bd.Amber, selectedLabelColor = Bd.OnAmber, labelColor = Bd.OnSurfaceVariant),
                )
            }
        }
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = { vm.refresh(true) })
            is Load.Content -> {
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.data.size} auctions", style = Bd.LabelNumeric, color = Bd.OnSurfaceVariant, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Text("Sort: ${sortOptions.first { it.first == filters.sort }.second}", color = Bd.Amber)
                            Icon(Icons.Filled.ExpandMore, null, tint = Bd.Amber)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            sortOptions.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { vm.setSort(key); menu = false }) }
                        }
                    }
                }
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh(true) }, modifier = Modifier.fillMaxSize()) {
                    if (s.data.isEmpty()) {
                        EmptyState(
                            "No auctions match", if (filters.activeCount > 0) "Try removing some filters." else "There are no auctions in this category.",
                            actionLabel = if (filters.activeCount > 0) "Clear filters" else null, onAction = if (filters.activeCount > 0) vm::clearFilters else null,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(s.data, key = { it.id }) { a ->
                                AuctionCard(a, a.id in watched, { vm.toggleWatch(a.id) }, { onOpenAuction(a.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

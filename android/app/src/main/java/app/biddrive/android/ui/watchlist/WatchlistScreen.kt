@file:OptIn(ExperimentalMaterial3Api::class)

package app.biddrive.android.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.WatchlistRepository
import app.biddrive.android.ui.BaseViewModel
import app.biddrive.android.ui.CollectMessages
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.AuctionCard
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.EmptyState
import app.biddrive.android.ui.components.ErrorState
import app.biddrive.android.ui.components.Load
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.PollWhileStarted
import app.biddrive.android.ui.theme.Bd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchlistViewModel(private val repo: WatchlistRepository) : BaseViewModel(repo) {
    private val _state = MutableStateFlow<Load<List<AuctionDto>>>(Load.Loading)
    val state: StateFlow<Load<List<AuctionDto>>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh(userInitiated: Boolean = false) {
        viewModelScope.launch {
            val hadContent = _state.value is Load.Content
            if (userInitiated) _refreshing.value = true
            if (!hadContent) _state.value = Load.Loading
            when (val r = repo.load()) {
                is ApiResult.Success -> _state.value = Load.Content(r.value.mapNotNull { it.auction })
                is ApiResult.Failure -> if (!hadContent) _state.value = Load.Error(r.message) else if (userInitiated) say(r.message)
            }
            _refreshing.value = false
        }
    }
}

@Composable
fun WatchlistScreen(onOpenAuction: (Int) -> Unit, onExplore: () -> Unit) {
    val vm = appViewModel { c, _ -> WatchlistViewModel(c.watchlist) }
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val watchedIds by vm.watchedIds.collectAsState()
    var tab by rememberSaveable { mutableStateOf("all") }
    CollectMessages(vm.messages)
    PollWhileStarted(30_000L) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("My Watchlist")
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = { vm.refresh(true) })
            is Load.Content -> {
                // Items un-bookmarked in this session disappear immediately (ids mirror the server).
                val saved = s.data.filter { it.id in watchedIds }
                val tabs = listOf("all" to "All", "live" to "Live", "upcoming" to "Upcoming", "ended" to "Ended")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs) { (key, label) ->
                        val n = if (key == "all") saved.size else saved.count { it.status == key }
                        FilterChip(
                            selected = tab == key, onClick = { tab = key }, label = { Text("$label $n") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bd.Amber, selectedLabelColor = Bd.OnAmber, labelColor = Bd.OnSurfaceVariant),
                        )
                    }
                }
                val shown = if (tab == "all") saved else saved.filter { it.status == tab }
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh(true) }, modifier = Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) {
                        EmptyState(
                            if (saved.isEmpty()) "Your watchlist is empty" else "Nothing in this tab",
                            "Tap the bookmark on any vehicle to save it here.",
                            icon = Icons.Filled.BookmarkBorder, actionLabel = if (saved.isEmpty()) "Explore live auctions" else null, onAction = if (saved.isEmpty()) onExplore else null,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(shown, key = { it.id }) { a -> AuctionCard(a, true, { vm.toggleWatch(a.id) }, { onOpenAuction(a.id) }) }
                        }
                    }
                }
            }
        }
    }
}

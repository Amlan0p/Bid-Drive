package app.biddrive.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.biddrive.android.R
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.AuctionFilters
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.ImagePreloader
import app.biddrive.android.data.WatchlistRepository
import app.biddrive.android.ui.BaseViewModel
import app.biddrive.android.ui.CollectMessages
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.AuctionCard
import app.biddrive.android.ui.components.EmptyState
import app.biddrive.android.ui.components.ErrorState
import app.biddrive.android.ui.components.Load
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.PollWhileStarted
import app.biddrive.android.ui.components.SectionLabel
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.parseInstantMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: AuctionRepository, watch: WatchlistRepository) : BaseViewModel(watch) {
    private val _state = MutableStateFlow<Load<List<AuctionDto>>>(Load.Loading)
    val state: StateFlow<Load<List<AuctionDto>>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh(userInitiated: Boolean = false) {
        viewModelScope.launch {
            val hadContent = _state.value is Load.Content
            if (userInitiated) _refreshing.value = true
            if (!hadContent) _state.value = Load.Loading
            when (val r = repo.list(AuctionFilters())) {
                is ApiResult.Success -> _state.value = Load.Content(r.value)
                is ApiResult.Failure -> if (!hadContent) _state.value = Load.Error(r.message) else if (userInitiated) say(r.message)
            }
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenAuction: (Int) -> Unit, onOpenSearch: () -> Unit, onSeeAll: () -> Unit) {
    val vm = appViewModel { c, _ -> HomeViewModel(c.auctions, c.watchlist) }
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val watched by vm.watchedIds.collectAsState()
    CollectMessages(vm.messages)
    PollWhileStarted(30_000L) { vm.refresh() }

    val context = LocalContext.current
    LaunchedEffect(state) {
        val s = state
        if (s is Load.Content) {
            val urls = s.data.flatMap { it.car?.images.orEmpty() }
            ImagePreloader.preload(context, urls)
        }
    }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.biddrive_logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Text("BidDrive", style = MaterialTheme.typography.headlineMedium, color = Bd.Amber)
        }
        // Search entry point: opens Search & Filters.
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bd.SurfaceLow)
                .border(1.dp, Bd.OutlineVariant, RoundedCornerShape(10.dp)).clickable(onClick = onOpenSearch).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Bd.Outline)
            Spacer(Modifier.width(10.dp))
            Text("Search cars, brands or models", style = MaterialTheme.typography.bodyMedium, color = Bd.Outline, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Tune, "Filters", tint = Bd.Amber)
        }
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = { vm.refresh(true) })
            is Load.Content -> {
                val live = s.data.filter { it.isLive }
                val upcoming = s.data.filter { it.isUpcoming }
                val ended = s.data.filter { it.isEnded }.take(3)
                val spotlight = live.maxByOrNull { it.bidCount }
                val arena = live.filter { it.id != spotlight?.id }
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh(true) }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (s.data.isEmpty()) item { EmptyState("No auctions yet", "Check back soon - new vehicles are listed regularly.") }
                        if (live.isNotEmpty()) item { StatsRow(live) }
                        if (spotlight != null) item {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("Flagship spotlight • most active lot")
                                AuctionCard(spotlight, spotlight.id in watched, { vm.toggleWatch(spotlight.id) }, { onOpenAuction(spotlight.id) }, ctaLabel = "Enter Arena")
                            }
                        }
                        if (arena.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("Live bidding arena • ${arena.size} more", Modifier.padding(horizontal = 16.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(arena, key = { it.id }) { a ->
                                        AuctionCard(a, a.id in watched, { vm.toggleWatch(a.id) }, { onOpenAuction(a.id) }, Modifier.width(300.dp), ctaLabel = "Bid Now")
                                    }
                                }
                            }
                        }
                        if (upcoming.isNotEmpty()) {
                            item {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SectionLabel("Upcoming lots", Modifier.weight(1f))
                                    TextButton(onClick = onSeeAll) { Text("View all", color = Bd.Amber) }
                                }
                            }
                            items(upcoming, key = { it.id }) { a ->
                                AuctionCard(a, a.id in watched, { vm.toggleWatch(a.id) }, { onOpenAuction(a.id) }, Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        if (ended.isNotEmpty()) {
                            item { SectionLabel("Recently ended", Modifier.padding(horizontal = 16.dp)) }
                            items(ended, key = { it.id }) { a ->
                                AuctionCard(a, a.id in watched, { vm.toggleWatch(a.id) }, { onOpenAuction(a.id) }, Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Real figures computed from the loaded live auctions - nothing here is hard-coded. */
@Composable
private fun StatsRow(live: List<AuctionDto>) {
    val endingSoon = live.count { parseInstantMillis(it.endTime) - parseInstantMillis(it.serverTime) < 3_600_000 }
    val reserveMet = live.count { it.reserveMet }
    val avgBids = if (live.isEmpty()) 0 else live.sumOf { it.bidCount } / live.size
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("${live.size}" to "Live now", "$endingSoon" to "Ending < 1h", "$reserveMet" to "Reserve met", "$avgBids" to "Avg bids / lot")) { (n, label) ->
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Bd.Surface).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(n, style = Bd.BidMetricSmall, color = Bd.Amber)
                Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
            }
        }
    }
}

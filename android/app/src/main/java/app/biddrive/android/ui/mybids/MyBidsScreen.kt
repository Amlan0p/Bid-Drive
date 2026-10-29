@file:OptIn(ExperimentalMaterial3Api::class)

package app.biddrive.android.ui.mybids

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.MyBidDto
import app.biddrive.android.ui.CollectMessages
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.CarImage
import app.biddrive.android.ui.components.CountdownChip
import app.biddrive.android.ui.components.EmptyState
import app.biddrive.android.ui.components.ErrorState
import app.biddrive.android.ui.components.Load
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.Pill
import app.biddrive.android.ui.components.PollWhileStarted
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.formatInr
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyBidsViewModel(private val repo: AuctionRepository) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<MyBidDto>>>(Load.Loading)
    val state: StateFlow<Load<List<MyBidDto>>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun refresh(userInitiated: Boolean = false) {
        viewModelScope.launch {
            val hadContent = _state.value is Load.Content
            if (userInitiated) _refreshing.value = true
            if (!hadContent) _state.value = Load.Loading
            when (val r = repo.myBids()) {
                is ApiResult.Success -> _state.value = Load.Content(r.value)
                is ApiResult.Failure -> if (!hadContent) _state.value = Load.Error(r.message) else if (userInitiated) _messages.tryEmit(r.message)
            }
            _refreshing.value = false
        }
    }
}

private enum class BidTab(val label: String) { All("All"), Winning("Winning"), Outbid("Outbid"), Won("Won"), History("History") }

private fun MyBidDto.inTab(tab: BidTab) = when (tab) {
    BidTab.All -> true
    BidTab.Winning -> state == "leading"
    BidTab.Outbid -> state == "outbid"
    BidTab.Won -> state == "won"
    BidTab.History -> state == "lost" || state == "ended_reserve_not_met" || state == "won"
}

@Composable
fun MyBidsScreen(onOpenAuction: (Int) -> Unit, onExplore: () -> Unit) {
    val vm = appViewModel { c, _ -> MyBidsViewModel(c.auctions) }
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var tab by rememberSaveable { mutableStateOf(BidTab.All.name) }
    CollectMessages(vm.messages)
    PollWhileStarted(15_000L) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("My Bids")
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = { vm.refresh(true) })
            is Load.Content -> {
                val all = s.data
                val active = all.count { it.state == "leading" || it.state == "outbid" }
                val leading = all.count { it.state == "leading" }
                val outbid = all.count { it.state == "outbid" }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Active", active, Bd.Amber, Modifier.weight(1f))
                    Stat("Leading", leading, Bd.Green, Modifier.weight(1f))
                    Stat("Outbid", outbid, Bd.Red, Modifier.weight(1f))
                }
                Text("Refreshes every 15 seconds while this screen is open.", style = Bd.LabelCaps, color = Bd.Outline, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                val current = BidTab.valueOf(tab)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BidTab.entries.toList()) { t ->
                        FilterChip(
                            selected = current == t, onClick = { tab = t.name }, label = { Text("${t.label} ${all.count { it.inTab(t) }}") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bd.Amber, selectedLabelColor = Bd.OnAmber, labelColor = Bd.OnSurfaceVariant),
                        )
                    }
                }
                val shown = all.filter { it.inTab(current) }
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh(true) }, modifier = Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) {
                        EmptyState(
                            if (all.isEmpty()) "You haven't placed any bids" else "Nothing in this tab",
                            if (all.isEmpty()) "Find a live auction and place your first bid." else "Your bids will appear here as auctions progress.",
                            icon = Icons.Filled.Gavel, actionLabel = if (all.isEmpty()) "Explore auctions" else null, onAction = if (all.isEmpty()) onExplore else null,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(shown, key = { it.auction.id }) { bid -> BidCard(bid) { onOpenAuction(bid.auction.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Bd.Surface).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(value.toString().padStart(2, '0'), style = Bd.BidMetric, color = color)
        Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
    }
}

@Composable
private fun BidCard(bid: MyBidDto, onOpen: () -> Unit) {
    val a = bid.auction
    val (pillText, pillColor) = when (bid.state) {
        "leading" -> "HIGHEST BIDDER" to Bd.Green
        "outbid" -> "OUTBID" to Bd.Red
        "won" -> "AUCTION WON" to Bd.Green
        "ended_reserve_not_met" -> "ENDED • RESERVE NOT MET" to Bd.Outline
        else -> "LOST" to Bd.Outline
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface)
            .border(1.dp, (if (bid.state == "outbid") Bd.Red else Bd.OutlineVariant).copy(alpha = 0.5f), RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(pillText, pillColor)
            Spacer(Modifier.weight(1f))
            if (a.isLive) CountdownChip(a)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CarImage(a.car?.images?.firstOrNull(), Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("LOT #${a.id}", style = Bd.LabelCaps, color = Bd.Outline)
                Text(a.car?.title ?: "Vehicle", style = MaterialTheme.typography.titleMedium, color = Bd.OnSurface)
                Text("${bid.myBidCount} bid${if (bid.myBidCount == 1) "" else "s"} by you", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
            }
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text("YOUR HIGHEST BID", style = Bd.LabelCaps, color = Bd.Outline)
                Text(formatInr(bid.myHighestBid), style = Bd.BidMetricSmall, color = Bd.OnSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(if (a.isEnded) "FINAL BID" else "CURRENT HIGH BID", style = Bd.LabelCaps, color = Bd.Outline)
                Text(formatInr(a.winningBid ?: a.currentBid), style = Bd.BidMetricSmall, color = if (bid.state == "outbid") Bd.Red else Bd.Amber)
            }
        }
        when (bid.state) {
            "outbid" -> PrimaryButton("Bid again", onOpen, Modifier.fillMaxWidth())
            "leading" -> Text(if (a.reserveMet) "Reserve met - you're in pole position." else "You lead, but the reserve price has not been met yet.", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
            "won" -> Text(bid.payment?.message ?: "Payment integration coming soon.", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Bd.SurfaceHigh).padding(10.dp))
            "ended_reserve_not_met" -> Text("The auction closed below the reserve price, so there is no winner.", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
            else -> Unit
        }
    }
}

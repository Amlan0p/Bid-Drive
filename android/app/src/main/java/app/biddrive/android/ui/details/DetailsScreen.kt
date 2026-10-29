package app.biddrive.android.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.ImagePreloader
import app.biddrive.android.data.SessionState
import app.biddrive.android.data.SessionStore
import app.biddrive.android.data.WatchlistRepository
import app.biddrive.android.ui.BaseViewModel
import app.biddrive.android.ui.CollectMessages
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTextField
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.CarImage
import app.biddrive.android.ui.components.CountdownChip
import app.biddrive.android.ui.components.ErrorState
import app.biddrive.android.ui.components.Load
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.PollWhileStarted
import app.biddrive.android.ui.components.Pill
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SectionLabel
import app.biddrive.android.ui.components.StatusBadge
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.formatInr
import app.biddrive.android.util.formatKm
import app.biddrive.android.util.formatRelativePast
import app.biddrive.android.util.fuelLabel
import app.biddrive.android.util.transmissionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(
    handle: SavedStateHandle, private val repo: AuctionRepository, watch: WatchlistRepository, session: SessionStore,
) : BaseViewModel(watch) {
    val auctionId: Int = checkNotNull(handle.get<Int>("auctionId"))
    val myId: Int? = (session.state.value as? SessionState.SignedIn)?.user?.id
    private val _state = MutableStateFlow<Load<AuctionDto>>(Load.Loading)
    val state: StateFlow<Load<AuctionDto>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val hadContent = _state.value is Load.Content
            when (val r = repo.detail(auctionId)) {
                is ApiResult.Success -> _state.value = Load.Content(r.value)
                is ApiResult.Failure -> if (!hadContent) _state.value = Load.Error(r.message)
            }
        }
    }
}

@Composable
fun AuctionDetailsScreen(onBack: () -> Unit, onPlaceBid: (auctionId: Int, amount: Long) -> Unit) {
    val vm = appViewModel { c, h -> DetailsViewModel(h, c.auctions, c.watchlist, c.session) }
    val state by vm.state.collectAsState()
    val watched by vm.watchedIds.collectAsState()
    CollectMessages(vm.messages)
    // There is no push channel on mobile: the page polls the server every 5 seconds while it is visible.
    PollWhileStarted(5_000L) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("Live Lot Arena", onBack = onBack) {
            IconButton(onClick = { vm.toggleWatch(vm.auctionId) }) {
                val on = vm.auctionId in watched
                Icon(if (on) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (on) "Remove from watchlist" else "Add to watchlist", tint = if (on) Bd.Amber else Bd.OnSurface)
            }
        }
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = { vm.refresh() })
            is Load.Content -> AuctionContent(s.data, vm.myId, onPlaceBid)
        }
    }
}

@Composable
private fun AuctionContent(a: AuctionDto, myId: Int?, onPlaceBid: (Int, Long) -> Unit) {
    val car = a.car
    val iAmHighest = myId != null && a.highestBidderId == myId
    var amountText by rememberSaveable(a.id) { mutableStateOf("") }
    var seeded by rememberSaveable(a.id) { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(a.minNextBid) { if (!seeded) { amountText = a.minNextBid.toLong().toString(); seeded = true } }
    val amount = amountText.toLongOrNull()
    val minBid = a.minNextBid.toLong()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Gallery
            val images = car?.images.orEmpty().ifEmpty { listOf("") }
            val pager = rememberPagerState(pageCount = { images.size })
            val context = LocalContext.current
            LaunchedEffect(images) {
                ImagePreloader.preload(context, images)
            }
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page -> CarImage(images[page].ifEmpty { null }, Modifier.fillMaxSize()) }
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { StatusBadge(a.status) }
                if (images.size > 1) Text("${pager.currentPage + 1} / ${images.size}", style = Bd.LabelNumeric, color = Bd.OnSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(4.dp)).background(Bd.Background.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Title + timer
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LOT #${a.id}", style = Bd.LabelCaps, color = Bd.Outline)
                    Text(car?.title ?: "Vehicle", style = MaterialTheme.typography.headlineLarge, color = Bd.OnSurface)
                    if (car != null) Text("${car.location}${car.registrationNumber?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
                    if (!a.isEnded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (a.isLive) "CLOSES IN  " else "OPENS IN  ", style = Bd.LabelCaps, color = Bd.Outline)
                            CountdownChip(a)
                        }
                    }
                }

                // Price panel
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val headline = when {
                        a.isEnded && a.winningBid != null -> "FINAL WINNING BID"
                        a.isEnded -> "CLOSING BID"
                        a.bidCount == 0 -> "STARTING BID"
                        else -> "CURRENT HIGH BID"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(headline, style = Bd.LabelCaps, color = Bd.Outline)
                            Text(formatInr(if (a.isEnded && a.winningBid != null) a.winningBid else a.displayPrice), style = Bd.BidMetric, color = Bd.Amber)
                        }
                        if (iAmHighest && !a.isEnded) Pill("YOU'RE HIGHEST", Bd.Green)
                    }
                    HorizontalDivider(color = Bd.OutlineVariant.copy(alpha = 0.5f))
                    Row {
                        Metric("Starting bid", formatInr(a.startingPrice), Modifier.weight(1f))
                        Metric("Min. increment", "+" + formatInr(a.bidIncrement), Modifier.weight(1f))
                    }
                    Row {
                        Metric(if (a.isEnded) "Total bids" else "Next minimum bid", if (a.isEnded) "${a.bidCount}" else formatInr(a.minNextBid), Modifier.weight(1f))
                        Metric("Reserve", if (a.bidCount > 0 && a.reserveMet) "Met" else "Not met", Modifier.weight(1f), valueColor = if (a.reserveMet) Bd.Green else Bd.OnSurface)
                    }
                    if (!a.isEnded) Metric("Total bids", "${a.bidCount}")
                }

                if (a.isEnded) EndedBanner(a, myId)

                // Spec dossier
                if (car != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Vehicle specifications")
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SpecRow("Year", "${car.year}")
                            SpecRow("Make", car.brand)
                            SpecRow("Model", car.model)
                            SpecRow("Odometer", formatKm(car.mileage))
                            SpecRow("Fuel", fuelLabel(car.fuelType))
                            SpecRow("Transmission", transmissionLabel(car.transmission))
                            car.color?.let { SpecRow("Colour", it) }
                            SpecRow("Location", car.location)
                        }
                        car.inspectionReport?.takeIf { it.isNotBlank() }?.let { Note("Inspection", it) }
                        car.damageReport?.takeIf { it.isNotBlank() }?.let { Note("Damage report", it) }
                    }
                }

                // Bid history
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Bid history", Modifier.weight(1f))
                        if (a.isLive) Text("updates every 5 s", style = Bd.LabelCaps, color = Bd.Outline)
                    }
                    if (a.recentBids.isEmpty()) {
                        Text("No bids yet.", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
                    } else {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(horizontal = 16.dp, vertical = 4.dp)) {
                            a.recentBids.forEachIndexed { i, b ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(formatInr(b.amount), style = Bd.LabelNumeric, color = if (i == 0) Bd.Amber else Bd.OnSurface)
                                        Text("${b.bidderLabel.ifEmpty { "Bidder" }}${if (b.bidderId == myId) " (you)" else ""} • ${formatRelativePast(b.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
                                    }
                                    if (i == 0) Pill("HIGH BID", Bd.Amber)
                                }
                                if (i < a.recentBids.lastIndex) HorizontalDivider(color = Bd.OutlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Bid bar
        if (!a.isEnded) {
            Column(Modifier.fillMaxWidth().background(Bd.SurfaceLow).navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    a.isUpcoming -> Text("Bidding opens when the auction starts.", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
                    iAmHighest -> Text("You are the highest bidder. You can bid again once someone outbids you.", style = MaterialTheme.typography.bodyMedium, color = Bd.Green)
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 4).forEach { steps ->
                                val value = minBid + steps * a.bidIncrement.toLong()
                                Text(
                                    if (steps == 0) "Min ${formatInr(value)}" else "+${formatInr(steps * a.bidIncrement)}",
                                    style = Bd.LabelNumeric, color = Bd.Amber,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Bd.OutlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { amountText = value.toString(); inputError = null }.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                        BdTextField(
                            amountText, { amountText = it.filter(Char::isDigit).take(11); inputError = null }, "Your bid", prefix = "₹ ",
                            keyboardType = KeyboardType.Number, error = inputError,
                        )
                        if (amount != null && amount < minBid && inputError == null) Text("Minimum bid is now ${formatInr(minBid)}.", style = MaterialTheme.typography.bodySmall, color = Bd.Red)
                    }
                }
                if (a.isLive && !iAmHighest) {
                    PrimaryButton(
                        "Place Bid" + (amount?.let { " • ${formatInr(it)}" } ?: ""),
                        onClick = {
                            val v = amount
                            when {
                                v == null || v <= 0 -> inputError = "Enter a bid amount"
                                v < minBid -> inputError = "Minimum bid is ${formatInr(minBid)}"
                                else -> onPlaceBid(a.id, v)
                            }
                        },
                        Modifier.fillMaxWidth(), icon = Icons.Filled.Gavel,
                    )
                    Text("Bids are binding. The server confirms every bid before it counts.", style = MaterialTheme.typography.bodySmall, color = Bd.Outline)
                }
            }
        }
    }
}

@Composable
private fun EndedBanner(a: AuctionDto, myId: Int?) {
    val (title, body, color) = when {
        a.winnerId != null && a.winnerId == myId -> Triple("You won this auction", "Payment integration coming soon. No payment is collected or recorded in the app yet.", Bd.Green)
        a.winnerId != null -> Triple("Auction ended - sold", "This vehicle was won by another bidder.", Bd.OnSurfaceVariant)
        a.bidCount == 0 -> Triple("Auction ended - no bids", "This lot received no bids.", Bd.OnSurfaceVariant)
        else -> Triple("Auction ended - reserve not met", "The highest bid did not reach the reserve price, so there is no winner.", Bd.Red)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = Bd.OnSurface) {
    Column(modifier) {
        Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
        Text(value, style = Bd.LabelNumeric, color = valueColor)
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant, modifier = Modifier.width(120.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurface, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Bd.OutlineVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun Note(title: String, text: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurface)
    }
}

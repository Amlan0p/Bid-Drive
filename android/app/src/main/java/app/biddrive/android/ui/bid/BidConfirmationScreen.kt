package app.biddrive.android.ui.bid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.SessionState
import app.biddrive.android.data.SessionStore
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.CarImage
import app.biddrive.android.ui.components.CountdownChip
import app.biddrive.android.ui.components.InlineError
import app.biddrive.android.ui.components.LoadingState
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.formatInr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BidOutcome {
    /** The SERVER's auction state after the bid; never inferred locally. */
    data class Accepted(val auction: AuctionDto) : BidOutcome
    data class Rejected(val message: String, val unconfirmed: Boolean) : BidOutcome
}

data class ConfirmUi(
    val auction: AuctionDto? = null,
    val loadError: String? = null,
    val agreed: Boolean = false,
    val submitting: Boolean = false,
    val outcome: BidOutcome? = null,
)

class BidConfirmationViewModel(handle: SavedStateHandle, private val repo: AuctionRepository, session: SessionStore) : ViewModel() {
    private val auctionId: Int = checkNotNull(handle.get<Int>("auctionId"))
    val amount: Long = checkNotNull(handle.get<Long>("amount"))
    private val myId: Int? = (session.state.value as? SessionState.SignedIn)?.user?.id
    private val _ui = MutableStateFlow(ConfirmUi())
    val ui: StateFlow<ConfirmUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val r = repo.detail(auctionId)) {
                is ApiResult.Success -> _ui.update { it.copy(auction = r.value, loadError = null) }
                is ApiResult.Failure -> _ui.update { it.copy(loadError = r.message) }
            }
        }
    }

    fun setAgreed(v: Boolean) = _ui.update { it.copy(agreed = v) }

    fun submit() {
        val s = _ui.value
        if (s.submitting || s.outcome != null || !s.agreed) return        // duplicate-submit guard
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val r = repo.placeBid(auctionId, amount)) {
                is ApiResult.Success -> _ui.update { it.copy(submitting = false, outcome = BidOutcome.Accepted(r.value.auction)) }
                is ApiResult.Failure -> {
                    val unclear = r.code == "timeout" || r.code == "network" || r.code == "unknown"
                    if (unclear) {
                        // We don't know if the server recorded it - ask the server instead of guessing.
                        val check = repo.detail(auctionId)
                        if (check is ApiResult.Success && check.value.highestBidderId == myId && check.value.currentBid.toLong() == amount) {
                            _ui.update { it.copy(submitting = false, auction = check.value, outcome = BidOutcome.Accepted(check.value)) }
                            return@launch
                        }
                        _ui.update { it.copy(submitting = false, outcome = BidOutcome.Rejected(r.message + " We couldn't confirm whether your bid was recorded - check the auction before trying again.", true)) }
                    } else {
                        _ui.update { it.copy(submitting = false, outcome = BidOutcome.Rejected(r.message, false)) }
                        load() // refresh so the user sees the current minimum
                    }
                }
            }
        }
    }
}

@Composable
fun BidConfirmationScreen(onBack: () -> Unit, onViewMyBids: () -> Unit) {
    val vm = appViewModel { c, h -> BidConfirmationViewModel(h, c.auctions, c.session) }
    val ui by vm.ui.collectAsState()
    val a = ui.auction

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("Bid Confirmation", onBack = if (ui.submitting) null else onBack)
        when {
            a == null && ui.loadError != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InlineError(ui.loadError!!)
                SecondaryButton("Try again", vm::load)
            }
            a == null -> LoadingState()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val outcome = ui.outcome
                // Vehicle
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface), verticalAlignment = Alignment.CenterVertically) {
                    CarImage(a.car?.images?.firstOrNull(), Modifier.size(width = 120.dp, height = 90.dp))
                    Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("LOT #${a.id}", style = Bd.LabelCaps, color = Bd.Outline)
                        Text(a.car?.title ?: "Vehicle", style = MaterialTheme.typography.titleMedium, color = Bd.OnSurface)
                        if (!a.isEnded) CountdownChip(a)
                    }
                }

                if (outcome is BidOutcome.Accepted) {
                    val fin = outcome.auction
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Green.copy(alpha = 0.12f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Bd.Green, modifier = Modifier.size(44.dp))
                        Text("Bid accepted", style = MaterialTheme.typography.headlineMedium, color = Bd.Green)
                        Text("The server recorded your bid of ${formatInr(vm.amount)}.", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurface, textAlign = TextAlign.Center)
                        Text("Current highest bid: ${formatInr(fin.currentBid)} • ${fin.bidCount} bids", style = Bd.LabelNumeric, color = Bd.OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    PrimaryButton("Back to auction", onBack, Modifier.fillMaxWidth())
                    SecondaryButton("View My Bids", onViewMyBids, Modifier.fillMaxWidth())
                } else {
                    // Summary
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Line("Current highest bid", formatInr(if (a.bidCount == 0) a.startingPrice else a.currentBid), Bd.OnSurface)
                        Line("Minimum permitted bid", formatInr(a.minNextBid), Bd.OnSurface)
                        Line("Minimum increment", formatInr(a.bidIncrement), Bd.OnSurface)
                        HorizontalDivider(color = Bd.OutlineVariant.copy(alpha = 0.5f))
                        Line("Your bid", formatInr(vm.amount), Bd.Amber, big = true)
                    }
                    val tooLow = vm.amount < a.minNextBid.toLong()
                    if (a.isEnded) InlineError("This auction has ended. Your bid cannot be placed.")
                    else if (a.isUpcoming) InlineError("This auction has not started yet.")
                    else if (tooLow) InlineError("The minimum bid is now ${formatInr(a.minNextBid)}. Go back and update your bid.")
                    if (outcome is BidOutcome.Rejected) InlineError(outcome.message)

                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(ui.agreed, vm::setAgreed, enabled = !ui.submitting, colors = CheckboxDefaults.colors(checkedColor = Bd.Amber, checkmarkColor = Bd.OnAmber))
                        Text("I understand this is a binding bid of ${formatInr(vm.amount)}.", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                    PrimaryButton(
                        "Confirm Bid", vm::submit, Modifier.fillMaxWidth(), icon = Icons.Filled.Gavel,
                        enabled = ui.agreed && !tooLow && a.isLive && outcome == null, loading = ui.submitting,
                    )
                    SecondaryButton(if (outcome != null) "Back to auction" else "Cancel", onBack, Modifier.fillMaxWidth(), enabled = !ui.submitting)
                    Spacer(Modifier.height(4.dp))
                    Text("Your bid is only counted once the server confirms it.", style = MaterialTheme.typography.bodySmall, color = Bd.Outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (big) Bd.BidMetricSmall else Bd.LabelNumeric, color = color)
    }
}

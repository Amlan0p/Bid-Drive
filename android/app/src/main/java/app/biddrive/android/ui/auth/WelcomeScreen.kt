package app.biddrive.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.R
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionFilters
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.components.SectionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WelcomeStats(val live: Int, val upcoming: Int, val total: Int)

class WelcomeViewModel(private val repo: AuctionRepository) : ViewModel() {
    private val _stats = MutableStateFlow<WelcomeStats?>(null)
    /** Real counts from the public catalogue; stays null (stats hidden) if the server can't be reached. */
    val stats: StateFlow<WelcomeStats?> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            val r = repo.list(AuctionFilters())
            if (r is ApiResult.Success) _stats.value = WelcomeStats(r.value.count { it.isLive }, r.value.count { it.isUpcoming }, r.value.size)
        }
    }
}

@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val vm = appViewModel { c, _ -> WelcomeViewModel(c.auctions) }
    val stats by vm.stats.collectAsState()

    Column(Modifier.fillMaxSize().background(Bd.Background).verticalScroll(rememberScrollState())) {
        // ---- Hero
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            Image(painterResource(R.drawable.welcome_hero), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bd.Background.copy(alpha = 0.55f), Bd.Background))))
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.biddrive_logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("BidDrive", style = MaterialTheme.typography.headlineMedium, color = Bd.Amber)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Bd.Background.copy(alpha = 0.88f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Bd.Live))
                    Spacer(Modifier.width(6.dp))
                    Text(stats?.let { "${it.live} LIVE LOT${if (it.live == 1) "" else "S"}" } ?: "LIVE AUCTIONS", style = Bd.LabelCaps, color = Bd.OnSurface)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Bd.Amber))
                    Spacer(Modifier.width(8.dp))
                    Text("THE VEHICLE AUCTION ARENA", style = Bd.LabelCaps, color = Bd.OnSurfaceVariant)
                }
                Text("Own The Apex.", style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold), color = Bd.OnSurface)
            }
        }

        // ---- Body
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Bid on cars in live auctions. Every bid is checked and recorded by the server, so the highest bid is always the one that counts.",
                style = MaterialTheme.typography.bodyLarge, color = Bd.OnSurfaceVariant,
            )

            stats?.let { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(vertical = 14.dp),
                ) {
                    StatCell(s.live.toString(), "Live lots", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(Bd.OutlineVariant).align(Alignment.CenterVertically))
                    StatCell(s.upcoming.toString(), "Upcoming", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(Bd.OutlineVariant).align(Alignment.CenterVertically))
                    StatCell(s.total.toString(), "Vehicles", Modifier.weight(1f))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Why BidDrive")
                // Horizontally scrollable pillars, as in the design.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pillar(Icons.Filled.Gavel, "SERVER-VERIFIED", "Every bid is checked", "Bids are validated and recorded by the server in strict order - no stale or duplicate bids.")
                    Pillar(Icons.Filled.VerifiedUser, "VEHICLE DETAILS", "Specs & inspection notes", "Each lot lists year, mileage, fuel, location and any inspection or damage notes the seller provided.")
                    Pillar(Icons.Filled.Timer, "LIVE TRACKING", "Follow the clock", "Countdowns and bid history refresh every few seconds while you watch. Watchlist and My Bids keep you on top of it.")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Create Account", onCreateAccount, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.ArrowForward)
                SecondaryButton("Sign In", onSignIn, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Login)
                Text(
                    "An account is needed to browse and bid. Sign-up takes a minute, with no verification codes. You can also try the demo account from the Sign In screen.",
                    style = MaterialTheme.typography.bodySmall, color = Bd.Outline,
                )
            }

            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Lock, null, tint = Bd.Outline, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("SECURE SIGN-IN • BIDS CONFIRMED BY THE SERVER", style = Bd.LabelCaps, color = Bd.Outline)
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Bd.BidMetricSmall, color = Bd.Amber)
        Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
    }
}

@Composable
private fun Pillar(icon: ImageVector, tag: String, title: String, body: String) {
    Column(
        Modifier.width(280.dp).clip(RoundedCornerShape(12.dp)).background(Bd.SurfaceLow).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Bd.Surface), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Bd.Amber, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(tag, style = Bd.LabelCaps, color = Bd.Outline)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Bd.OnSurface)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
    }
}

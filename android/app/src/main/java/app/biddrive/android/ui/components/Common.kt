package app.biddrive.android.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.biddrive.android.data.ApiConfig
import app.biddrive.android.data.AuctionDto
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.fuelLabel
import app.biddrive.android.util.formatCountdown
import app.biddrive.android.util.formatInr
import app.biddrive.android.util.formatKm
import app.biddrive.android.util.parseInstantMillis
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** Generic load state for screens. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Content<T>(val data: T) : Load<T>
}

// ------------------------------------------------------------------ polling / clock

/** Runs [action] immediately and then every [intervalMs] while the screen is at least STARTED. */
@Composable
fun PollWhileStarted(intervalMs: Long, key: Any? = Unit, action: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, key, intervalMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                action()
                delay(intervalMs)
            }
        }
    }
}

/** Milliseconds remaining until [target]; ticks every second and corrects for device/server clock skew. */
@Composable
fun rememberCountdownMs(targetIso: String, serverTimeIso: String): State<Long> {
    val skew = remember(serverTimeIso) { parseInstantMillis(serverTimeIso) - System.currentTimeMillis() }
    val target = remember(targetIso) { parseInstantMillis(targetIso) }
    val state = remember(targetIso, serverTimeIso) { mutableStateOf(target - (System.currentTimeMillis() + skew)) }
    LaunchedEffect(targetIso, serverTimeIso) {
        while (true) {
            state.value = target - (System.currentTimeMillis() + skew)
            delay(1000)
        }
    }
    return state
}

// ------------------------------------------------------------------ small pieces

@Composable
fun CarImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val resolved = remember(url) { ApiConfig.imageUrl(url) }
    val context = LocalContext.current
    val imageRequest = remember(resolved) {
        if (resolved != null) {
            ImageRequest.Builder(context)
                .data(resolved)
                .memoryCacheKey(resolved)
                .diskCacheKey(resolved)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(100)
                .build()
        } else null
    }

    Box(
        modifier = modifier.background(Bd.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = Bd.Outline,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (status) {
        "live" -> Triple("LIVE", Bd.Live, Color.White)
        "upcoming" -> Triple("UPCOMING", Bd.SurfaceHighest, Bd.AmberSoft)
        else -> Triple("ENDED", Bd.SurfaceHighest, Bd.Outline)
    }
    Text(
        label, style = Bd.LabelCaps, color = fg,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = Bd.LabelCaps, color = color,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun CountdownChip(auction: AuctionDto, modifier: Modifier = Modifier) {
    val target = if (auction.isUpcoming) auction.startTime else auction.endTime
    val ms by rememberCountdownMs(target, auction.serverTime)
    val text = when {
        auction.isEnded || ms <= 0 && auction.isLive -> "Ended"
        auction.isUpcoming -> "Starts in ${formatCountdown(ms)}"
        else -> formatCountdown(ms)
    }
    val urgent = auction.isLive && ms in 1..(15 * 60_000)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Timer, null, tint = if (urgent) Bd.Live else Bd.Amber, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = Bd.LabelNumeric, color = if (urgent) Bd.Live else Bd.OnSurface)
    }
}

@Composable
fun SpecItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Bd.Outline, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null,
) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Bd.Amber, contentColor = Bd.OnAmber,
            disabledContainerColor = Bd.SurfaceHighest, disabledContentColor = Bd.Outline,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Bd.OnAmber, strokeWidth = 2.dp)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Bd.OutlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Bd.OnSurface),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun bdFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Bd.OnSurface, unfocusedTextColor = Bd.OnSurface,
    focusedContainerColor = Bd.SurfaceLow, unfocusedContainerColor = Bd.SurfaceLow,
    focusedBorderColor = Bd.Amber, unfocusedBorderColor = Bd.OutlineVariant,
    focusedLabelColor = Bd.Amber, unfocusedLabelColor = Bd.Outline,
    cursorColor = Bd.Amber, errorBorderColor = Bd.Red, errorLabelColor = Bd.Red,
    focusedLeadingIconColor = Bd.Amber, unfocusedLeadingIconColor = Bd.Outline,
    focusedPlaceholderColor = Bd.Outline, unfocusedPlaceholderColor = Bd.Outline,
)

@Composable
fun BdTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null, error: String? = null, keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false, enabled: Boolean = true, singleLine: Boolean = true, placeholder: String? = null,
    prefix: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), enabled = enabled, singleLine = singleLine,
        shape = RoundedCornerShape(10.dp), colors = bdFieldColors(),
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it, color = Bd.Amber) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = if (isPassword) ({
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide password" else "Show password", tint = Bd.Outline)
            }
        }) else null,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
fun BdTopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(Bd.Background).statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Bd.OnSurface) }
        } else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Bd.OnSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Bd.Amber) }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.ErrorOutline, null, tint = Bd.Red, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurface, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Try again", onRetry)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = Bd.Outline, modifier = Modifier.size(40.dp)); Spacer(Modifier.height(12.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Bd.OnSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(
        message, color = Bd.Red, style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Bd.RedContainer.copy(alpha = 0.35f)).padding(12.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Bd.LabelCaps, color = Bd.Outline, modifier = modifier)
}

// ------------------------------------------------------------------ auction card

@Composable
fun AuctionCard(
    auction: AuctionDto, watched: Boolean, onToggleWatch: () -> Unit, onClick: () -> Unit,
    modifier: Modifier = Modifier, ctaLabel: String? = null,
) {
    val car = auction.car
    val priceLabel = when {
        auction.isEnded && auction.winningBid != null -> "Final winning bid"
        auction.isEnded -> "Closing bid"
        auction.bidCount == 0 -> "Starting bid"
        else -> "Current highest bid"
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface)
            .border(1.dp, Bd.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            CarImage(car?.images?.firstOrNull(), Modifier.fillMaxSize())
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(auction.status)
                if (!auction.isEnded) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Bd.Background.copy(alpha = 0.88f)).padding(horizontal = 8.dp, vertical = 3.dp)) { CountdownChip(auction) }
                }
            }
            IconButton(
                onClick = onToggleWatch,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(Bd.Background.copy(alpha = 0.88f)),
            ) {
                Icon(if (watched) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (watched) "Remove from watchlist" else "Add to watchlist", tint = if (watched) Bd.Amber else Bd.OnSurface)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(car?.title ?: "Vehicle #${auction.carId}", style = MaterialTheme.typography.titleMedium, color = Bd.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (car != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SpecItem(Icons.Filled.LocationOn, car.location)
                    Text("${formatKm(car.mileage)} • ${fuelLabel(car.fuelType)}", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(priceLabel.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
                    Text(formatInr(if (auction.isEnded && auction.winningBid != null) auction.winningBid else auction.displayPrice), style = Bd.BidMetricSmall, color = Bd.Amber)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${auction.bidCount} bids", style = Bd.LabelNumeric, color = Bd.OnSurfaceVariant)
                    if (auction.isLive && auction.bidCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        if (auction.reserveMet) Pill("RESERVE MET", Bd.Green) else Pill("RESERVE NOT MET", Bd.Outline)
                    }
                }
            }
            if (ctaLabel != null) PrimaryButton(ctaLabel, onClick, Modifier.fillMaxWidth())
        }
    }
}

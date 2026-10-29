@file:OptIn(ExperimentalMaterial3Api::class)

package app.biddrive.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiConfig
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.AuthRepository
import app.biddrive.android.data.SessionState
import app.biddrive.android.data.SessionStore
import app.biddrive.android.data.SettingsStore
import app.biddrive.android.ui.theme.ThemeMode
import app.biddrive.android.data.UserDto
import app.biddrive.android.data.WatchlistRepository
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTextField
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.InlineError
import app.biddrive.android.ui.components.Pill
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.components.SectionLabel
import app.biddrive.android.ui.theme.Bd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUi(
    val saving: Boolean = false, val error: String? = null, val saved: Boolean = false,
    val activeBids: Int? = null, val wonCount: Int? = null,
)

class ProfileViewModel(
    private val auth: AuthRepository, private val auctions: AuctionRepository,
    private val watchlist: WatchlistRepository, session: SessionStore, private val settings: SettingsStore,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.theme
    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)
    val session: StateFlow<SessionState> = session.state
    val watchCount = watchlist.ids
    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            auth.refreshProfile()   // keep the cached profile in sync with the server
            val r = auctions.myBids()
            if (r is ApiResult.Success) _ui.update {
                it.copy(activeBids = r.value.count { b -> b.state == "leading" || b.state == "outbid" }, wonCount = r.value.count { b -> b.state == "won" })
            }
        }
    }

    fun save(name: String, phone: String, onDone: () -> Unit) {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            when (val r = auth.updateProfile(name, phone)) {
                is ApiResult.Success -> { _ui.update { it.copy(saving = false, saved = true) }; onDone() }
                is ApiResult.Failure -> _ui.update { it.copy(saving = false, error = r.message) }
            }
        }
    }

    fun clearSaved() = _ui.update { it.copy(saved = false, error = null) }
    fun logout() { viewModelScope.launch { auth.logout() } }
}

@Composable
fun ProfileScreen() {
    val vm = appViewModel { c, _ -> ProfileViewModel(c.auth, c.auctions, c.watchlist, c.session, c.settings) }
    val sessionState by vm.session.collectAsState()
    val ui by vm.ui.collectAsState()
    val watchCount by vm.watchCount.collectAsState()
    val theme by vm.theme.collectAsState()
    val user = (sessionState as? SessionState.SignedIn)?.user ?: return

    var editing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("My Profile")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Identity card - always the signed-in user's own data from the server.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).border(1.dp, Bd.OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Bd.Amber), contentAlignment = Alignment.Center) {
                    Text(user.name.trim().take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Bd.OnAmber)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleLarge, color = Bd.OnSurface)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
                    if (user.isDemo) Pill("DEMO ACCOUNT", Bd.Amber, Modifier.padding(top = 4.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Active bids", ui.activeBids?.toString() ?: "–", Modifier.weight(1f))
                StatBox("Watchlist", watchCount.size.toString(), Modifier.weight(1f))
                StatBox("Won", ui.wonCount?.toString() ?: "–", Modifier.weight(1f))
            }

            if (editing) {
                EditForm(user, ui, onCancel = { editing = false; vm.clearSaved() }, onSave = { n, p -> vm.save(n, p) { editing = false } })
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Profile details")
                    Detail("Full name", user.name)
                    Detail("Email", user.email)
                    Detail("Phone", user.phone?.takeIf { it.isNotBlank() } ?: "Not set")
                    user.createdAt?.let { Detail("Member since", it.take(10)) }
                    if (ui.saved) Text("Profile saved.", color = Bd.Green, style = MaterialTheme.typography.bodySmall)
                }
                SecondaryButton("Edit profile", { vm.clearSaved(); editing = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Payments")
                Text("Payment integration coming soon.", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurface)
                Text("You can browse, bid and track auctions today. Paying for won vehicles is not available in this version, and no payment details are collected.", style = MaterialTheme.typography.bodySmall, color = Bd.OnSurfaceVariant)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Appearance")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                        FilterChip(
                            selected = theme == mode, onClick = { vm.setTheme(mode) }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bd.Amber, selectedLabelColor = Bd.OnAmber, labelColor = Bd.OnSurfaceVariant),
                        )
                    }
                }
            }

            PrimaryButton("Sign out", { confirmLogout = true }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Logout)
            Text("BidDrive 1.0.0 • server: ${runCatching { java.net.URI(ApiConfig.baseUrl).host }.getOrNull() ?: "?"}", style = Bd.LabelCaps, color = Bd.Outline, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out?") },
            text = { Text(if ((ui.activeBids ?: 0) > 0) "You have ${ui.activeBids} active bid(s). They stay valid on the server, but you won't see updates until you sign in again." else "You'll need to sign in again to bid.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Sign out", color = Bd.Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            containerColor = Bd.SurfaceHigh,
        )
    }
}

@Composable
private fun EditForm(user: UserDto, ui: ProfileUi, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone ?: "") }
    var submitted by remember { mutableStateOf(false) }
    val nameError = if (submitted && name.trim().length < 2) "Enter your full name" else null
    val phoneError = if (submitted && phone.isNotBlank() && !Regex("^[+0-9 ()-]{7,20}$").matches(phone.trim())) "Enter a valid phone number" else null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bd.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Edit profile")
        ui.error?.let { InlineError(it) }
        BdTextField(name, { name = it }, "Full name", leadingIcon = Icons.Filled.Badge, error = nameError, enabled = !ui.saving)
        BdTextField(phone, { phone = it }, "Phone (optional)", leadingIcon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone, error = phoneError, enabled = !ui.saving)
        BdTextField(user.email, {}, "Email (cannot be changed)", enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f), enabled = !ui.saving)
            PrimaryButton("Save changes", {
                submitted = true
                if (name.trim().length >= 2 && (phone.isBlank() || Regex("^[+0-9 ()-]{7,20}$").matches(phone.trim()))) onSave(name, phone)
            }, Modifier.weight(1f), loading = ui.saving)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Bd.Surface).padding(12.dp)) {
        Text(value, style = Bd.BidMetric, color = Bd.Amber)
        Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = Bd.LabelCaps, color = Bd.Outline)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Bd.OnSurface)
    }
}

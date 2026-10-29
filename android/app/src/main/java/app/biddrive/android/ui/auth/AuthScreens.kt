package app.biddrive.android.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.R
import app.biddrive.android.data.ApiConfig
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuthRepository
import app.biddrive.android.data.SessionStore
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTextField
import app.biddrive.android.ui.components.InlineError
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.isValidEmail
import app.biddrive.android.util.passwordProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val demoLoading: Boolean = false, val error: String? = null)

class AuthViewModel(private val auth: AuthRepository, private val session: SessionStore) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()
    val sessionExpired = session.sessionExpired

    fun clearError() { _ui.update { it.copy(error = null) } }
    fun dismissExpired() = session.consumeExpiredFlag()

    private fun run(demo: Boolean, call: suspend () -> ApiResult<*>) {
        if (_ui.value.loading || _ui.value.demoLoading) return   // ignore double taps
        _ui.value = AuthUiState(loading = !demo, demoLoading = demo)
        viewModelScope.launch {
            val r = call()
            // On success the session store flips to SignedIn and navigation leaves this screen.
            _ui.value = AuthUiState(error = (r as? ApiResult.Failure)?.message)
        }
    }

    fun signIn(email: String, password: String) = run(false) { auth.login(email, password) }
    fun signUp(name: String, email: String, password: String) = run(false) { auth.register(name, email, password) }
    fun demoLogin() = run(true) { auth.demoLogin() }
}

@Composable
private fun AuthColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bd.Background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun Brand(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.biddrive_logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.size(12.dp))
        Text("BidDrive", style = MaterialTheme.typography.headlineLarge, color = Bd.Amber)
    }
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.displayMedium, color = Bd.OnSurface)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
}

@Composable
fun SignInScreen(onSignUp: () -> Unit) {
    val vm = appViewModel { c, _ -> AuthViewModel(c.auth, c.session) }
    val ui by vm.ui.collectAsState()
    val expired by vm.sessionExpired.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val busy = ui.loading || ui.demoLoading
    val emailError = if (submitted && !isValidEmail(email)) "Enter a valid email address" else null
    val passError = if (submitted && password.isEmpty()) "Enter your password" else null

    AuthColumn {
        Brand("Welcome back", "Sign in to bid on live vehicle auctions.")
        if (!ApiConfig.isConfigured) InlineError("Backend URL not configured. Rebuild with -PbiddriveApiBaseUrl=https://<your-service>/api/ (see README).")
        if (expired) InlineError("Your session expired. Please sign in again.")
        ui.error?.let { InlineError(it) }

        BdTextField(email, { email = it; vm.clearError() }, "Email", leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email, error = emailError, enabled = !busy)
        BdTextField(password, { password = it; vm.clearError() }, "Password", leadingIcon = Icons.Filled.Lock, isPassword = true, error = passError, enabled = !busy)
        PrimaryButton("Sign In", {
            submitted = true
            vm.dismissExpired()
            if (isValidEmail(email) && password.isNotEmpty()) vm.signIn(email, password)
        }, Modifier.fillMaxWidth(), loading = ui.loading, enabled = !busy)

        Text("— or —", style = Bd.LabelCaps, color = Bd.Outline, modifier = Modifier.align(Alignment.CenterHorizontally))
        SecondaryButton("Demo Login", { vm.dismissExpired(); vm.demoLogin() }, Modifier.fillMaxWidth(), enabled = !busy, icon = Icons.Filled.PlayArrow)
        Text(
            if (ui.demoLoading) "Signing in to the demo account…" else "Signs you in with the seeded demo account (demo@biddrive.app) with sample bids and watchlist. No admin access.",
            style = MaterialTheme.typography.bodySmall, color = Bd.Outline,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("New to BidDrive?", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
            TextButton(onClick = onSignUp, enabled = !busy) { Text("Create account", color = Bd.Amber) }
        }
    }
}

@Composable
fun SignUpScreen(onBackToSignIn: () -> Unit) {
    val vm = appViewModel { c, _ -> AuthViewModel(c.auth, c.session) }
    val ui by vm.ui.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    val nameError = if (submitted && name.trim().length < 2) "Enter your full name" else null
    val emailError = if (submitted && !isValidEmail(email)) "Enter a valid email address" else null
    val passError = if (submitted) passwordProblem(password) else null
    val confirmError = if (submitted && confirm != password) "Passwords do not match" else null
    val valid = name.trim().length >= 2 && isValidEmail(email) && passwordProblem(password) == null && confirm == password

    AuthColumn {
        Brand("Join BidDrive", "Create an account to place bids and track vehicles.")
        if (!ApiConfig.isConfigured) InlineError("Backend URL not configured. See README.")
        ui.error?.let { InlineError(it) }
        BdTextField(name, { name = it; vm.clearError() }, "Full name", leadingIcon = Icons.Filled.Badge, error = nameError, enabled = !ui.loading)
        BdTextField(email, { email = it; vm.clearError() }, "Email", leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email, error = emailError, enabled = !ui.loading)
        BdTextField(password, { password = it; vm.clearError() }, "Password", leadingIcon = Icons.Filled.Lock, isPassword = true, error = passError, enabled = !ui.loading)
        Text("At least 8 characters, including a letter and a number.", style = MaterialTheme.typography.bodySmall, color = Bd.Outline)
        BdTextField(confirm, { confirm = it; vm.clearError() }, "Confirm password", leadingIcon = Icons.Filled.Lock, isPassword = true, error = confirmError, enabled = !ui.loading)
        PrimaryButton("Create Account", {
            submitted = true
            if (valid) vm.signUp(name, email, password)
        }, Modifier.fillMaxWidth(), loading = ui.loading, icon = Icons.Filled.Gavel)
        Text("You'll be signed in right after your account is created. No verification codes are required.", style = MaterialTheme.typography.bodySmall, color = Bd.Outline)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium, color = Bd.OnSurfaceVariant)
            TextButton(onClick = onBackToSignIn, enabled = !ui.loading) { Text("Sign in", color = Bd.Amber) }
        }
    }
}

package app.biddrive.android.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.biddrive.android.AppContainer
import app.biddrive.android.BidDriveApp
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("SnackbarHostState not provided") }

/** Creates a ViewModel wired to the app's [AppContainer] (no DI framework needed). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer, SavedStateHandle) -> VM): VM =
    viewModel(
        key = key,
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BidDriveApp
                create(app.container, createSavedStateHandle())
            }
        },
    )

/** Shows one-off messages (errors, confirmations) from a ViewModel in the global snackbar. */
@Composable
fun CollectMessages(messages: Flow<String>) {
    val host = LocalSnackbar.current
    LaunchedEffect(messages) { messages.collect { host.showSnackbar(it) } }
}

abstract class BaseViewModel(protected val watchRepo: WatchlistRepository) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val watchedIds = watchRepo.ids

    protected fun say(text: String) { _messages.tryEmit(text) }

    fun toggleWatch(auctionId: Int) {
        viewModelScope.launch {
            when (val r = watchRepo.toggle(auctionId)) {
                is ApiResult.Success -> say(if (r.value) "Added to watchlist" else "Removed from watchlist")
                is ApiResult.Failure -> say(r.message)
            }
        }
    }
}

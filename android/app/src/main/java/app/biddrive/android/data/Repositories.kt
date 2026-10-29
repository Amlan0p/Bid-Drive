package app.biddrive.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AuthRepository(private val api: BidDriveApi, private val session: SessionStore) {
    suspend fun login(email: String, password: String): ApiResult<UserDto> =
        finish(apiCall { api.login(LoginRequest(email.trim(), password)) })

    suspend fun register(name: String, email: String, password: String): ApiResult<UserDto> =
        finish(apiCall { api.register(RegisterRequest(name.trim(), email.trim(), password)) })

    /** Real server-issued session for the seeded, non-admin demo account. */
    suspend fun demoLogin(): ApiResult<UserDto> = finish(apiCall { api.demoLogin() })

    suspend fun logout() = session.clear()

    suspend fun refreshProfile(): ApiResult<UserDto> {
        val r = apiCall { api.me() }
        if (r is ApiResult.Success) session.updateUser(r.value)
        return r
    }

    suspend fun updateProfile(name: String, phone: String): ApiResult<UserDto> {
        val r = apiCall { api.updateMe(UpdateProfileRequest(name.trim(), phone.trim())) }
        if (r is ApiResult.Success) session.updateUser(r.value)
        return r
    }

    private suspend fun finish(r: ApiResult<AuthResponse>): ApiResult<UserDto> = when (r) {
        is ApiResult.Success -> { session.save(r.value); ApiResult.Success(r.value.user) }
        is ApiResult.Failure -> r
    }
}

class AuctionRepository(private val api: BidDriveApi) {
    suspend fun list(filters: AuctionFilters): ApiResult<List<AuctionDto>> =
        when (val r = apiCall { api.auctions(filters.toQuery()) }) {
            is ApiResult.Success -> ApiResult.Success(r.value.auctions)
            is ApiResult.Failure -> r
        }

    suspend fun detail(id: Int): ApiResult<AuctionDto> = apiCall { api.auction(id) }

    /** The server decides whether the bid is accepted; the returned auction is the server's new state. */
    suspend fun placeBid(auctionId: Int, amount: Long): ApiResult<PlaceBidResponse> =
        apiCall { api.placeBid(auctionId, PlaceBidRequest(amount)) }

    suspend fun myBids(): ApiResult<List<MyBidDto>> = apiCall { api.myBids() }
}

/** Watchlist is server-persisted per user. [ids] is an in-memory mirror of the server list for fast UI toggles. */
class WatchlistRepository(private val api: BidDriveApi) {
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids.asStateFlow()

    suspend fun load(): ApiResult<List<WatchlistItemDto>> {
        val r = apiCall { api.watchlist() }
        if (r is ApiResult.Success) _ids.value = r.value.map { it.auctionId }.toSet()
        return r
    }

    /** Optimistic toggle that is rolled back if the server call fails. */
    suspend fun toggle(auctionId: Int): ApiResult<Boolean> {
        val wasWatched = auctionId in _ids.value
        _ids.update { if (wasWatched) it - auctionId else it + auctionId }
        val r = apiCall { if (wasWatched) api.removeFromWatchlist(auctionId) else api.addToWatchlist(auctionId) }
        return when (r) {
            is ApiResult.Success -> ApiResult.Success(!wasWatched)
            is ApiResult.Failure -> {
                _ids.update { if (wasWatched) it + auctionId else it - auctionId }
                r
            }
        }
    }

    fun clear() { _ids.value = emptySet() }
}

/** Single source of truth for the filters edited on Search & Filters and applied on Explore. */
class FilterStore {
    private val _filters = MutableStateFlow(AuctionFilters())
    val filters: StateFlow<AuctionFilters> = _filters.asStateFlow()
    fun set(f: AuctionFilters) { _filters.value = f }
    fun reset() { _filters.value = AuctionFilters() }
}

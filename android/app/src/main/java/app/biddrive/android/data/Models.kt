package app.biddrive.android.data

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val name: String,
    val phone: String? = null,
    val role: String = "customer",
    val isDemo: Boolean = false,
    val createdAt: String? = null,
)

@Serializable data class AuthResponse(val token: String, val user: UserDto)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val name: String, val email: String, val password: String)
/** Send phone = "" to clear it. */
@Serializable data class UpdateProfileRequest(val name: String, val phone: String)

@Serializable
data class CarDto(
    val id: Int,
    val brand: String,
    val model: String,
    val year: Int,
    val fuelType: String = "",
    val transmission: String = "",
    val mileage: Int = 0,
    val location: String = "",
    val color: String? = null,
    val registrationNumber: String? = null,
    val damageReport: String? = null,
    val inspectionReport: String? = null,
    val images: List<String> = emptyList(),
) {
    val title: String get() = "$year $brand $model"
}

@Serializable
data class BidDto(
    val id: Int,
    val auctionId: Int = 0,
    val bidderId: Int,
    val amount: Double,
    val createdAt: String,
    val bidderLabel: String = "",
)

@Serializable
data class AuctionDto(
    val id: Int,
    val carId: Int,
    val startingPrice: Double,
    val currentBid: Double,
    val bidIncrement: Double,
    val bidCount: Int,
    val minNextBid: Double,
    val reserveMet: Boolean = false,
    /** "upcoming" | "live" | "ended" - derived by the server from its own clock. */
    val status: String,
    val highestBidderId: Int? = null,
    val winnerId: Int? = null,
    val winningBid: Double? = null,
    val startTime: String,
    val endTime: String,
    /** Server clock at response time; used to correct for a wrong device clock. */
    val serverTime: String,
    val car: CarDto? = null,
    val recentBids: List<BidDto> = emptyList(),
) {
    /** What to show as "current price": the starting bid until someone bids. */
    val displayPrice: Double get() = if (bidCount == 0) startingPrice else currentBid
    val isLive get() = status == "live"
    val isUpcoming get() = status == "upcoming"
    val isEnded get() = status == "ended"
}

@Serializable
data class AuctionListResponse(val auctions: List<AuctionDto>, val total: Int = 0, val page: Int = 1, val limit: Int = 20)

@Serializable data class PlaceBidRequest(val amount: Long)
@Serializable data class PlaceBidResponse(val bid: BidDto, val auction: AuctionDto)

@Serializable data class WatchlistItemDto(val id: Int, val auctionId: Int, val auction: AuctionDto? = null)

@Serializable
data class PaymentInfoDto(val status: String = "not_available", val message: String = "Payment integration coming soon.")

@Serializable
data class MyBidDto(
    val auction: AuctionDto,
    val myHighestBid: Double,
    val myBidCount: Int,
    val lastBidAt: String,
    /** leading | outbid | won | lost | ended_reserve_not_met */
    val state: String,
    val payment: PaymentInfoDto? = null,
)

@Serializable data class ErrorBody(val error: String? = null, val message: String? = null, val code: String? = null)

/** Filters shared by Explore and Search & Filters. All fields are applied together (AND). */
data class AuctionFilters(
    val query: String = "",
    val makes: Set<String> = emptySet(),
    val model: String = "",
    val location: String = "",
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val priceMin: Long? = null,
    val priceMax: Long? = null,
    val statuses: Set<String> = emptySet(),   // empty = all
    val fuelTypes: Set<String> = emptySet(),
    val sort: String = "ending_soon",
) {
    val activeCount: Int
        get() = listOf(
            query.isNotBlank(), makes.isNotEmpty(), model.isNotBlank(), location.isNotBlank(),
            yearMin != null || yearMax != null, priceMin != null || priceMax != null,
            statuses.isNotEmpty(), fuelTypes.isNotEmpty(),
        ).count { it }

    fun toQuery(): Map<String, String> = buildMap {
        if (query.isNotBlank()) put("q", query.trim())
        if (makes.isNotEmpty()) put("make", makes.joinToString(","))
        if (model.isNotBlank()) put("model", model.trim())
        if (location.isNotBlank()) put("location", location.trim())
        yearMin?.let { put("yearMin", it.toString()) }
        yearMax?.let { put("yearMax", it.toString()) }
        priceMin?.let { put("priceMin", it.toString()) }
        priceMax?.let { put("priceMax", it.toString()) }
        if (statuses.isNotEmpty()) put("status", statuses.joinToString(","))
        if (fuelTypes.isNotEmpty()) put("fuelType", fuelTypes.joinToString(","))
        put("sort", sort)
        put("limit", "100")
    }
}

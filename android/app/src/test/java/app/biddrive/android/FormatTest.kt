package app.biddrive.android

import app.biddrive.android.data.AuctionDto
import app.biddrive.android.data.AuctionFilters
import app.biddrive.android.util.formatCountdown
import app.biddrive.android.util.formatInr
import app.biddrive.android.util.formatInrShort
import app.biddrive.android.util.isValidEmail
import app.biddrive.android.util.passwordProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test fun indianGrouping() {
        assertEquals("₹999", formatInr(999L))
        assertEquals("₹1,000", formatInr(1000L))
        assertEquals("₹48,75,000", formatInr(4875000.0))
        assertEquals("₹1,64,00,000", formatInr(16400000L))
    }
    @Test fun shortForm() {
        assertEquals("₹48.75 L", formatInrShort(4875000.0))
        assertEquals("₹1.64 Cr", formatInrShort(16400000.0))
    }
    @Test fun countdown() {
        assertEquals("01h 02m 03s", formatCountdown(3_723_000))
        assertEquals("02m 05s", formatCountdown(125_000))
        assertEquals("00s", formatCountdown(-1))
    }
    @Test fun validation() {
        assertTrue(isValidEmail("a@b.co")); assertFalse(isValidEmail("a@b")); assertFalse(isValidEmail("a b@c.com"))
        assertNull(passwordProblem("abc12345")); assertNotNull(passwordProblem("abcdefgh")); assertNotNull(passwordProblem("a1"))
    }
}

class ModelsTest {
    private fun auction(bids: Int) = AuctionDto(
        id = 1, carId = 1, startingPrice = 100.0, currentBid = if (bids == 0) 0.0 else 150.0, bidIncrement = 10.0, bidCount = bids,
        minNextBid = 160.0, status = "live", startTime = "2026-01-01T00:00:00Z", endTime = "2026-01-02T00:00:00Z", serverTime = "2026-01-01T12:00:00Z",
    )

    @Test fun displayPriceUsesStartingBidUntilFirstBid() {
        assertEquals(100.0, auction(0).displayPrice, 0.0)
        assertEquals(150.0, auction(3).displayPrice, 0.0)
    }
    @Test fun filtersAreCombinedIntoOneQuery() {
        val f = AuctionFilters(makes = setOf("BMW", "Audi"), yearMin = 2021, priceMax = 5_000_000, statuses = setOf("live"), query = " x5 ")
        val q = f.toQuery()
        assertEquals("BMW,Audi", q["make"]); assertEquals("2021", q["yearMin"]); assertEquals("5000000", q["priceMax"])
        assertEquals("live", q["status"]); assertEquals("x5", q["q"])
        assertEquals(4, f.activeCount)
        assertEquals(0, AuctionFilters().activeCount)
    }
}

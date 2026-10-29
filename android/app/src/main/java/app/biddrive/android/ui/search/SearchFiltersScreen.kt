@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.biddrive.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.biddrive.android.data.ApiResult
import app.biddrive.android.data.AuctionFilters
import app.biddrive.android.data.AuctionRepository
import app.biddrive.android.data.FilterStore
import app.biddrive.android.ui.appViewModel
import app.biddrive.android.ui.components.BdTextField
import app.biddrive.android.ui.components.BdTopBar
import app.biddrive.android.ui.components.PrimaryButton
import app.biddrive.android.ui.components.SecondaryButton
import app.biddrive.android.ui.components.SectionLabel
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.util.fuelLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: AuctionRepository, private val store: FilterStore) : ViewModel() {
    private val _draft = MutableStateFlow(store.filters.value)
    val draft: StateFlow<AuctionFilters> = _draft.asStateFlow()

    /** Option lists come from the real catalogue (makes/locations that actually exist). */
    private val _makes = MutableStateFlow<List<String>>(emptyList())
    val makes: StateFlow<List<String>> = _makes.asStateFlow()
    private val _locations = MutableStateFlow<List<String>>(emptyList())
    val locations: StateFlow<List<String>> = _locations.asStateFlow()
    private val _fuels = MutableStateFlow<List<String>>(emptyList())
    val fuels: StateFlow<List<String>> = _fuels.asStateFlow()
    private val _optionsError = MutableStateFlow<String?>(null)
    val optionsError: StateFlow<String?> = _optionsError.asStateFlow()

    init { loadOptions() }

    fun loadOptions() {
        viewModelScope.launch {
            when (val r = repo.list(AuctionFilters(sort = "newest"))) {
                is ApiResult.Success -> {
                    val cars = r.value.mapNotNull { it.car }
                    _makes.value = cars.map { it.brand }.distinct().sorted()
                    _locations.value = cars.map { it.location }.distinct().sorted()
                    _fuels.value = cars.map { it.fuelType }.filter { it.isNotBlank() }.distinct().sorted()
                    _optionsError.value = null
                }
                is ApiResult.Failure -> _optionsError.value = r.message
            }
        }
    }

    fun edit(block: (AuctionFilters) -> AuctionFilters) = _draft.update(block)
    fun resetDraft() { _draft.value = AuctionFilters(sort = _draft.value.sort) }
    fun apply() { store.set(_draft.value) }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
fun SearchFiltersScreen(onBack: () -> Unit, onApplied: () -> Unit) {
    val vm = appViewModel { c, _ -> SearchViewModel(c.auctions, c.filters) }
    val draft by vm.draft.collectAsState()
    val makes by vm.makes.collectAsState()
    val locations by vm.locations.collectAsState()
    val fuels by vm.fuels.collectAsState()
    val optionsError by vm.optionsError.collectAsState()

    // Raw text for numeric fields, so partially typed numbers are not rewritten under the cursor.
    var yearMin by remember { mutableStateOf(draft.yearMin?.toString() ?: "") }
    var yearMax by remember { mutableStateOf(draft.yearMax?.toString() ?: "") }
    var priceMin by remember { mutableStateOf(draft.priceMin?.toString() ?: "") }
    var priceMax by remember { mutableStateOf(draft.priceMax?.toString() ?: "") }

    val yMin = yearMin.toIntOrNull(); val yMax = yearMax.toIntOrNull()
    val pMin = priceMin.toLongOrNull(); val pMax = priceMax.toLongOrNull()
    val yearError = if (yMin != null && yMax != null && yMin > yMax) "Min year is after max year" else null
    val priceError = if (pMin != null && pMax != null && pMin > pMax) "Min price is above max price" else null
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = Bd.Amber, selectedLabelColor = Bd.OnAmber, labelColor = Bd.OnSurfaceVariant)

    Column(Modifier.fillMaxSize().background(Bd.Background)) {
        BdTopBar("Search & Filters", onBack = onBack) {
            TextButton(onClick = { vm.resetDraft(); yearMin = ""; yearMax = ""; priceMin = ""; priceMax = "" }) { Text("Reset all", color = Bd.Amber) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BdTextField(draft.query, { v -> vm.edit { it.copy(query = v) } }, "Search make, model, colour or city", leadingIcon = Icons.Filled.Search)
            optionsError?.let { Text("Couldn't load filter options: $it", color = Bd.Red, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Make${if (draft.makes.isNotEmpty()) " • ${draft.makes.size} selected" else ""}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    makes.forEach { m -> FilterChip(m in draft.makes, { vm.edit { it.copy(makes = it.makes.toggle(m)) } }, { Text(m) }, colors = chipColors) }
                }
                BdTextField(draft.model, { v -> vm.edit { it.copy(model = v) } }, "Model contains")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Location")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    locations.forEach { l -> FilterChip(draft.location == l, { vm.edit { it.copy(location = if (it.location == l) "" else l) } }, { Text(l) }, colors = chipColors) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Model year")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BdTextField(yearMin, { v -> yearMin = v.filter(Char::isDigit).take(4); vm.edit { it.copy(yearMin = yearMin.toIntOrNull()) } }, "From", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    BdTextField(yearMax, { v -> yearMax = v.filter(Char::isDigit).take(4); vm.edit { it.copy(yearMax = yearMax.toIntOrNull()) } }, "To", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
                yearError?.let { Text(it, color = Bd.Red, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Price range (₹)")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BdTextField(priceMin, { v -> priceMin = v.filter(Char::isDigit).take(11); vm.edit { it.copy(priceMin = priceMin.toLongOrNull()) } }, "Min", Modifier.weight(1f), keyboardType = KeyboardType.Number, prefix = "₹ ")
                    BdTextField(priceMax, { v -> priceMax = v.filter(Char::isDigit).take(11); vm.edit { it.copy(priceMax = priceMax.toLongOrNull()) } }, "Max", Modifier.weight(1f), keyboardType = KeyboardType.Number, prefix = "₹ ")
                }
                priceError?.let { Text(it, color = Bd.Red, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                Text("Matches the current bid, or the starting bid if there are no bids yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Bd.Outline)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Auction status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("live" to "Live", "upcoming" to "Upcoming", "ended" to "Ended").forEach { (k, label) ->
                        FilterChip(k in draft.statuses, { vm.edit { it.copy(statuses = it.statuses.toggle(k)) } }, { Text(label) }, colors = chipColors)
                    }
                }
            }
            if (fuels.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Fuel type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fuels.forEach { f -> FilterChip(f in draft.fuelTypes, { vm.edit { it.copy(fuelTypes = it.fuelTypes.toggle(f)) } }, { Text(fuelLabel(f)) }, colors = chipColors) }
                }
            }
            Column { androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp)) }
        }
        Row(Modifier.fillMaxWidth().background(Bd.SurfaceLow).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onBack, Modifier.weight(1f))
            PrimaryButton("Apply filters", { vm.apply(); onApplied() }, Modifier.weight(2f), enabled = yearError == null && priceError == null)
        }
    }
}

@file:Suppress("MagicNumber")

package com.tonapps.wallet.features.brotherhood.city

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.h3.H3Helper
import com.tonapps.brotherhood.h3.LatLngPoint
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringTail
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.db.WatchedLocationEntity
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.CityNetworkRepository
import com.tonapps.wallet.data.brotherhood.repo.HydratedCityLocation
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import org.ton.cell.CellBuilder
import java.math.BigInteger

data class H3VisualizerCell(
    val h3Cell: String,
    val label: String,
    val center: LatLngPoint,
    val countryCode: Int,
    val isPreset: Boolean,
)

data class CityPollItem(
    val pollId: Long,
    val h3Cell: String,
    val title: String,
    val description: String,
    val proposerAddress: String,
    val yesVotes: Long,
    val noVotes: Long,
    val userVote: Boolean? = null,
    val ended: Boolean = false,
)

object H3Res1Grid {
    private const val TOTAL_RES1_CELL_COUNT = 84
    private const val GRID_LAT_ROWS = 7
    private const val GRID_LNG_COLS = 12
    private const val LAT_MIN_DEG = -60.0
    private const val LAT_STEP_DEG = 20.0
    private const val LNG_MIN_DEG = -165.0
    private const val LNG_STEP_DEG = 30.0
    private const val DEFAULT_COUNTRY_CODE = 356

    val ALL_RES1_CELLS: List<H3VisualizerCell> by lazy {
        val byCell = LinkedHashMap<String, H3VisualizerCell>()
        for (preset in H3Helper.PRESET_REGIONS) {
            byCell[preset.h3Cell] = H3VisualizerCell(
                h3Cell = preset.h3Cell,
                label = preset.name,
                center = preset.center,
                countryCode = preset.countryCode,
                isPreset = true,
            )
        }
        for (row in 0 until GRID_LAT_ROWS) {
            val lat = LAT_MIN_DEG + row * LAT_STEP_DEG
            for (col in 0 until GRID_LNG_COLS) {
                if (byCell.size >= TOTAL_RES1_CELL_COUNT) {
                    break
                }
                val lng = LNG_MIN_DEG + col * LNG_STEP_DEG
                val syntheticIndex = row * GRID_LNG_COLS + col
                val rawHeader: ULong = (1UL shl 59) or
                    (1UL shl 52) or
                    ((syntheticIndex % 122).toULong() shl 45) or
                    ((syntheticIndex % 7).toULong() shl 42) or
                    0x3FFFFFFFFFFUL
                val cellHex = rawHeader.toString(16).lowercase()
                if (!byCell.containsKey(cellHex)) {
                    byCell[cellHex] = H3VisualizerCell(
                        h3Cell = cellHex,
                        label = "Res-1 Sector #${byCell.size + 1}",
                        center = LatLngPoint(latitude = lat, longitude = lng),
                        countryCode = DEFAULT_COUNTRY_CODE,
                        isPreset = false,
                    )
                }
            }
        }
        byCell.values.take(TOTAL_RES1_CELL_COUNT)
    }

    fun latLngToRes1H3Cell(lat: Double, lng: Double): String {
        return H3Helper.latLngToRes1H3Cell(lat, lng)
    }
}

class CityNetworkFeature(
    private val cityNetworkRepository: CityNetworkRepository,
    private val brotherhoodRepository: BrotherhoodRepository,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    private val _watchedLocations = MutableStateFlow<List<WatchedLocationEntity>>(emptyList())
    val watchedLocations: StateFlow<List<WatchedLocationEntity>> = _watchedLocations.asStateFlow()

    private val _selectedH3Cell = MutableStateFlow(DEFAULT_H3_CELL)
    val selectedH3Cell: StateFlow<String> = _selectedH3Cell.asStateFlow()

    private val _inspectedLocation = MutableStateFlow<HydratedCityLocation?>(null)
    val inspectedLocation: StateFlow<HydratedCityLocation?> = _inspectedLocation.asStateFlow()

    private val _cityPolls = MutableStateFlow(defaultCityPolls(DEFAULT_H3_CELL))
    val cityPolls: StateFlow<List<CityPollItem>> = _cityPolls.asStateFlow()

    private val _h3SearchQuery = MutableStateFlow(DEFAULT_H3_CELL)
    val h3SearchQuery: StateFlow<String> = _h3SearchQuery.asStateFlow()

    private val _showH3VisualizerModal = MutableStateFlow(false)
    val showH3VisualizerModal: StateFlow<Boolean> = _showH3VisualizerModal.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntent = MutableStateFlow<BrotherhoodTransferIntent?>(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> = _pendingIntent.asStateFlow()

    init {
        bgScope.launch {
            cityNetworkRepository.observeWatchedLocations().collectLatest { entities ->
                _watchedLocations.value = entities
            }
        }
        bgScope.launch {
            brotherhoodRepository.accountSnapshot.collectLatest { snapshot ->
                val profileCell = snapshot?.fiWalletStore?.profile?.h3Cell
                val normalized = H3Helper.normalizeH3Cell(profileCell)
                if (H3Helper.isValidH3Cell(normalized)) {
                    _selectedH3Cell.value = normalized
                    _h3SearchQuery.value = normalized
                }
                syncWatchedAndCurrentCell(forceRefresh = false)
            }
        }
        inspectCurrentCell(forceRefresh = false)
    }

    fun updateSearchQuery(query: String) {
        _h3SearchQuery.value = query
    }

    fun setShowH3VisualizerModal(visible: Boolean) {
        _showH3VisualizerModal.value = visible
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun consumePendingIntent() {
        _pendingIntent.value = null
    }

    fun selectH3Cell(h3Cell: String) {
        val clean = H3Helper.normalizeH3Cell(h3Cell)
        if (!H3Helper.isValidH3Cell(clean)) {
            _statusMessage.value = "Invalid H3 cell format. Expected 15-character hex index (e.g. $DEFAULT_H3_CELL)."
            return
        }
        _selectedH3Cell.value = clean
        _h3SearchQuery.value = clean
        val presetName = H3Helper.PRESET_REGIONS.firstOrNull { region -> region.h3Cell == clean }?.name
        _statusMessage.value = if (presetName != null) {
            "Selected $presetName ($clean)"
        } else {
            "Selected H3 cell $clean"
        }
        inspectCurrentCell(forceRefresh = true)
    }

    fun inspectCurrentCell(forceRefresh: Boolean = true) {
        val candidate = H3Helper.normalizeH3Cell(
            _h3SearchQuery.value.ifBlank { _selectedH3Cell.value }
        )
        val targetCell = if (H3Helper.isValidH3Cell(candidate)) {
            candidate
        } else {
            _selectedH3Cell.value
        }
        _selectedH3Cell.value = targetCell
        _h3SearchQuery.value = targetCell

        bgScope.launch {
            runCatching {
                cityNetworkRepository.addWatchedLocation(h3Cell = targetCell)
                syncWatchedAndCurrentCell(forceRefresh = forceRefresh)
            }.onFailure { error ->
                verifyError(error)
                _statusMessage.value = "Unable to hydrate Location contract for $targetCell: ${error.message ?: "Network error"}"
            }
        }
    }

    fun autoDetectFromLatLng(lat: Double, lng: Double) {
        val cell = H3Res1Grid.latLngToRes1H3Cell(lat, lng)
        val matchedPreset = H3Helper.PRESET_REGIONS.firstOrNull { region -> region.h3Cell == cell }
        _selectedH3Cell.value = cell
        _h3SearchQuery.value = cell
        val regionLabel = matchedPreset?.name ?: "H3 Res-1 Sector"
        _statusMessage.value = "Auto-detected $regionLabel ($cell) from GPS ($lat, $lng)"
        inspectCurrentCell(forceRefresh = true)
    }

    fun relocatePrimaryResidence(
        newH3Cell: String = _selectedH3Cell.value,
        countryCode: Int = DEFAULT_COUNTRY_CODE,
    ) {
        val clean = H3Helper.normalizeH3Cell(newH3Cell)
        if (!H3Helper.isValidH3Cell(clean)) {
            _statusMessage.value = "Enter a valid 15-character H3 resolution-1 cell index before relocating."
            return
        }
        val fiWalletAddress = resolveActiveFiWalletAddress()
        if (fiWalletAddress == null) {
            _statusMessage.value = "Connect an active TON wallet to update your primary H3 residence."
            return
        }
        val effectiveCountry = H3Helper.PRESET_REGIONS
            .firstOrNull { region -> region.h3Cell == clean }
            ?.countryCode
            ?: countryCode
        val intent = cityNetworkRepository.buildUpdateResidenceIntent(
            fiWalletAddress = fiWalletAddress,
            h3Cell = clean,
            countryCode = effectiveCountry,
        )
        _selectedH3Cell.value = clean
        _h3SearchQuery.value = clean
        _pendingIntent.value = intent
        _statusMessage.value = "Prepared primary residence relocation to $clean (Country #$effectiveCountry)"
    }

    fun submitCityProposal(title: String, description: String) {
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        if (cleanTitle.isEmpty()) {
            _statusMessage.value = "Proposal title is required."
            return
        }
        val activeCell = _selectedH3Cell.value
        val nextPollId = (_cityPolls.value.maxOfOrNull { poll -> poll.pollId } ?: INITIAL_CITY_POLL_ID) + 1L
        val fiWalletAddress = resolveActiveFiWalletAddress()
        val proposer = fiWalletAddress ?: BrotherhoodConfig.DAO_PROXY_ADDRESS_STR

        val newPoll = CityPollItem(
            pollId = nextPollId,
            h3Cell = activeCell,
            title = cleanTitle,
            description = cleanDesc.ifEmpty { "Regional H3 proposal for cell $activeCell" },
            proposerAddress = proposer,
            yesVotes = 1L,
            noVotes = 0L,
            userVote = true,
            ended = false,
        )
        _cityPolls.update { existing -> listOf(newPoll) + existing }

        if (fiWalletAddress != null) {
            val targetMsgCell = CellBuilder.createCell {
                storeUInt(0, 32)
                storeStringTail("CITY:$activeCell:$cleanTitle:$cleanDesc")
            }
            _pendingIntent.value = BrotherhoodTransferIntent(
                title = "Submit City Proposal #$nextPollId ($activeCell)",
                subtitle = cleanTitle,
                messages = listOf(
                    BrotherhoodOutgoingMessage(
                        destination = AddrStd(fiWalletAddress),
                        amountNano = BigInteger.valueOf(BrotherhoodConfig.SUBMIT_PROPOSAL_GAS_NANO),
                        payloadCell = BrotherhoodMessages.buildActSubmitProposal(targetMsg = targetMsgCell),
                    )
                ),
            )
            _statusMessage.value = "Prepared City Proposal #$nextPollId for H3 cell $activeCell"
        } else {
            _statusMessage.value = "Added local City Proposal #$nextPollId for H3 cell $activeCell"
        }
    }

    fun voteOnCityPoll(pollId: Long, support: Boolean) {
        val currentPoll = _cityPolls.value.firstOrNull { poll -> poll.pollId == pollId }
        val oldVote = currentPoll?.userVote
        _cityPolls.update { polls ->
            polls.map { poll ->
                if (poll.pollId != pollId) {
                    poll
                } else {
                    val adjustedYes = when {
                        support && poll.userVote != true -> poll.yesVotes + 1L
                        !support && poll.userVote == true -> (poll.yesVotes - 1L).coerceAtLeast(0L)
                        else -> poll.yesVotes
                    }
                    val adjustedNo = when {
                        !support && poll.userVote != false -> poll.noVotes + 1L
                        support && poll.userVote == false -> (poll.noVotes - 1L).coerceAtLeast(0L)
                        else -> poll.noVotes
                    }
                    poll.copy(
                        yesVotes = adjustedYes,
                        noVotes = adjustedNo,
                        userVote = support,
                    )
                }
            }
        }

        val fiWalletAddress = resolveActiveFiWalletAddress()
        val voteLabel = if (support) {
            "FOR"
        } else {
            "AGAINST"
        }
        if (fiWalletAddress != null) {
            val pollAddress = ShardedAddressDerivation.derivePoll(pollId).address
            _pendingIntent.value = BrotherhoodTransferIntent(
                title = "Vote $voteLabel on City Poll #$pollId",
                subtitle = currentPoll?.title ?: "Regional H3 City Poll #$pollId",
                messages = listOf(
                    BrotherhoodOutgoingMessage(
                        destination = AddrStd(fiWalletAddress),
                        amountNano = BigInteger.valueOf(BrotherhoodConfig.VOTE_PROPOSAL_GAS_NANO),
                        payloadCell = BrotherhoodMessages.buildActVoteProposal(
                            pollAddress = pollAddress,
                            proposalId = pollId,
                            vote = support,
                            oldVote = oldVote,
                        ),
                    )
                ),
            )
            _statusMessage.value = "Prepared $voteLabel vote on City Poll #$pollId"
        } else {
            _statusMessage.value = "Recorded $voteLabel vote on City Poll #$pollId"
        }
    }

    private suspend fun syncWatchedAndCurrentCell(forceRefresh: Boolean) {
        val snapshot = brotherhoodRepository.accountSnapshot.value
        val primaryH3 = snapshot?.fiWalletStore?.profile?.h3Cell ?: _selectedH3Cell.value
        val primaryCountry = snapshot?.fiWalletStore?.profile?.country ?: DEFAULT_COUNTRY_CODE
        val locations = cityNetworkRepository.syncLocations(
            primaryResidenceH3Cell = primaryH3,
            primaryCountryCode = primaryCountry,
            forceRefresh = forceRefresh,
        )
        val activeCell = _selectedH3Cell.value
        val matched = locations.firstOrNull { loc -> loc.h3Cell == activeCell }
            ?: locations.firstOrNull { loc -> loc.isPrimaryResidence }
            ?: locations.firstOrNull()
        if (matched != null) {
            _inspectedLocation.value = matched
        }
    }

    private fun resolveActiveFiWalletAddress(): String? {
        val fromSnapshot = brotherhoodRepository.accountSnapshot.value?.derivedFiWalletAddressRaw
        if (!fromSnapshot.isNullOrBlank() && !BrotherhoodConfig.isZeroAddress(fromSnapshot)) {
            return fromSnapshot
        }
        val ownerWallet = sessionHolder.walletAddressFlow.value
        if (ownerWallet.isNullOrBlank()) {
            return null
        }
        return runCatching {
            brotherhoodRepository.deriveFiWalletAddress(ownerWallet).toAccountId()
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_H3_CELL = "813d3ffffffffff"
        const val DEFAULT_COUNTRY_CODE = 356
        private const val INITIAL_CITY_POLL_ID = 100L

        private fun defaultCityPolls(h3Cell: String): List<CityPollItem> {
            return listOf(
                CityPollItem(
                    pollId = 101L,
                    h3Cell = h3Cell,
                    title = "Regional Mesh Relay & Node Grant",
                    description = "Allocate regional H3 treasury funds to operate local TON archive & mesh relay infrastructure.",
                    proposerAddress = BrotherhoodConfig.DAO_PROXY_ADDRESS_STR,
                    yesVotes = 18L,
                    noVotes = 2L,
                ),
                CityPollItem(
                    pollId = 102L,
                    h3Cell = h3Cell,
                    title = "Monthly Citizen Onboarding Meetups",
                    description = "Sponsor verified in-person circle vouching & onboarding sessions within H3 cell $h3Cell.",
                    proposerAddress = BrotherhoodConfig.FI_ADDRESS_STR,
                    yesVotes = 11L,
                    noVotes = 1L,
                ),
            )
        }
    }
}

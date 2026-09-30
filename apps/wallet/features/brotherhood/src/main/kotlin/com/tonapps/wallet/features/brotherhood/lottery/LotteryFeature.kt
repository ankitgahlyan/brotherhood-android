package com.tonapps.wallet.features.brotherhood.lottery

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.store.LotteryStorage
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.LotteryRepository
import com.tonapps.wallet.data.brotherhood.repo.LotterySnapshot
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigInteger

typealias LotteryRoundSnapshot = LotterySnapshot

val LotteryStorage.winners: Map<Long, String>
    get() = participants.mapIndexed { index, participant ->
        (index + 1).toLong() to participant
    }.toMap()

class LotteryFeature(
    private val lotteryRepository: LotteryRepository,
    private val brotherhoodRepository: BrotherhoodRepository,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    private val _selectedRoundId = MutableStateFlow(DEFAULT_ROUND_ID)
    val selectedRoundId: StateFlow<Long> = _selectedRoundId.asStateFlow()

    private val _lotteryState = MutableStateFlow<LotteryRoundSnapshot?>(null)
    val lotteryState: StateFlow<LotteryRoundSnapshot?> = _lotteryState.asStateFlow()

    private val _ticketCountInput = MutableStateFlow(DEFAULT_TICKET_COUNT)
    val ticketCountInput: StateFlow<String> = _ticketCountInput.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntent = MutableStateFlow<BrotherhoodTransferIntent?>(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> = _pendingIntent.asStateFlow()

    init {
        bgScope.launch {
            lotteryRepository.snapshot.collectLatest { snap ->
                if (snap != null) {
                    _lotteryState.value = snap
                }
            }
        }
        bgScope.launch {
            sessionHolder.walletAddressFlow.collectLatest {
                refreshLottery(forceRefresh = false)
            }
        }
    }

    fun selectRound(roundId: Long) {
        val normalizedRound = roundId.coerceAtLeast(DEFAULT_ROUND_ID)
        _selectedRoundId.value = normalizedRound
        refreshLottery(forceRefresh = false)
    }

    fun updateTicketCountInput(input: String) {
        _ticketCountInput.value = input
    }

    fun consumePendingIntent() {
        _pendingIntent.value = null
    }

    fun refreshLottery(forceRefresh: Boolean = false) {
        bgScope.launch {
            try {
                val updated = lotteryRepository.refreshLottery(forceRefresh = forceRefresh)
                _lotteryState.value = updated
                val roundId = _selectedRoundId.value
                _statusMessage.value = if (updated.isDeployed) {
                    val potStr = BrotherhoodUiFormatters.formatNanoAmount(
                        updated.storage?.prizePool,
                        BrotherhoodConfig.FI_SYMBOL,
                    )
                    "Round #$roundId active • Pot: $potStr (${updated.storage?.participantCount ?: 0} entries)"
                } else {
                    "Sharded Lottery Round #$roundId (${BrotherhoodUiFormatters.shortAddress(updated.lotteryAddress)}) ready"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to refresh Lottery contract state"
            }
        }
    }

    fun buyTickets() {
        bgScope.launch {
            try {
                val count = _ticketCountInput.value.trim().toLongOrNull()
                if (count == null || count <= 0L) {
                    _statusMessage.value = "Enter a valid ticket count (1 or more)"
                    return@launch
                }
                val cappedCount = count.coerceAtMost(MAX_TICKETS_PER_TX)
                val fiWalletAddress = resolveUserFiWalletAddress()
                val singleTicketFiNano = BigInteger.valueOf(BrotherhoodConfig.LOTTERY_TICKET_PRICE_FI_NANO)
                val totalFiNano = singleTicketFiNano.multiply(BigInteger.valueOf(cappedCount))
                val intent = lotteryRepository.buildBuyLotteryTicketIntent(
                    userFiWalletAddress = fiWalletAddress,
                    ticketPriceFiNano = totalFiNano,
                )
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title} ($cappedCount ticket(s) for Round #${_selectedRoundId.value})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build lottery ticket purchase intent"
            }
        }
    }

    fun triggerDrawWinner() {
        bgScope.launch {
            try {
                val intent = lotteryRepository.buildDrawWinnersIntent()
                _pendingIntent.value = intent
                _statusMessage.value = "Prepared: ${intent.title} (Round #${_selectedRoundId.value})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Failed to build draw winner intent"
            }
        }
    }

    private fun resolveUserFiWalletAddress(): String {
        val cachedFiWallet = brotherhoodRepository.accountSnapshot.value?.derivedFiWalletAddressRaw
        if (!cachedFiWallet.isNullOrBlank()) {
            return cachedFiWallet
        }
        val ownerAddress = sessionHolder.walletAddressFlow.value
            ?: throw IllegalStateException("Connect or select a TON wallet first")
        return brotherhoodRepository.deriveFiWalletAddress(ownerAddress).toAccountId()
    }

    companion object {
        private const val DEFAULT_ROUND_ID = 1L
        private const val DEFAULT_TICKET_COUNT = "1"
        private const val MAX_TICKETS_PER_TX = 100L
    }
}

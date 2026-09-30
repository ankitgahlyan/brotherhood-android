package com.tonapps.wallet.data.brotherhood.autofund

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ton.block.AddrStd
import java.math.BigInteger

data class AutoFundTarget(
    val fiWalletAddress: String,
    val label: String,
    val currentBalanceNano: BigInteger,
    val topUpAmountNano: BigInteger,
    val isSelfFiWallet: Boolean,
)

data class AutoFundNotificationEvent(
    val timestampMs: Long,
    val targets: List<AutoFundTarget>,
    val transferIntent: BrotherhoodTransferIntent,
    val message: String,
)

/**
 * Port of web `useAutoFundFiWallet.ts`.
 *Monitors the user's own `FiWallet` and Circle invitees' `FiWallet`s.
 * When any active `FiWallet` drops below `2.0 TON` (`AUTO_FUND_THRESHOLD_NANO`), and the user's
 * base TON wallet holds enough TON above the `0.5 TON` reserve (`AUTO_FUND_RESERVE_NANO`),
 * constructs a `TopUpTons` (`0x00001164`) transfer intent and emits a user-visible notification.
 */
class AutoFiWalletFunder(
    private val hydrator: AccountStateHydrator,
    private val telemetryRepository: TelemetryRepository,
) {
    private val mutex = Mutex()
    private val lastFundedTimestampByAddress = mutableMapOf<String, Long>()

    private val _notifications = MutableSharedFlow<AutoFundNotificationEvent>(extraBufferCapacity = 16)
    val notifications: SharedFlow<AutoFundNotificationEvent> = _notifications.asSharedFlow()

    suspend fun evaluateAndBuildAutoFund(
        userWalletTonBalanceNano: BigInteger,
        userFiWalletAddress: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AutoFundNotificationEvent? {
        if (!telemetryRepository.isAutoFundEnabled.value) {
            return null
        }
        val normalizedSelfFi = BrotherhoodConfig.normalizeAddress(userFiWalletAddress)
        if (normalizedSelfFi.isBlank() || BrotherhoodConfig.isZeroAddress(normalizedSelfFi)) {
            return null
        }

        val selfHydrated = hydrator.hydrateAddress(
            address = normalizedSelfFi,
            explicitType = KnownContractType.FI_WALLET,
        ) ?: return null

        val selfStore = (selfHydrated.decodedStore as? DecodedContractStore.FiWallet)?.store
        val inviteeFiWallets = selfStore?.maps?.invitedAddresses
            ?.filter { !BrotherhoodConfig.isZeroAddress(it) }
            .orEmpty()

        val allCandidateAddresses = (listOf(normalizedSelfFi) + inviteeFiWallets).distinct()
        val hydratedMap = hydrator.hydrateBatch(
            addresses = allCandidateAddresses,
            explicitTypes = allCandidateAddresses.associateWith { KnownContractType.FI_WALLET },
        )

        val thresholdNano = BigInteger.valueOf(BrotherhoodConfig.AUTO_FUND_THRESHOLD_NANO)
        val topUpNano = BigInteger.valueOf(BrotherhoodConfig.AUTO_FUND_TOP_UP_NANO)
        val reserveNano = BigInteger.valueOf(BrotherhoodConfig.AUTO_FUND_RESERVE_NANO)

        val eligibleTargets = mutableListOf<AutoFundTarget>()
        var remainingSpendableTon = userWalletTonBalanceNano - reserveNano

        mutex.withLock {
            for (addr in allCandidateAddresses) {
                if (remainingSpendableTon < topUpNano) {
                    break
                }
                val lastFundedAt = lastFundedTimestampByAddress[addr] ?: 0L
                if (nowMs - lastFundedAt < COOLDOWN_PER_TARGET_MS) {
                    continue
                }
                val state = hydratedMap[addr] ?: continue
                if (!state.isActive) {
                    continue
                }
                if (state.balanceNano < thresholdNano) {
                    val isSelf = addr == normalizedSelfFi
                    val fwStore = (state.decodedStore as? DecodedContractStore.FiWallet)?.store
                    val username = fwStore?.profile?.username?.takeIf { it.isNotBlank() }
                    val label = when {
                        isSelf -> "Your FiWallet"
                        username != null -> "@$username's FiWallet"
                        else -> "Circle Invitee FiWallet"
                    }
                    eligibleTargets.add(
                        AutoFundTarget(
                            fiWalletAddress = addr,
                            label = label,
                            currentBalanceNano = state.balanceNano,
                            topUpAmountNano = topUpNano,
                            isSelfFiWallet = isSelf,
                        )
                    )
                    remainingSpendableTon -= topUpNano
                }
            }

            if (eligibleTargets.isEmpty()) {
                return null
            }

            for (target in eligibleTargets) {
                lastFundedTimestampByAddress[target.fiWalletAddress] = nowMs
            }
        }

        val topUpCell = BrotherhoodMessages.buildTopUpTons()
        val outgoingMessages = eligibleTargets.map { target ->
            BrotherhoodOutgoingMessage(
                destination = AddrStd(target.fiWalletAddress),
                amountNano = target.topUpAmountNano,
                payloadCell = topUpCell,
            )
        }

        val targetSummary = eligibleTargets.joinToString(", ") { it.label }
        val notificationMessage = "Auto-funding gas (2.0 TON each) for: $targetSummary"
        val intent = BrotherhoodTransferIntent(
            title = "Auto-Fund FiWallet Gas",
            subtitle = notificationMessage,
            messages = outgoingMessages,
            isAutoFund = true,
        )
        val event = AutoFundNotificationEvent(
            timestampMs = nowMs,
            targets = eligibleTargets,
            transferIntent = intent,
            message = notificationMessage,
        )
        _notifications.tryEmit(event)
        return event
    }

    suspend fun clearCooldowns() {
        mutex.withLock {
            lastFundedTimestampByAddress.clear()
        }
    }

    companion object {
        const val COOLDOWN_PER_TARGET_MS = 10 * 60 * 1_000L // 10 minutes
    }
}

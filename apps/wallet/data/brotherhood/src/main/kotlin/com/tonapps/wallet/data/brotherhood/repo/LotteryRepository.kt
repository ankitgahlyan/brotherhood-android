package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.LotteryStorage
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ton.block.AddrStd
import java.math.BigInteger

data class LotterySnapshot(
    val lotteryAddress: String,
    val isDeployed: Boolean,
    val balanceNano: BigInteger,
    val storage: LotteryStorage?,
    val updatedAtMs: Long,
)

class LotteryRepository(
    private val hydrator: AccountStateHydrator,
) {
    private val _snapshot = MutableStateFlow<LotterySnapshot?>(null)
    val snapshot: StateFlow<LotterySnapshot?> = _snapshot.asStateFlow()

    fun deriveLotteryAddress(): AddrStd {
        return ShardedAddressDerivation.deriveLottery().address
    }

    suspend fun refreshLottery(forceRefresh: Boolean = false): LotterySnapshot {
        val lotteryAddr = deriveLotteryAddress().toAccountId()
        val state = hydrator.hydrateAddress(
            address = lotteryAddr,
            explicitType = KnownContractType.LOTTERY,
            forceRefresh = forceRefresh,
        )
        val storage = (state?.decodedStore as? DecodedContractStore.Lottery)?.store
        val snap = LotterySnapshot(
            lotteryAddress = lotteryAddr,
            isDeployed = state?.isActive == true && storage != null,
            balanceNano = state?.balanceNano ?: BigInteger.ZERO,
            storage = storage,
            updatedAtMs = System.currentTimeMillis(),
        )
        _snapshot.value = snap
        return snap
    }

    fun buildBuyLotteryTicketIntent(
        userFiWalletAddress: String,
        ticketPriceFiNano: BigInteger = BigInteger.valueOf(BrotherhoodConfig.LOTTERY_TICKET_PRICE_FI_NANO),
    ): BrotherhoodTransferIntent {
        val lotteryAddress = deriveLotteryAddress()
        val forwardCell = BrotherhoodMessages.buildEnterLottery(
            sender = AddrStd(userFiWalletAddress),
            amountRaw = ticketPriceFiNano,
        )
        return BrotherhoodTransferIntent(
            title = "Buy Lottery Ticket",
            subtitle = "Enter BrotherHood round (100 FI/HD + 0.08 TON fee)",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(userFiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.LOTTERY_ENTRY_FEE_NANO),
                    payloadCell = BrotherhoodMessages.buildAskToTransfer(
                        jettonAmountRaw = ticketPriceFiNano,
                        transferRecipient = lotteryAddress,
                        forwardTonAmountNano = BigInteger.valueOf(50_000_000L),
                        forwardPayload = forwardCell,
                    ),
                )
            ),
        )
    }

    fun buildDrawWinnersIntent(): BrotherhoodTransferIntent {
        val lotteryAddress = deriveLotteryAddress()
        return BrotherhoodTransferIntent(
            title = "Trigger Lottery Draw",
            subtitle = "Select round winners and distribute prizes",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = lotteryAddress,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.SIMPLE_WALLET_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildDrawWinner(),
                )
            ),
        )
    }
}

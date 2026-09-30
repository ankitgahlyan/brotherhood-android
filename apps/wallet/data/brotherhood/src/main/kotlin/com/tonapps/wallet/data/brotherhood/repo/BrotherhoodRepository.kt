package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.FiStore
import com.tonapps.brotherhood.store.FiWalletStore
import com.tonapps.brotherhood.store.FollowingStore
import com.tonapps.brotherhood.store.HoldingStore
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ton.block.AddrStd
import java.math.BigInteger

data class DeferredHoldingItem(
    val holdingAddress: String,
    val store: HoldingStore,
    val balanceNano: BigInteger,
    val isPayerSelf: Boolean,
)

data class BrotherhoodAccountSnapshot(
    val ownerAddressRaw: String,
    val derivedFiWalletAddressRaw: String,
    val isFiWalletDeployed: Boolean,
    val fiWalletTonBalanceNano: BigInteger,
    val fiWalletStore: FiWalletStore?,
    val fiMinterStore: FiStore?,
    val followingStore: FollowingStore?,
    val deferredHoldings: List<DeferredHoldingItem>,
    val updatedAtMs: Long,
)

class BrotherhoodRepository(
    private val dao: BrotherhoodDao,
    private val hydrator: AccountStateHydrator,
) {
    private val _accountSnapshot = MutableStateFlow<BrotherhoodAccountSnapshot?>(null)
    val accountSnapshot: StateFlow<BrotherhoodAccountSnapshot?> = _accountSnapshot.asStateFlow()

    fun observeAddressBook(): Flow<List<AddressBookCacheEntity>> = dao.observeAddressBook()

    suspend fun searchContacts(query: String): List<AddressBookCacheEntity> {
        return dao.searchContacts(query)
    }

    suspend fun resolveUsernameToOwnerOrFiWallet(usernameQuery: String): AddressBookCacheEntity? {
        val clean = usernameQuery.trim().removePrefix("@")
        if (clean.isEmpty()) {
            return null
        }
        return dao.findContactByUsername(clean)
    }

    fun deriveFiWalletAddress(ownerWalletAddress: String): AddrStd {
        val owner = AddrStd(ownerWalletAddress)
        return ShardedAddressDerivation.deriveFiWallet(owner).address
    }

    suspend fun refreshBrotherhoodAccount(
        ownerWalletAddress: String,
        forceRefresh: Boolean = false,
    ): BrotherhoodAccountSnapshot {
        val ownerAddr = AddrStd(ownerWalletAddress)
        val ownerRaw = ownerAddr.toAccountId()
        val fiWalletInit = ShardedAddressDerivation.deriveFiWallet(ownerAddr)
        val fiWalletRaw = fiWalletInit.address.toAccountId()
        val fiMinterRaw = BrotherhoodConfig.FI_ADDRESS_RAW

        val primaryBatch = hydrator.hydrateBatch(
            addresses = listOf(fiMinterRaw, fiWalletRaw),
            explicitTypes = mapOf(
                fiMinterRaw to KnownContractType.FI_MINTER,
                fiWalletRaw to KnownContractType.FI_WALLET,
            ),
            forceRefresh = forceRefresh,
        )

        val fiMinterState = primaryBatch[fiMinterRaw]
        val fiMinterStore = (fiMinterState?.decodedStore as? DecodedContractStore.FiMinter)?.store

        val fiWalletState = primaryBatch[fiWalletRaw]
        val fiWalletStore = (fiWalletState?.decodedStore as? DecodedContractStore.FiWallet)?.store

        // Hydrate social circle & nominators so @username directory is populated
        if (fiWalletStore != null) {
            val peerAddresses = buildSet {
                fiWalletStore.addresses.nomInAddrs.invitor?.let { add(it) }
                fiWalletStore.addresses.nomInAddrs.invitor0?.let { add(it) }
                fiWalletStore.addresses.nomInAddrs.nominee?.let { add(it) }
                addAll(fiWalletStore.maps.invitedAddresses)
                addAll(fiWalletStore.maps.allowances.keys)
                addAll(fiWalletStore.maps.social.votedFor.keys)
            }.filter { !BrotherhoodConfig.isZeroAddress(it) }

            if (peerAddresses.isNotEmpty()) {
                hydrator.hydrateBatch(
                    addresses = peerAddresses,
                    explicitTypes = peerAddresses.associateWith { KnownContractType.FI_WALLET },
                    forceRefresh = false,
                )
            }
        }

        val snapshot = BrotherhoodAccountSnapshot(
            ownerAddressRaw = ownerRaw,
            derivedFiWalletAddressRaw = fiWalletRaw,
            isFiWalletDeployed = fiWalletState?.isActive == true && fiWalletStore != null,
            fiWalletTonBalanceNano = fiWalletState?.balanceNano ?: BigInteger.ZERO,
            fiWalletStore = fiWalletStore,
            fiMinterStore = fiMinterStore,
            followingStore = null,
            deferredHoldings = emptyList(),
            updatedAtMs = System.currentTimeMillis(),
        )
        _accountSnapshot.value = snapshot
        return snapshot
    }

    // =========================================================================
    // 1. Account & Claim Intents
    // =========================================================================

    fun buildMintCitizenRewardIntent(fiWalletAddress: String): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Claim Weekly Citizen Grant",
            subtitle = "Mint periodic FI/HD citizen reward",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.CLAIM_WEEKLY_GRANT_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActClaimWeeklyGrant(),
                )
            ),
        )
    }

    fun buildCloseAccountIntent(fiWalletAddress: String): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Close FiWallet Account",
            subtitle = "Deactivate citizen account",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.SIMPLE_WALLET_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActCloseAccount(),
                )
            ),
        )
    }

    // =========================================================================
    // 2. Invite & Circle Intents
    // =========================================================================

    fun buildNominateInviteIntent(
        fiWalletAddress: String,
        recipientWalletAddress: String,
        username: String,
        h3Cell: String,
        countryCode: Int = 356,
    ): BrotherhoodTransferIntent {
        val target = AddrStd(recipientWalletAddress)
        return BrotherhoodTransferIntent(
            title = "Send Circle Invitation (@${username.trim()})",
            subtitle = "Onboard new citizen into your trusted circle (100 FI + 1.1 TON)",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.INVITE_TOTAL_TON_NANO),
                    payloadCell = BrotherhoodMessages.buildActInvite(
                        transferRecipient = target,
                        username = username.trim(),
                        h3Cell = h3Cell.trim().lowercase(),
                        country = countryCode,
                    ),
                )
            ),
        )
    }

    fun buildDeactivateCircleRingIntent(
        fiWalletAddress: String,
        targetFiWalletAddress: String,
    ): BrotherhoodTransferIntent {
        val target = AddrStd(targetFiWalletAddress)
        return BrotherhoodTransferIntent(
            title = "Deactivate Circle Member",
            subtitle = "Toggle circle ring status",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.SIMPLE_WALLET_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildDeActivateCircleRing(target),
                )
            ),
        )
    }

    // =========================================================================
    // 3. Social Trust & Reputation Voting Intents
    // =========================================================================

    fun buildGiveVoteIntent(
        fiWalletAddress: String,
        targetFiWalletAddress: String,
        votesCount: Int,
    ): BrotherhoodTransferIntent {
        val target = AddrStd(targetFiWalletAddress)
        return BrotherhoodTransferIntent(
            title = "Give Trust Votes ($votesCount)",
            subtitle = "Endorse citizen reputation in BrotherHood",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.VOTE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActVote(target, votesCount),
                )
            ),
        )
    }

    fun buildRetractVoteIntent(
        fiWalletAddress: String,
        targetFiWalletAddress: String,
        votesCount: Int = 1,
    ): BrotherhoodTransferIntent {
        val target = AddrStd(targetFiWalletAddress)
        return BrotherhoodTransferIntent(
            title = "Retract Trust Vote",
            subtitle = "Withdraw reputation endorsement",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.VOTE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActUnvote(target, votesCount),
                )
            ),
        )
    }

    fun buildFollowOrUnfollowIntent(
        fiWalletAddress: String,
        targetFiWalletAddress: String,
        follow: Boolean,
    ): BrotherhoodTransferIntent {
        val selfFi = AddrStd(fiWalletAddress)
        val target = AddrStd(targetFiWalletAddress)
        val cell = if (follow) {
            BrotherhoodMessages.buildFollow(target)
        } else {
            BrotherhoodMessages.buildUnfollow(selfFi, target)
        }
        return BrotherhoodTransferIntent(
            title = if (follow) {
                "Follow Citizen"
            } else {
                "Unfollow Citizen"
            },
            subtitle = "Update on-chain social graph",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = selfFi,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.FOLLOW_ACTION_GAS_NANO),
                    payloadCell = cell,
                )
            ),
        )
    }

    // =========================================================================
    // 4. Credit Limit & Mutual Credit Intents
    // =========================================================================

    fun buildSetCreditNeedIntent(
        fiWalletAddress: String,
        creditNeedNano: BigInteger,
        maturityTimestampSeconds: Long,
        multiplier: Int = 100,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Set Credit Requirement",
            subtitle = "Configure mutual credit capacity and maturity",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.CREDIT_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildSetLoanRequirement(
                        amountRaw = creditNeedNano,
                        maturityDateSeconds = maturityTimestampSeconds,
                        multiplier = multiplier,
                    ),
                )
            ),
        )
    }

    fun buildPayEmiIntent(fiWalletAddress: String): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Pay EMI Installment",
            subtitle = "Pay scheduled mutual credit installment",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.CREDIT_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActPayEmi(),
                )
            ),
        )
    }

    fun buildRepayDebtIntent(
        fiWalletAddress: String,
        amountNano: BigInteger,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Repay Mutual Debt",
            subtitle = "Settle outstanding FI/HD debt",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.CREDIT_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildRepayDebt(amountNano),
                )
            ),
        )
    }

    // =========================================================================
    // 5. Allowances & Gold Coins Intents
    // =========================================================================

    fun buildSetAllowanceIntent(
        fiWalletAddress: String,
        spenderFiWalletAddress: String,
        amountNano: BigInteger,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Set Spending Allowance",
            subtitle = "Authorize delegated FI/HD spending",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.ALLOWANCE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildSetAllowance(AddrStd(spenderFiWalletAddress), amountNano),
                )
            ),
        )
    }

    fun buildSpendAllowanceIntent(
        granterFiWalletAddress: String,
        recipientFiWalletAddress: String,
        amountNano: BigInteger,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Spend Delegated Allowance",
            subtitle = "Transfer FI/HD from authorized allowance",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(granterFiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.ALLOWANCE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildSpendAllowance(
                        amountRaw = amountNano,
                        receiver = AddrStd(recipientFiWalletAddress),
                    ),
                )
            ),
        )
    }

    fun buildTransferGoldIntent(
        fiWalletAddress: String,
        goldCoinsCount: Long,
        recipientFiWalletAddress: String,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Transfer $goldCoinsCount Gold Coins",
            subtitle = "BrotherHood Gold reputation reserve",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.GOLD_TRANSFER_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildAskGoldCoinsTransfer(
                        amount = goldCoinsCount,
                        receiver = AddrStd(recipientFiWalletAddress),
                    ),
                )
            ),
        )
    }

    // =========================================================================
    // 6. Profile, Deferred Escrow & Authority Moderation Intents
    // =========================================================================

    fun buildUpdateProfileIntent(
        fiWalletAddress: String,
        username: String?,
        h3Cell: String?,
        countryCode: Int?,
        nomineeAddress: String? = null,
    ): BrotherhoodTransferIntent {
        val nominee = nomineeAddress?.takeIf { !BrotherhoodConfig.isZeroAddress(it) }?.let { AddrStd(it) }
        return BrotherhoodTransferIntent(
            title = "Update Citizen Profile",
            subtitle = "Update on-chain username, H3 region, or country",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.PROFILE_CHANGE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildChangeProfile(
                        username = username?.takeIf { it.isNotBlank() },
                        h3Cell = h3Cell?.takeIf { it.isNotBlank() },
                        country = countryCode,
                        nominee = nominee,
                    ),
                )
            ),
        )
    }

    fun buildDeferredTransferIntent(
        fiWalletAddress: String,
        payerFiWalletAddress: String,
        amountNano: BigInteger,
    ): BrotherhoodTransferIntent {
        return BrotherhoodTransferIntent(
            title = "Request Deferred Escrow Payment",
            subtitle = "Deploy sharded Holding escrow contract",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DEFERRED_PAYMENT_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildRequestDeferredPayment(
                        payer = AddrStd(payerFiWalletAddress),
                        amountRaw = amountNano,
                    ),
                )
            ),
        )
    }

    fun buildSettleDeferredHoldingIntent(
        fiWalletAddress: String,
        holdingAddress: String,
        claim: Boolean,
    ): BrotherhoodTransferIntent {
        val holdingAddr = AddrStd(holdingAddress)
        val payload = if (claim) {
            BrotherhoodMessages.buildActClaimDeferredPayment(holdingAddr)
        } else {
            BrotherhoodMessages.buildActCancelDeferredPayment(holdingAddr)
        }
        return BrotherhoodTransferIntent(
            title = if (claim) {
                "Claim Deferred Payment"
            } else {
                "Cancel Deferred Payment"
            },
            subtitle = "Settle Holding escrow contract",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DEFERRED_PAYMENT_GAS_NANO),
                    payloadCell = payload,
                )
            ),
        )
    }

    fun buildAuthorityModerationIntent(
        fiWalletAddress: String,
        targetFiWalletAddress: String,
        toggleActive: Boolean = false,
        slashAmountRaw: BigInteger = BigInteger.ZERO,
    ): BrotherhoodTransferIntent {
        val target = AddrStd(targetFiWalletAddress)
        return BrotherhoodTransferIntent(
            title = "Authority Moderation Action",
            subtitle = "Citizen moderation & accountability",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.AUTHORITY_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildActDispatchAuthorityAction(
                        transferRecipient = target,
                        amountRaw = slashAmountRaw,
                        toggleActive = toggleActive,
                    ),
                )
            ),
        )
    }
}

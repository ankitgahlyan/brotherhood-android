package com.tonapps.wallet.features.brotherhood.hub

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.h3.H3Helper
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.brotherhood.autofund.AutoFiWalletFunder
import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodAccountSnapshot
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigInteger

data class AutoFundNotification(
    val id: String,
    val reason: String,
    val targetFiWalletAddress: String,
)

enum class BrotherhoodHubSubTab(val key: String, val title: String) {
    ACCOUNT("account", "Account"),
    NETWORK("network", "Network"),
    CLAIM("claim", "Claim"),
    INVITE("invite", "Invite"),
    VOTE("vote", "Vote"),
    CREDIT("credit", "Credit"),
    ALLOWANCE("allowance", "Allowance"),
    GOLD("gold", "Gold"),
    PROFILE("profile", "Profile"),
    DEFERRED("deferred", "Deferred"),
    AUTHORITY("authority", "Authority");

    companion object {
        val ALL_TABS: List<Pair<String, String>> = entries.map { it.key to it.title }
    }
}

class BrotherhoodHubFeature(
    private val brotherhoodRepository: BrotherhoodRepository,
    private val autoFiWalletFunder: AutoFiWalletFunder,
    private val sessionHolder: BrotherhoodWalletSessionHolder,
) : AsyncViewModel() {

    val selectedSubTab: StateFlow<String> field = MutableStateFlow(BrotherhoodHubSubTab.ACCOUNT.key)
    val snapshot: StateFlow<BrotherhoodAccountSnapshot?> field = MutableStateFlow(null)
    val addressBook: StateFlow<List<AddressBookCacheEntity>> field = MutableStateFlow(emptyList())
    val autoFundNotifications: StateFlow<List<AutoFundNotification>> field = MutableStateFlow(emptyList())
    val isLoading: StateFlow<Boolean> field = MutableStateFlow(false)
    val statusMessage: StateFlow<String?> field = MutableStateFlow(null)
    val pendingIntent: StateFlow<BrotherhoodTransferIntent?> field = MutableStateFlow(null)

    init {
        bgScope.launch {
            brotherhoodRepository.observeAddressBook().collectLatest { list ->
                addressBook.value = list
            }
        }
        bgScope.launch {
            autoFiWalletFunder.notifications.collectLatest { event ->
                val newItems = event.targets.map { target ->
                    AutoFundNotification(
                        id = "${event.timestampMs}_${target.fiWalletAddress}",
                        reason = target.label,
                        targetFiWalletAddress = target.fiWalletAddress,
                    )
                }
                autoFundNotifications.value = (newItems + autoFundNotifications.value).take(10)
            }
        }
        bgScope.launch {
            sessionHolder.walletAddressFlow.collectLatest { address ->
                if (!address.isNullOrBlank()) {
                    refresh(forceRefresh = false)
                }
            }
        }
    }

    fun selectSubTab(key: String) {
        selectedSubTab.value = key
    }

    fun consumePendingIntent() {
        pendingIntent.value = null
    }

    fun dismissAutoFundNotification(id: String) {
        autoFundNotifications.value = autoFundNotifications.value.filterNot { it.id == id }
    }

    fun refresh(forceRefresh: Boolean = true) {
        val owner = sessionHolder.walletAddressFlow.value ?: return
        bgScope.launch {
            isLoading.value = true
            try {
                val updated = brotherhoodRepository.refreshBrotherhoodAccount(
                    ownerWalletAddress = owner,
                    forceRefresh = forceRefresh,
                )
                snapshot.value = updated
                statusMessage.value = if (updated.isFiWalletDeployed) {
                    "Citizen FiWallet active (${BrotherhoodUiFormatters.shortAddress(updated.derivedFiWalletAddressRaw)})"
                } else {
                    "FiWallet not yet onboarded — request a Circle Invite from an active citizen"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusMessage.value = e.message ?: "Failed to refresh BrotherHood account"
            } finally {
                isLoading.value = false
            }
        }
    }

    // 1. Account & Claim
    fun claimWeeklyGrant() = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildMintCitizenRewardIntent(fiWallet)
    }

    fun closeAccount() = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildCloseAccountIntent(fiWallet)
    }

    // 2. Invite & Circle
    fun sendCircleInvite(
        recipientWalletAddress: String,
        username: String,
        h3Cell: String,
        countryCode: Int = 356,
    ) = dispatchIntent { fiWallet ->
        val validH3 = h3Cell.trim().ifBlank { H3Helper.PRESET_REGIONS.first().h3Cell }
        brotherhoodRepository.buildNominateInviteIntent(
            fiWalletAddress = fiWallet,
            recipientWalletAddress = recipientWalletAddress.trim(),
            username = username.trim().removePrefix("@"),
            h3Cell = validH3,
            countryCode = countryCode,
        )
    }

    fun toggleCircleMember(targetFiWalletAddress: String) = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildDeactivateCircleRingIntent(
            fiWalletAddress = fiWallet,
            targetFiWalletAddress = targetFiWalletAddress.trim(),
        )
    }

    // 3. Vote & Social Follow
    fun giveTrustVote(targetFiWalletAddress: String, votesCount: Int) = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildGiveVoteIntent(
            fiWalletAddress = fiWallet,
            targetFiWalletAddress = targetFiWalletAddress.trim(),
            votesCount = votesCount.coerceIn(1, 15),
        )
    }

    fun retractTrustVote(targetFiWalletAddress: String, votesCount: Int = 1) = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildRetractVoteIntent(
            fiWalletAddress = fiWallet,
            targetFiWalletAddress = targetFiWalletAddress.trim(),
            votesCount = votesCount.coerceAtLeast(1),
        )
    }

    fun followOrUnfollow(targetFiWalletAddress: String, follow: Boolean) = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildFollowOrUnfollowIntent(
            fiWalletAddress = fiWallet,
            targetFiWalletAddress = targetFiWalletAddress.trim(),
            follow = follow,
        )
    }

    // 4. Credit & Debt
    fun setCreditRequirement(
        creditNeedFiStr: String,
        maturityDays: Int,
        multiplier: Int,
    ) = dispatchIntent { fiWallet ->
        val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(creditNeedFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
            ?: throw IllegalArgumentException("Enter a valid credit requirement amount")
        val maturitySeconds = (System.currentTimeMillis() / 1000L) + (maturityDays.coerceAtLeast(1) * 86_400L)
        brotherhoodRepository.buildSetCreditNeedIntent(
            fiWalletAddress = fiWallet,
            creditNeedNano = amountNano,
            maturityTimestampSeconds = maturitySeconds,
            multiplier = multiplier.coerceIn(10, 1000),
        )
    }

    fun payEmiInstallment() = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildPayEmiIntent(fiWallet)
    }

    fun repayMutualDebt(amountFiStr: String) = dispatchIntent { fiWallet ->
        val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(amountFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
            ?: throw IllegalArgumentException("Enter a valid debt repayment amount")
        brotherhoodRepository.buildRepayDebtIntent(fiWallet, amountNano)
    }

    // 5. Allowance & Gold
    fun setSpendingAllowance(spenderFiWalletAddress: String, amountFiStr: String) = dispatchIntent { fiWallet ->
        val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(amountFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
            ?: BigInteger.ZERO
        brotherhoodRepository.buildSetAllowanceIntent(
            fiWalletAddress = fiWallet,
            spenderFiWalletAddress = spenderFiWalletAddress.trim(),
            amountNano = amountNano,
        )
    }

    fun spendDelegatedAllowance(
        granterFiWalletAddress: String,
        recipientFiWalletAddress: String,
        amountFiStr: String,
    ) {
        bgScope.launch {
            try {
                val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(amountFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
                    ?: throw IllegalArgumentException("Enter a valid allowance spend amount")
                val intent = brotherhoodRepository.buildSpendAllowanceIntent(
                    granterFiWalletAddress = granterFiWalletAddress.trim(),
                    recipientFiWalletAddress = recipientFiWalletAddress.trim(),
                    amountNano = amountNano,
                )
                pendingIntent.value = intent
                statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: Exception) {
                statusMessage.value = e.message ?: "Failed to build allowance spend intent"
            }
        }
    }

    fun transferGoldCoins(recipientFiWalletAddress: String, goldCountStr: String) = dispatchIntent { fiWallet ->
        val count = goldCountStr.trim().toLongOrNull()
            ?: throw IllegalArgumentException("Enter a valid Gold coin count")
        brotherhoodRepository.buildTransferGoldIntent(
            fiWalletAddress = fiWallet,
            goldCoinsCount = count,
            recipientFiWalletAddress = recipientFiWalletAddress.trim(),
        )
    }

    // 6. Profile, Deferred Escrow & Authority
    fun updateCitizenProfile(
        username: String,
        h3Cell: String,
        countryCodeStr: String,
        nomineeAddress: String,
    ) = dispatchIntent { fiWallet ->
        val country = countryCodeStr.trim().toIntOrNull()
        brotherhoodRepository.buildUpdateProfileIntent(
            fiWalletAddress = fiWallet,
            username = username.trim().removePrefix("@").ifBlank { null },
            h3Cell = h3Cell.trim().lowercase().ifBlank { null },
            countryCode = country,
            nomineeAddress = nomineeAddress.trim().ifBlank { null },
        )
    }

    fun requestDeferredEscrow(payerFiWalletAddress: String, amountFiStr: String) = dispatchIntent { fiWallet ->
        val amountNano = BrotherhoodUiFormatters.parseDecimalToNano(amountFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
            ?: throw IllegalArgumentException("Enter a valid escrow amount")
        brotherhoodRepository.buildDeferredTransferIntent(
            fiWalletAddress = fiWallet,
            payerFiWalletAddress = payerFiWalletAddress.trim(),
            amountNano = amountNano,
        )
    }

    fun settleDeferredHolding(holdingAddress: String, claim: Boolean) = dispatchIntent { fiWallet ->
        brotherhoodRepository.buildSettleDeferredHoldingIntent(
            fiWalletAddress = fiWallet,
            holdingAddress = holdingAddress.trim(),
            claim = claim,
        )
    }

    fun dispatchAuthorityModeration(
        targetFiWalletAddress: String,
        toggleActive: Boolean,
        slashFiStr: String,
    ) = dispatchIntent { fiWallet ->
        val slashNano = BrotherhoodUiFormatters.parseDecimalToNano(slashFiStr, BrotherhoodConfig.FI_RAW_DECIMALS)
            ?: BigInteger.ZERO
        brotherhoodRepository.buildAuthorityModerationIntent(
            fiWalletAddress = fiWallet,
            targetFiWalletAddress = targetFiWalletAddress.trim(),
            toggleActive = toggleActive,
            slashAmountRaw = slashNano,
        )
    }

    private fun dispatchIntent(builder: (fiWalletAddress: String) -> BrotherhoodTransferIntent) {
        bgScope.launch {
            try {
                val fiWallet = snapshot.value?.derivedFiWalletAddressRaw
                    ?: sessionHolder.walletAddressFlow.value?.let {
                        brotherhoodRepository.deriveFiWalletAddress(it).toAccountId()
                    }
                    ?: throw IllegalStateException("Connect or select a TON wallet first")
                val intent = builder(fiWallet)
                pendingIntent.value = intent
                statusMessage.value = "Prepared: ${intent.title}"
            } catch (e: Exception) {
                statusMessage.value = e.message ?: "Action failed"
            }
        }
    }
}

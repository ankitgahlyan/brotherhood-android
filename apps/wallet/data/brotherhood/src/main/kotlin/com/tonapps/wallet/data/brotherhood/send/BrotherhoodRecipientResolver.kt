package com.tonapps.wallet.data.brotherhood.send

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.repo.BroDnsRepository
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import org.ton.block.AddrStd
import java.math.BigInteger

enum class ResolvedRecipientType {
    DIRECT_ADDRESS,
    CITIZEN_USERNAME,
    BRO_DOMAIN,
}

data class ResolvedBrotherhoodRecipient(
    val originalInput: String,
    val walletAddressRaw: String,
    val displayLabel: String,
    val type: ResolvedRecipientType,
    val fiWalletAddressRaw: String? = null,
)

/**
 * Resolves BrotherHood recipient inputs in the Send flow:
 * 1. `@username` Citizen Contact Book lookup (`address_book_cache` populated by `AccountStateHydrator`)
 * 2. `.bro` DNS domain lookup (deterministic 8-bit sharded `DnsItem` derivation + zero-getter BOC hydration)
 * 3. `SpendAllowance` (`0x00001144`) delegated send intent builder when spending from a granter's allowance.
 */
class BrotherhoodRecipientResolver(
    private val brotherhoodRepository: BrotherhoodRepository,
    private val broDnsRepository: BroDnsRepository,
) {
    suspend fun resolveRecipient(rawInput: String): ResolvedBrotherhoodRecipient? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        // 1. Check @username handle
        if (trimmed.startsWith("@") ||
            (!trimmed.contains(":") && !trimmed.endsWith(".bro", ignoreCase = true) && trimmed.length <= 32)
        ) {
            val entry = brotherhoodRepository.resolveUsernameToOwnerOrFiWallet(trimmed)
            if (entry != null) {
                return ResolvedBrotherhoodRecipient(
                    originalInput = trimmed,
                    walletAddressRaw = entry.ownerAddress,
                    displayLabel = "@${entry.username}",
                    type = ResolvedRecipientType.CITIZEN_USERNAME,
                    fiWalletAddressRaw = entry.fiWalletAddress,
                )
            }
        }

        // 2. Check .bro DNS domain
        if (trimmed.endsWith(".bro", ignoreCase = true)) {
            val resolvedAddress = broDnsRepository.resolveBroDomainToWallet(trimmed)
            if (!resolvedAddress.isNullOrBlank()) {
                return ResolvedBrotherhoodRecipient(
                    originalInput = trimmed,
                    walletAddressRaw = resolvedAddress,
                    displayLabel = trimmed.lowercase(),
                    type = ResolvedRecipientType.BRO_DOMAIN,
                )
            }
        }

        // 3. Direct TON address fallback
        val normalized = BrotherhoodConfig.normalizeAddress(trimmed)
        if (!normalized.isNullOrBlank() && normalized.contains(":") && normalized.length >= 66) {
            return ResolvedBrotherhoodRecipient(
                originalInput = trimmed,
                walletAddressRaw = normalized,
                displayLabel = trimmed,
                type = ResolvedRecipientType.DIRECT_ADDRESS,
            )
        }

        return null
    }

    fun buildDelegatedSpendAllowanceSendIntent(
        granterOwnerWalletAddress: String,
        recipientOwnerWalletAddress: String,
        amountRaw: BigInteger,
    ): BrotherhoodTransferIntent {
        val granterFi = ShardedAddressDerivation.deriveFiWallet(AddrStd(granterOwnerWalletAddress)).address.toAccountId()
        val recipientFi = ShardedAddressDerivation.deriveFiWallet(AddrStd(recipientOwnerWalletAddress)).address.toAccountId()
        return brotherhoodRepository.buildSpendAllowanceIntent(
            granterFiWalletAddress = granterFi,
            recipientFiWalletAddress = recipientFi,
            amountNano = amountRaw,
        )
    }
}

package com.tonapps.wallet.data.brotherhood.token

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownCodeHashes
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao

enum class BrotherhoodAssetCategory {
    NATIVE_TON,
    FOSSFI_MAIN,
    PERSONAL_TOKEN,
    EXCLUDED,
}

data class BrotherhoodTokenDescriptor(
    val addressRaw: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val category: BrotherhoodAssetCategory,
    val isReferenceStablecoin: Boolean = false,
)

/**
 * Enforces the locked-in BrotherHood wallet asset scope (ADR-0001):
 * Only Native TON (`GRAM`), BrotherHood `FI`/`HD` (`FI_ADDRESS`), and verified BrotherHood
 * Personal Tokens (`PersonalMinter` / `PersonalWallet`) are displayed in the portfolio and token pickers.
 */
class BrotherhoodTokenWhitelist(
    private val dao: BrotherhoodDao,
) {
    fun classifyAsset(
        tokenAddress: String,
        codeHashHex: String? = null,
        knownPersonalMinters: Set<String> = emptySet(),
        adminPersonalMinterAddress: String? = null,
    ): BrotherhoodAssetCategory {
        val trimmed = tokenAddress.trim()
        if (trimmed.equals("TON", ignoreCase = true) || trimmed.equals("GRAM", ignoreCase = true)) {
            return BrotherhoodAssetCategory.NATIVE_TON
        }
        val normalized = BrotherhoodConfig.normalizeAddress(trimmed)
        if (normalized == BrotherhoodConfig.normalizeAddress(BrotherhoodConfig.FI_ADDRESS_STR)) {
            return BrotherhoodAssetCategory.FOSSFI_MAIN
        }
        if (adminPersonalMinterAddress != null &&
            normalized == BrotherhoodConfig.normalizeAddress(adminPersonalMinterAddress)
        ) {
            return BrotherhoodAssetCategory.PERSONAL_TOKEN
        }
        if (knownPersonalMinters.any { BrotherhoodConfig.normalizeAddress(it) == normalized }) {
            return BrotherhoodAssetCategory.PERSONAL_TOKEN
        }
        if (codeHashHex != null) {
            val contractType = KnownCodeHashes.detectKnownType(codeHashHex)
            if (contractType == KnownContractType.PERSONAL_MINTER ||
                contractType == KnownContractType.PERSONAL_WALLET
            ) {
                return BrotherhoodAssetCategory.PERSONAL_TOKEN
            }
        }
        return BrotherhoodAssetCategory.EXCLUDED
    }

    suspend fun getTrackedPersonalMinterAddresses(): Set<String> {
        val tracked = dao.getAllTrackedPersonalTokens().mapNotNull {
            BrotherhoodConfig.normalizeAddress(it.minterAddress)
        }
        val cachedMetadata = dao.getAllTokenMetadata()
            .filter { it.isPersonalToken || it.isVerifiedEcosystemToken }
            .mapNotNull { BrotherhoodConfig.normalizeAddress(it.minterAddress) }
        return (tracked + cachedMetadata).toSet()
    }

    suspend fun isWhitelistedToken(
        tokenAddress: String,
        codeHashHex: String? = null,
        adminPersonalMinterAddress: String? = null,
    ): Boolean {
        val knownMinters = getTrackedPersonalMinterAddresses()
        val category = classifyAsset(
            tokenAddress = tokenAddress,
            codeHashHex = codeHashHex,
            knownPersonalMinters = knownMinters,
            adminPersonalMinterAddress = adminPersonalMinterAddress,
        )
        return category != BrotherhoodAssetCategory.EXCLUDED
    }

    companion object {
        val NATIVE_TON_DESCRIPTOR = BrotherhoodTokenDescriptor(
            addressRaw = "TON",
            name = "Gram",
            symbol = "GRAM",
            decimals = BrotherhoodConfig.TON_DECIMALS,
            category = BrotherhoodAssetCategory.NATIVE_TON,
        )

        val FOSSFI_DESCRIPTOR = BrotherhoodTokenDescriptor(
            addressRaw = BrotherhoodConfig.FI_ADDRESS_RAW,
            name = BrotherhoodConfig.FI_NAME,
            symbol = BrotherhoodConfig.FI_SYMBOL,
            decimals = BrotherhoodConfig.FI_UI_DECIMALS,
            category = BrotherhoodAssetCategory.FOSSFI_MAIN,
        )
    }
}

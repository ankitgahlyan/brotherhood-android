package com.tonapps.brotherhood.config

import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64

enum class KnownContractType(val wireName: String) {
    FI_WALLET("fiWallet"),
    FI_MINTER("fiMinter"),
    PERSONAL_MINTER("personalMinter"),
    PERSONAL_WALLET("personalWallet"),
    LOCATION("location"),
    LOTTERY("lottery"),
    POLL("poll"),
    VOTER("voter"),
    HOLDING("holding"),
    FOLLOWING("following"),
    DNS_ITEM("dnsItem"),
    WALLET_V5_R1("walletV5R1"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireName(name: String?): KnownContractType {
            if (name.isNullOrBlank()) {
                return UNKNOWN
            }
            return entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

object KnownCodeHashes {
    const val FI_WALLET = "Ti0UlT4VdS2E/rEmAwldu2wcW65vu8mM5JQi9VZegM4="
    const val FI_MINTER = "HfRbbtUw0OmTktmgiANPBMA0839NzO3js4oa7t4kwGY="
    const val PERSONAL_MINTER = "Ry9JsYpmoEIgOCeTWb0TR+KGQr/f6MgJbC7Yp0nSCGQ="
    const val PERSONAL_WALLET = "Uu1KYzHr+Lvfr7ET2y2WI6m1bxX8v44Vn7gNhqu4mU4="
    const val LOCATION = "xB9hKP2yNL+B4skAr4q26SlNqHXwsva1XFn8Ib3MjkU="
    const val LOTTERY = "HHh95xA0sDcOowpVnyULcDbZczqe0zk2oAw8x+ulo9M="
    const val POLL = "Y4S1BhWmVTOpAVHeDqaFUTUPnHay+aFyF+gjFZx9l8Q="
    const val WALLET_V5_R1 = "IINLe3KxEhR+Gy+0V7hOdNGjDwT3N9T2KmaOlVLSty8="

    private val HASH_TO_TYPE: Map<String, KnownContractType> = mapOf(
        FI_WALLET to KnownContractType.FI_WALLET,
        FI_MINTER to KnownContractType.FI_MINTER,
        PERSONAL_MINTER to KnownContractType.PERSONAL_MINTER,
        PERSONAL_WALLET to KnownContractType.PERSONAL_WALLET,
        LOCATION to KnownContractType.LOCATION,
        LOTTERY to KnownContractType.LOTTERY,
        POLL to KnownContractType.POLL,
        WALLET_V5_R1 to KnownContractType.WALLET_V5_R1,
    )

    /**
     * Normalizes a code_hash returned by Toncenter v3 (hex or base64/base64url) to standard base64.
     */
    fun normalizeCodeHash(hash: String?): String? {
        if (hash.isNullOrBlank()) {
            return null
        }
        val trimmed = hash.trim()
        // If 64-char hex string, convert to base64
        if (trimmed.length == 64 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return try {
                ByteArray(32) { i ->
                    trimmed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }.encodeBase64()
            } catch (_: Exception) {
                trimmed
            }
        }
        // Standardize base64url to base64
        val stdBase64 = trimmed.replace('-', '+').replace('_', '/')
        return try {
            stdBase64.decodeBase64().encodeBase64()
        } catch (_: Exception) {
            stdBase64
        }
    }

    fun detectKnownType(
        codeHash: String?,
        explicitType: KnownContractType? = null,
    ): KnownContractType {
        if (explicitType != null && explicitType != KnownContractType.UNKNOWN) {
            return explicitType
        }
        val normalized = normalizeCodeHash(codeHash) ?: return KnownContractType.UNKNOWN
        return HASH_TO_TYPE[normalized] ?: KnownContractType.UNKNOWN
    }
}

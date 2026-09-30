package com.tonapps.brotherhood.config

import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import org.ton.block.AddrStd
import java.math.BigInteger

object BrotherhoodConfig {
    const val DEFAULT_TESTNET: Boolean = true

    const val FI_ADDRESS_STR = "kQByVk5DwR_q9O0QECxai3CDpE-7Qimbb4OUE9Bt4Qz0deAE"
    const val BRO_COLLECTION_RESOLVER_STR = "kQCKrNefTDKT8hNkJ-wxxANdn6KXLa_3VqsRKFKEXyFUchTa"
    const val BRO_TREASURY_ADDRESS_STR = "kQCSUhA50ynSi1hxR9KrTLOCOk89iVGHG9wKS_1Agqi-OQF6"
    const val DAO_PROXY_ADDRESS_STR = "kQCe-0dlNfCYRw_YWKjunlJmxIDfSRWxvHS6FI-eflPgY1jZ"
    const val ZERO_ADDRESS_RAW = "0:0000000000000000000000000000000000000000000000000000000000000000"
    const val ZERO_ADDRESS_USER_FRIENDLY = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"

    val FI_ADDRESS: AddrStd by lazy { AddrStd(FI_ADDRESS_STR) }
    val FI_ADDRESS_RAW: String by lazy { FI_ADDRESS.toAccountId() }
    val BRO_COLLECTION_RESOLVER: AddrStd by lazy { AddrStd(BRO_COLLECTION_RESOLVER_STR) }
    val BRO_TREASURY_ADDRESS: AddrStd by lazy { AddrStd(BRO_TREASURY_ADDRESS_STR) }
    val DAO_PROXY_ADDRESS: AddrStd by lazy { AddrStd(DAO_PROXY_ADDRESS_STR) }
    val ZERO_ADDRESS: AddrStd by lazy { AddrStd(ZERO_ADDRESS_RAW) }

    const val SHARD_DEPTH: Int = 8

    // Token display & raw unit configuration
    const val FI_SYMBOL = "FI"
    const val FI_ALT_SYMBOL = "HD"
    const val FI_NAME = "Brotherhood FossFi"
    const val FI_RAW_DECIMALS = 9
    const val FI_UI_DECIMALS = 5
    const val PERSONAL_TOKEN_DECIMALS = 9
    const val TON_DECIMALS = 9

    // Standard gas / fee constants in nanotons (1 TON = 1_000_000_000L)
    const val ONE_TON_NANO: Long = 1_000_000_000L
    const val INVITE_MINT_FEE_NANO: Long = 100_000_000L // 0.1 TON
    const val INVITE_GAS_NANO: Long = 1_000_000_000L // 1.0 TON
    const val INVITE_TOTAL_TON_NANO: Long = 1_100_000_000L // 1.1 TON
    const val INVITE_AMOUNT_RAW: Long = 100_000_000_000L // 100 FI (in 9 decimals)

    const val CLAIM_WEEKLY_GRANT_GAS_NANO: Long = 500_000_000L // 0.5 TON
    const val VOTE_GAS_NANO: Long = 200_000_000L // 0.2 TON
    const val PROFILE_CHANGE_GAS_NANO: Long = 500_000_000L // 0.5 TON
    const val CREDIT_ACTION_GAS_NANO: Long = 350_000_000L // 0.35 TON
    const val ALLOWANCE_GAS_NANO: Long = 200_000_000L // 0.2 TON
    const val GOLD_TRANSFER_GAS_NANO: Long = 250_000_000L // 0.25 TON
    const val DEFERRED_PAYMENT_GAS_NANO: Long = 300_000_000L // 0.3 TON
    const val AUTHORITY_ACTION_GAS_NANO: Long = 300_000_000L // 0.3 TON
    const val SUBMIT_PROPOSAL_GAS_NANO: Long = 1_500_000_000L // 1.5 TON
    const val VOTE_PROPOSAL_GAS_NANO: Long = 500_000_000L // 0.5 TON
    const val DEPLOY_PERSONAL_MINTER_TON_NANO: Long = 500_000_000L // 0.5 TON
    const val LINK_PERSONAL_JETTON_GAS_NANO: Long = 200_000_000L // 0.2 TON
    const val FOLLOW_ACTION_GAS_NANO: Long = 300_000_000L // 0.3 TON
    const val SIMPLE_WALLET_ACTION_GAS_NANO: Long = 200_000_000L // 0.2 TON
    const val PERSONAL_MINT_GAS_NANO: Long = 350_000_000L // 0.35 TON
    const val JETTON_TRANSFER_GAS_NANO: Long = 150_000_000L // 0.15 TON
    const val DNS_ACTION_GAS_NANO: Long = 150_000_000L // 0.15 TON
    const val LOTTERY_ENTRY_FEE_NANO: Long = 80_000_000L // 0.08 TON
    const val LOTTERY_TICKET_PRICE_FI_NANO: Long = 100_000_000_000L // 100 FI
    const val DAO_ADMIN_GAS_NANO: Long = 300_000_000L // 0.3 TON
    const val UPGRADE_GAS_NANO: Long = 500_000_000L // 0.5 TON

    // Auto-funder thresholds (from web useAutoFundFiWallet)
    const val AUTO_FUND_THRESHOLD_NANO: Long = 2_000_000_000L // 2.0 TON
    const val AUTO_FUND_TOP_UP_NANO: Long = 2_000_000_000L // 2.0 TON
    const val AUTO_FUND_RESERVE_NANO: Long = 500_000_000L // 0.5 TON

    // Toncenter v3 base URLs
    const val TONCENTER_V3_MAINNET = "https://toncenter.com/api/v3"
    const val TONCENTER_V3_TESTNET = "https://testnet.toncenter.com/api/v3"

    val ZERO_BIG_INT: BigInteger = BigInteger.ZERO

    fun toncenterV3Url(testnet: Boolean = DEFAULT_TESTNET): String {
        return if (testnet) {
            TONCENTER_V3_TESTNET
        } else {
            TONCENTER_V3_MAINNET
        }
    }

    fun isZeroAddress(address: AddrStd?): Boolean {
        if (address == null) {
            return true
        }
        return address.workchainId == 0 && address.address.toByteArray().all { it == 0.toByte() }
    }

    fun isZeroAddress(addressStr: String?): Boolean {
        if (addressStr.isNullOrBlank()) {
            return true
        }
        val trimmed = addressStr.trim()
        if (trimmed == ZERO_ADDRESS_RAW || trimmed == ZERO_ADDRESS_USER_FRIENDLY) {
            return true
        }
        return try {
            isZeroAddress(AddrStd(trimmed))
        } catch (_: Exception) {
            false
        }
    }

    fun normalizeAddress(address: String?): String {
        if (address.isNullOrBlank()) {
            return ""
        }
        return try {
            AddrStd(address.trim()).toAccountId()
        } catch (_: Exception) {
            address.trim().lowercase()
        }
    }

    fun toUserFriendlyBounceable(address: AddrStd, testnet: Boolean = DEFAULT_TESTNET): String {
        return address.toString(userFriendly = true, bounceable = true, testOnly = testnet)
    }
}

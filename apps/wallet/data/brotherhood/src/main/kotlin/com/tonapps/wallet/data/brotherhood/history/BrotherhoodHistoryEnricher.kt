package com.tonapps.wallet.data.brotherhood.history

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.BrotherhoodErrors
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes

data class EnrichedContractAction(
    val title: String,
    val subtitle: String,
    val contextBadge: String?,
    val opcodeHex: String?,
    val failureWarning: String?,
)

/**
 * Enriches raw TON smart-contract executions (`SmartContractAction`) and failed transactions
 * with human-readable BrotherHood action titles, domain context badges (`BrotherHood`, `Personal Token`,
 * `City Network`, `DAO`, `Lottery`, `Escrow`, `.bro DNS`), and decoded TVM exit codes (`100..766`).
 */
object BrotherhoodHistoryEnricher {
    private val EXIT_CODE_REGEX = Regex("""(?:exit[\s_]*code|error|code)[:\s=#]*(\d{3})""", RegexOption.IGNORE_CASE)
    private val STANDALONE_CODE_REGEX = Regex("""\b([14567]\d{2})\b""")

    fun enrichSmartContractExecution(
        operation: String?,
        payload: String?,
        executorAddress: String?,
        fallbackTitle: String,
        fallbackSubtitle: String,
        isFailed: Boolean = false,
        failureDescription: String? = null,
    ): EnrichedContractAction {
        val opcode = BrotherhoodOpcodes.parseOpcodeNumber(operation)
            ?: BrotherhoodOpcodes.parseOpcodeNumber(payload)
        val opcodeInfo = opcode?.let { BrotherhoodOpcodes.getOpcodeInfo(it) }

        val inferredBadge = opcodeInfo?.contextBadge ?: inferBadgeFromAddress(executorAddress)
        val opcodeHex = opcode?.let { "0x" + it.toString(16).padStart(8, '0') }

        val title = opcodeInfo?.friendlyTitle ?: fallbackTitle
        val subtitle = when {
            opcodeInfo != null && fallbackSubtitle.isNotBlank() ->
                "${opcodeInfo.structName} · $fallbackSubtitle"
            opcodeInfo != null -> opcodeInfo.structName
            !payload.isNullOrBlank() -> payload
            !operation.isNullOrBlank() -> operation
            else -> fallbackSubtitle
        }

        val failureWarning = if (isFailed) {
            decodeFailureWarning(failureDescription ?: payload ?: operation)
        } else {
            null
        }

        return EnrichedContractAction(
            title = title,
            subtitle = subtitle,
            contextBadge = inferredBadge,
            opcodeHex = opcodeHex,
            failureWarning = failureWarning,
        )
    }

    fun decodeFailureWarning(rawText: String?): String? {
        if (rawText.isNullOrBlank()) {
            return null
        }
        val match = EXIT_CODE_REGEX.find(rawText) ?: STANDALONE_CODE_REGEX.find(rawText)
        val exitCode = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val description = BrotherhoodErrors.TVM_EXIT_CODES[exitCode] ?: return null
        return "Exit $exitCode: $description"
    }

    private fun inferBadgeFromAddress(address: String?): String? {
        if (address.isNullOrBlank()) {
            return null
        }
        val normalized = BrotherhoodConfig.normalizeAddress(address)
        return when (normalized) {
            BrotherhoodConfig.normalizeAddress(BrotherhoodConfig.FI_ADDRESS_STR) -> "BrotherHood"
            BrotherhoodConfig.normalizeAddress(BrotherhoodConfig.BRO_COLLECTION_RESOLVER_STR) -> ".bro DNS"
            BrotherhoodConfig.normalizeAddress(BrotherhoodConfig.DAO_PROXY_ADDRESS_STR) -> "DAO"
            else -> null
        }
    }
}

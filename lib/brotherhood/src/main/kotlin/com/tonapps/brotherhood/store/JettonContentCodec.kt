package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringTail
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStringTail
import com.tonapps.brotherhood.store.TolkSliceUtils.loadTonDict
import com.tonapps.brotherhood.store.TolkSliceUtils.parseUInt256Key
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import java.math.BigInteger
import java.security.MessageDigest

@Serializable
data class JettonMetadataContent(
    val name: String? = null,
    val symbol: String? = null,
    val description: String? = null,
    val image: String? = null,
    val decimals: Int? = null,
    val uri: String? = null,
)

object JettonContentCodec {
    private val KEY_URI = sha256Key("uri")
    private val KEY_NAME = sha256Key("name")
    private val KEY_DESCRIPTION = sha256Key("description")
    private val KEY_IMAGE = sha256Key("image")
    private val KEY_SYMBOL = sha256Key("symbol")
    private val KEY_DECIMALS = sha256Key("decimals")

    private fun sha256Key(key: String): BigInteger {
        val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return BigInteger(1, bytes)
    }

    /**
     * Parses both Tolk 4-ref flat metadata cell (PersonalMinter / FossFi) and TEP-64 0x00/0x01 metadata cells.
     */
    fun parseJettonContent(cell: Cell): JettonMetadataContent {
        if (cell.isEmpty()) {
            return JettonMetadataContent()
        }
        val s = cell.beginParse()

        // Case 1: Tolk 4-ref flat metadata cell (0 bits or no 0x00/0x01 tag, 4 refs: name, symbol, description, image)
        if (s.bits.size == 0 && s.refs.size >= 3) {
            val name = runCatching { s.loadRef().beginParse().loadStringTail() }.getOrNull()
            val symbol = runCatching { s.loadRef().beginParse().loadStringTail() }.getOrNull()
            val description = runCatching { s.loadRef().beginParse().loadStringTail() }.getOrNull()
            val image = if (s.refsPosition < s.refs.size) {
                runCatching { s.loadRef().beginParse().loadStringTail() }.getOrNull()
            } else {
                null
            }
            return JettonMetadataContent(
                name = name?.takeIf { it.isNotBlank() },
                symbol = symbol?.takeIf { it.isNotBlank() },
                description = description?.takeIf { it.isNotBlank() },
                image = image?.takeIf { it.isNotBlank() },
            )
        }

        if (s.bits.size >= 8) {
            val prefix = s.loadUInt(8).toInt()
            when (prefix) {
                0x01 -> {
                    val uri = s.loadStringTail()
                    return JettonMetadataContent(uri = uri)
                }
                0x00 -> {
                    val dict = runCatching {
                        s.loadTonDict(256, ::parseUInt256Key) { valSlice ->
                            decodeSnakeMetadataValue(valSlice)
                        }
                    }.getOrDefault(emptyMap())
                    return JettonMetadataContent(
                        name = dict[KEY_NAME],
                        symbol = dict[KEY_SYMBOL],
                        description = dict[KEY_DESCRIPTION],
                        image = dict[KEY_IMAGE],
                        decimals = dict[KEY_DECIMALS]?.toIntOrNull(),
                        uri = dict[KEY_URI],
                    )
                }
            }
        }

        // Fallback: plain string cell
        val fallbackStr = runCatching { cell.beginParse().loadStringTail() }.getOrNull()
        return JettonMetadataContent(uri = fallbackStr)
    }

    private fun decodeSnakeMetadataValue(slice: CellSlice): String {
        val targetSlice = if (slice.bits.size - slice.bitsPosition == 0 && slice.refs.size - slice.refsPosition > 0) {
            slice.loadRef().beginParse()
        } else {
            slice
        }
        if (targetSlice.bits.size - targetSlice.bitsPosition >= 8) {
            val tag = targetSlice.preloadUInt(8).toInt()
            if (tag == 0x00) {
                targetSlice.loadUInt(8)
            }
        }
        return targetSlice.loadStringTail()
    }

    /**
     * Builds the Tolk 4-ref flat metadata cell used by `PersonalMinter` and `ChangeMinterMetadata`:
     * Ref 0: name, Ref 1: symbol, Ref 2: description, Ref 3: image.
     */
    fun buildTolkOnchainMetadata(
        name: String,
        symbol: String,
        description: String = "",
        image: String = "",
    ): Cell {
        return CellBuilder.createCell {
            storeRef(CellBuilder.createCell { storeStringTail(name) })
            storeRef(CellBuilder.createCell { storeStringTail(symbol) })
            storeRef(CellBuilder.createCell { storeStringTail(description) })
            storeRef(CellBuilder.createCell { storeStringTail(image) })
        }
    }
}

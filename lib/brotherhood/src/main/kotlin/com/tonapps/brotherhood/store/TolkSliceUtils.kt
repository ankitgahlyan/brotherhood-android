package com.tonapps.brotherhood.store

import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import kotlinx.io.bytestring.ByteString
import org.ton.bigint.toBigInt
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.contract.CellStringTlbConstructor
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import java.math.BigInteger

object TolkSliceUtils {

    private const val BOC_GENERIC_MAGIC = 0xB5EE9C72.toInt()

    private data class CellKey(
        private val d1: Byte,
        private val d2: Byte,
        private val hash: BitString,
    )

    private val Cell.key: CellKey
        get() = CellKey(descriptor.d1, descriptor.d2, hash())

    fun BigInteger.toBigInt(): org.ton.bigint.BigInt = org.ton.bigint.BigInt(toByteArray())

    fun CellSlice.loadBytes(byteCount: Int): ByteArray {
        if (byteCount <= 0) {
            return ByteArray(0)
        }
        return loadBits(byteCount * 8).toByteArray()
    }

    fun AddrStd.toRawAccountId(): String {
        return toString(userFriendly = false).lowercase()
    }

    fun AddrStd.toAccountId(): String = toRawAccountId()

    fun String.parseCellFromBase64(): Cell {
        if (this.isBlank()) {
            throw IllegalArgumentException("Empty cell")
        }
        val parsed = BagOfCells(this.trim().decodeBase64())
        return parsed.first()
    }

    fun String.cellFromBase64(): Cell = parseCellFromBase64()

    fun Cell.toBoc(): ByteArray {
        val cells = topologicalOrder()
        val indexes = HashMap<CellKey, Int>(cells.size)
        cells.forEachIndexed { index, cell -> indexes[cell.key] = index }

        var sizeBytes = 0
        while (cells.size >= (1L shl (sizeBytes shl 3))) {
            sizeBytes++
        }

        val serializedCells = cells.map { cell ->
            val data = cell.bits.toByteArray(augment = true)
            val bytes = ByteArray(2 + data.size + cell.refs.size * sizeBytes)
            bytes[0] = cell.descriptor.d1
            bytes[1] = cell.descriptor.d2
            data.copyInto(bytes, 2)
            var offset = 2 + data.size
            for (reference in cell.refs) {
                val refIndex = indexes[reference.key]
                    ?: throw IllegalStateException("Cell reference is missing in the bag of cells")
                offset = bytes.writeInt(offset, refIndex, sizeBytes)
            }
            bytes
        }

        val totalSize = serializedCells.sumOf { it.size }
        var offsetBytes = 0
        while (totalSize >= (1L shl (offsetBytes shl 3))) {
            offsetBytes++
        }

        val output = ByteArray(6 + sizeBytes * 4 + offsetBytes + totalSize)
        var offset = output.writeInt(0, BOC_GENERIC_MAGIC, 4)
        offset = output.writeInt(offset, sizeBytes, 1)
        offset = output.writeInt(offset, offsetBytes, 1)
        offset = output.writeInt(offset, cells.size, sizeBytes)
        offset = output.writeInt(offset, 1, sizeBytes)
        offset = output.writeInt(offset, 0, sizeBytes)
        offset = output.writeInt(offset, totalSize, offsetBytes)
        offset = output.writeInt(offset, 0, sizeBytes)
        for (bytes in serializedCells) {
            bytes.copyInto(output, offset)
            offset += bytes.size
        }
        return output
    }

    private fun Cell.topologicalOrder(): List<Cell> {
        val discovered = LinkedHashMap<CellKey, Cell>()
        val heights = HashMap<CellKey, Int>()
        height(discovered, heights, 0)
        return discovered.values.sortedByDescending { heights.getValue(it.key) }
    }

    private fun Cell.height(
        discovered: LinkedHashMap<CellKey, Cell>,
        heights: HashMap<CellKey, Int>,
        depth: Int,
    ): Int {
        if (depth > Cell.MAX_DEPTH) {
            throw IllegalStateException("Cell depth is too large")
        }
        val cellKey = key
        heights[cellKey]?.let { return it }
        discovered[cellKey] = this
        var height = 0
        for (reference in refs) {
            height = maxOf(height, reference.height(discovered, heights, depth + 1) + 1)
        }
        heights[cellKey] = height
        return height
    }

    private fun ByteArray.writeInt(offset: Int, value: Int, bytes: Int): Int {
        for (i in 0 until bytes) {
            this[offset + i] = (value ushr ((bytes - i - 1) shl 3)).toByte()
        }
        return offset + bytes
    }

    fun Cell.toBocBase64(): String {
        return toBoc().encodeBase64()
    }

    fun Cell.base64(): String = toBocBase64()

    fun CellSlice.loadAddressInt(): MsgAddressInt {
        return loadTlb(MsgAddressInt)
    }

    fun CellSlice.loadMaybeAddress(): MsgAddress? {
        return when (val type = preloadUInt(2)) {
            2.toBigInt() -> loadAddressInt()
            0.toBigInt() -> {
                bitsPosition += 2
                null
            }
            else -> throw IllegalArgumentException("Invalid address type: $type")
        }
    }

    fun CellSlice.loadCoinsTlb(): Coins {
        return loadTlb(Coins)
    }

    fun CellBuilder.storeAddressInt(value: MsgAddressInt) = apply {
        storeTlb(MsgAddressInt, value)
    }

    fun CellBuilder.storeAddress(value: MsgAddressInt) = storeAddressInt(value)

    fun CellBuilder.storeMaybeAddressInt(value: MsgAddressInt?) = apply {
        if (value == null) {
            storeBit(false)
        } else {
            storeBit(true)
            storeAddressInt(value)
        }
    }

    fun CellBuilder.storeMaybeAddress(value: MsgAddressInt?) = storeMaybeAddressInt(value)

    fun CellBuilder.storeCoinsTlb(value: Coins) = apply {
        storeTlb(Coins, value)
    }

    fun CellBuilder.storeCoins(value: Coins) = storeCoinsTlb(value)

    fun CellBuilder.storeStringTailUtf8(src: String) = apply {
        val bytes = src.encodeToByteArray()
        storeTlb(CellStringTlbConstructor, ByteString(bytes))
    }

    fun CellBuilder.storeStringTail(src: String) = storeStringTailUtf8(src)

    fun CellBuilder.storeStringRefTailUtf8(src: String) = apply {
        storeRef(CellBuilder.createCell { storeStringTailUtf8(src) })
    }

    fun CellBuilder.storeStringRefTail(src: String) = storeStringRefTailUtf8(src)

    fun CellSlice.loadStdAddress(): AddrStd {
        return when (val addr = loadAddressInt()) {
            is AddrStd -> addr
            else -> throw IllegalArgumentException("Expected AddrStd, got $addr")
        }
    }

    fun CellSlice.loadMaybeStdAddress(): AddrStd? {
        return when (val addr = loadMaybeAddress()) {
            null -> null
            is AddrStd -> addr
            else -> null
        }
    }

    fun CellSlice.loadCoinsBigInt(): BigInteger {
        val bytes = loadCoinsTlb().amount.value.toByteArray()
        return if (bytes.isEmpty()) {
            BigInteger.ZERO
        } else {
            BigInteger(bytes)
        }
    }

    fun CellSlice.loadUIntBigInt(bits: Int): BigInteger {
        val bytes = loadUInt(bits).toByteArray()
        return if (bytes.isEmpty()) {
            BigInteger.ZERO
        } else {
            BigInteger(bytes)
        }
    }

    /**
     * Reads UTF-8 string across a cell slice and any snake-format tail refs.
     */
    fun CellSlice.loadStringTail(): String {
        val bytes = mutableListOf<Byte>()
        readRemainingBytesRecursive(this, bytes)
        return bytes.toByteArray().decodeToString()
    }

    /**
     * Reads a string stored in a child reference cell (`storeStringRefTail` in Tolk).
     */
    fun CellSlice.loadStringRefTail(): String {
        val refCell = loadRef()
        return refCell.beginParse().loadStringTail()
    }

    private fun readRemainingBytesRecursive(slice: CellSlice, out: MutableList<Byte>) {
        val remBits = slice.bits.size - slice.bitsPosition
        val byteCount = remBits / 8
        if (byteCount > 0) {
            val loaded = slice.loadBytes(byteCount)
            for (b in loaded) {
                out.add(b)
            }
        }
        if (slice.refsPosition < slice.refs.size) {
            val nextRef = slice.loadRef()
            readRemainingBytesRecursive(nextRef.beginParse(), out)
        }
    }

    /**
     * Generic TVM HashmapE(keyBits, V) parser directly on CellSlice.
     */
    fun <K, V> CellSlice.loadTonDict(
        keyBitLength: Int,
        keyParser: (BitString) -> K,
        valueParser: (CellSlice) -> V,
    ): Map<K, V> {
        val hasRoot = loadBit()
        if (!hasRoot) {
            return emptyMap()
        }
        val rootCell = loadRef()
        val result = LinkedHashMap<K, V>()
        parseDictNode(
            cell = rootCell,
            remainingBits = keyBitLength,
            prefix = BooleanArray(0),
            keyParser = keyParser,
            valueParser = valueParser,
            out = result,
        )
        return result
    }

    fun <K, V> parseTonDictFromRootCell(
        rootCell: Cell?,
        keyBitLength: Int,
        keyParser: (BitString) -> K,
        valueParser: (CellSlice) -> V,
    ): Map<K, V> {
        if (rootCell == null || rootCell.isEmpty()) {
            return emptyMap()
        }
        val result = LinkedHashMap<K, V>()
        parseDictNode(
            cell = rootCell,
            remainingBits = keyBitLength,
            prefix = BooleanArray(0),
            keyParser = keyParser,
            valueParser = valueParser,
            out = result,
        )
        return result
    }

    private fun <K, V> parseDictNode(
        cell: Cell,
        remainingBits: Int,
        prefix: BooleanArray,
        keyParser: (BitString) -> K,
        valueParser: (CellSlice) -> V,
        out: MutableMap<K, V>,
    ) {
        val slice = cell.beginParse()
        val labelBits = readHmLabel(slice, remainingBits)
        val nextPrefix = BooleanArray(prefix.size + labelBits.size).also { merged ->
            prefix.copyInto(merged, 0)
            labelBits.copyInto(merged, prefix.size)
        }
        val leftRemaining = remainingBits - labelBits.size
        if (leftRemaining <= 0) {
            val keyBitString = BitString(nextPrefix.toList())
            val key = keyParser(keyBitString)
            val value = valueParser(slice)
            out[key] = value
        } else {
            val leftCell = slice.loadRef()
            val rightCell = slice.loadRef()
            val leftPrefix = BooleanArray(nextPrefix.size + 1).also {
                nextPrefix.copyInto(it, 0)
                it[nextPrefix.size] = false
            }
            val rightPrefix = BooleanArray(nextPrefix.size + 1).also {
                nextPrefix.copyInto(it, 0)
                it[nextPrefix.size] = true
            }
            parseDictNode(leftCell, leftRemaining - 1, leftPrefix, keyParser, valueParser, out)
            parseDictNode(rightCell, leftRemaining - 1, rightPrefix, keyParser, valueParser, out)
        }
    }

    private fun readHmLabel(slice: CellSlice, maxLen: Int): BooleanArray {
        if (!slice.loadBit()) {
            var len = 0
            while (slice.loadBit()) {
                len++
            }
            return BooleanArray(len) { slice.loadBit() }
        }
        return if (!slice.loadBit()) {
            val nBits = bitLengthForMax(maxLen)
            val len = if (nBits > 0) {
                slice.loadUInt(nBits).toInt()
            } else {
                0
            }
            BooleanArray(len) { slice.loadBit() }
        } else {
            val bitVal = slice.loadBit()
            val nBits = bitLengthForMax(maxLen)
            val len = if (nBits > 0) {
                slice.loadUInt(nBits).toInt()
            } else {
                0
            }
            BooleanArray(len) { bitVal }
        }
    }

    private fun bitLengthForMax(maxLen: Int): Int {
        if (maxLen <= 0) {
            return 0
        }
        return 32 - Integer.numberOfLeadingZeros(maxLen)
    }

    fun parseAddressKey(bits: BitString): AddrStd {
        val slice = CellBuilder.createCell {
            storeBits(bits)
        }.beginParse()
        return when (val addr: MsgAddressInt = slice.loadAddressInt()) {
            is AddrStd -> addr
            else -> throw IllegalArgumentException("Unsupported dict address key: $addr")
        }
    }

    fun parseUInt256Key(bits: BitString): BigInteger {
        val slice = CellBuilder.createCell {
            storeBits(bits)
        }.beginParse()
        return slice.loadUIntBigInt(256)
    }

    fun CellSlice.loadAddressToAddressDict(): Map<AddrStd, AddrStd> {
        return loadTonDict(267, ::parseAddressKey) { it.loadStdAddress() }
    }

    fun CellSlice.loadAddressToCoinsDict(): Map<AddrStd, BigInteger> {
        return loadTonDict(267, ::parseAddressKey) { it.loadCoinsBigInt() }
    }

    fun CellSlice.loadAddressToUInt4Dict(): Map<AddrStd, Int> {
        return loadTonDict(267, ::parseAddressKey) { it.loadUInt(4).toInt() }
    }

    fun CellSlice.loadAddressToBoolDict(): Map<AddrStd, Boolean> {
        return loadTonDict(267, ::parseAddressKey) { it.loadBit() }
    }

    fun CellSlice.loadAddressToUnitDict(): Set<AddrStd> {
        return loadTonDict(267, ::parseAddressKey) { Unit }.keys
    }
}

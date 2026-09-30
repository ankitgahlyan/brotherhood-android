package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.store.TolkSliceUtils.loadAddressToBoolDict
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStdAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.loadStringRefTail
import kotlinx.serialization.Serializable
import org.ton.cell.Cell
import org.ton.cell.CellSlice

@Serializable
data class LocationStore(
    val h3Cell: String,
    val minterAddress: String,
    val memberCount: Long,
    val members: List<String>,
    val version: Int,
) {
    companion object {
        fun fromSlice(s: CellSlice): LocationStore {
            val h3Cell = s.loadStringRefTail()
            val minterAddress = s.loadStdAddress().toAccountId()
            val memberCount = s.loadUInt(32).toLong()
            val membersMap = s.loadAddressToBoolDict()
            val members = membersMap.filterValues { it }.keys.map { it.toAccountId() }
            val version = s.loadUInt(10).toInt()
            return LocationStore(
                h3Cell = h3Cell,
                minterAddress = minterAddress,
                memberCount = memberCount,
                members = members,
                version = version,
            )
        }

        fun fromCell(cell: Cell): LocationStore = fromSlice(cell.beginParse())

        fun fromBocBase64(bocBase64: String): LocationStore = fromCell(bocBase64.cellFromBase64())
    }
}

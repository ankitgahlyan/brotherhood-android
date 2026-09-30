package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.h3.H3Helper
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.LocationStore
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.WatchedLocationEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.Flow
import org.ton.block.AddrStd
import java.math.BigInteger

data class HydratedCityLocation(
    val h3Cell: String,
    val locationAddress: String,
    val label: String,
    val countryCode: Int,
    val memberCount: Long,
    val treasuryBalanceNano: BigInteger,
    val isPrimaryResidence: Boolean,
    val store: LocationStore?,
)

class CityNetworkRepository(
    private val dao: BrotherhoodDao,
    private val hydrator: AccountStateHydrator,
) {
    fun observeWatchedLocations(): Flow<List<WatchedLocationEntity>> {
        return dao.observeWatchedLocations()
    }

    suspend fun syncLocations(
        primaryResidenceH3Cell: String?,
        primaryCountryCode: Int = 356,
        forceRefresh: Boolean = false,
    ): List<HydratedCityLocation> {
        val existing = dao.getAllWatchedLocations()
        val candidateH3Cells = buildMap {
            val cleanPrimary = H3Helper.normalizeH3Cell(primaryResidenceH3Cell)
            if (H3Helper.isValidH3Cell(cleanPrimary)) {
                val preset = H3Helper.PRESET_REGIONS.firstOrNull { it.h3Cell == cleanPrimary }
                put(
                    cleanPrimary,
                    Triple(
                        preset?.name ?: "Primary Residence ($cleanPrimary)",
                        preset?.countryCode ?: primaryCountryCode,
                        true,
                    ),
                )
            }
            for (row in existing) {
                val cell = H3Helper.normalizeH3Cell(row.h3Cell)
                if (H3Helper.isValidH3Cell(cell) && !containsKey(cell)) {
                    put(cell, Triple(row.label, row.countryCode, false))
                }
            }
            if (isEmpty()) {
                for (preset in H3Helper.PRESET_REGIONS.take(4)) {
                    put(preset.h3Cell, Triple(preset.name, preset.countryCode, false))
                }
            }
        }

        val locationAddrByH3 = candidateH3Cells.keys.associateWith { cell ->
            ShardedAddressDerivation.deriveLocation(cell).address.toAccountId()
        }

        val hydratedMap = hydrator.hydrateBatch(
            addresses = locationAddrByH3.values.toList(),
            explicitTypes = locationAddrByH3.values.associateWith { KnownContractType.LOCATION },
            forceRefresh = forceRefresh,
        )

        val now = System.currentTimeMillis()
        val entities = mutableListOf<WatchedLocationEntity>()
        val results = mutableListOf<HydratedCityLocation>()

        for ((h3Cell, meta) in candidateH3Cells) {
            val (label, countryCode, isPrimary) = meta
            val locAddr = locationAddrByH3.getValue(h3Cell)
            val state = hydratedMap[locAddr]
            val locStore = (state?.decodedStore as? DecodedContractStore.Location)?.store
            val memberCount = locStore?.memberCount ?: 0L
            val treasuryBalance = state?.balanceNano ?: BigInteger.ZERO

            entities.add(
                WatchedLocationEntity(
                    h3Cell = h3Cell,
                    locationContractAddress = locAddr,
                    label = label,
                    countryCode = countryCode,
                    memberCount = memberCount,
                    treasuryBalanceNano = treasuryBalance.toString(),
                    isPrimaryResidence = isPrimary,
                    updatedAtMs = now,
                )
            )
            results.add(
                HydratedCityLocation(
                    h3Cell = h3Cell,
                    locationAddress = locAddr,
                    label = label,
                    countryCode = countryCode,
                    memberCount = memberCount,
                    treasuryBalanceNano = treasuryBalance,
                    isPrimaryResidence = isPrimary,
                    store = locStore,
                )
            )
        }

        if (entities.isNotEmpty()) {
            dao.upsertWatchedLocations(entities)
        }
        return results
    }

    suspend fun addWatchedLocation(
        h3Cell: String,
        label: String? = null,
        countryCode: Int = 356,
    ): WatchedLocationEntity? {
        val clean = H3Helper.normalizeH3Cell(h3Cell)
        if (!H3Helper.isValidH3Cell(clean)) {
            return null
        }
        val locAddr = ShardedAddressDerivation.deriveLocation(clean).address.toAccountId()
        val state = hydrator.hydrateAddress(locAddr, KnownContractType.LOCATION, forceRefresh = true)
        val locStore = (state?.decodedStore as? DecodedContractStore.Location)?.store
        val preset = H3Helper.PRESET_REGIONS.firstOrNull { it.h3Cell == clean }
        val entity = WatchedLocationEntity(
            h3Cell = clean,
            locationContractAddress = locAddr,
            label = label?.takeIf { it.isNotBlank() } ?: preset?.name ?: "H3 Region $clean",
            countryCode = preset?.countryCode ?: countryCode,
            memberCount = locStore?.memberCount ?: 0L,
            treasuryBalanceNano = (state?.balanceNano ?: BigInteger.ZERO).toString(),
            isPrimaryResidence = false,
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.upsertWatchedLocation(entity)
        return entity
    }

    fun buildUpdateResidenceIntent(
        fiWalletAddress: String,
        h3Cell: String,
        countryCode: Int,
    ): BrotherhoodTransferIntent {
        val clean = H3Helper.normalizeH3Cell(h3Cell)
        return BrotherhoodTransferIntent(
            title = "Set Primary City Residence ($clean)",
            subtitle = "Register residence in sharded H3 Location contract",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(fiWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.PROFILE_CHANGE_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildChangeProfile(
                        h3Cell = clean,
                        country = countryCode,
                    ),
                )
            ),
        )
    }
}

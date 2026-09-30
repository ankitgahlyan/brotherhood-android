package com.tonapps.brotherhood.store

import com.tonapps.brotherhood.store.TolkSliceUtils.cellFromBase64
import com.tonapps.brotherhood.store.TolkSliceUtils.loadBytes
import com.tonapps.brotherhood.config.KnownCodeHashes
import com.tonapps.brotherhood.config.KnownContractType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface DecodedContractStore {
    val contractType: KnownContractType

    @Serializable
    data class FiWallet(val store: FiWalletStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.FI_WALLET
    }

    @Serializable
    data class FiMinter(val store: FiStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.FI_MINTER
    }

    @Serializable
    data class PersonalMinter(val store: PersonalStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.PERSONAL_MINTER
    }

    @Serializable
    data class PersonalWallet(val store: PersonalWalletStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.PERSONAL_WALLET
    }

    @Serializable
    data class Location(val store: LocationStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.LOCATION
    }

    @Serializable
    data class Lottery(val store: LotteryStorage) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.LOTTERY
    }

    @Serializable
    data class Poll(val store: PollStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.POLL
    }

    @Serializable
    data class Voter(val store: VoterStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.VOTER
    }

    @Serializable
    data class Holding(val store: HoldingStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.HOLDING
    }

    @Serializable
    data class Following(val store: FollowingStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.FOLLOWING
    }

    @Serializable
    data class DnsItem(val store: DnsItemStore) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.DNS_ITEM
    }

    @Serializable
    data class WalletV5R1(
        val isSignatureAllowed: Boolean,
        val seqno: Long,
        val walletId: Long,
        val publicKeyHex: String,
    ) : DecodedContractStore {
        override val contractType: KnownContractType = KnownContractType.WALLET_V5_R1
    }
}

object UniversalBocDeserializer {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun deserializeAccountDataBoc(
        dataBocBase64: String,
        codeHash: String? = null,
        explicitType: KnownContractType? = null,
    ): DecodedContractStore? {
        if (dataBocBase64.isBlank()) {
            return null
        }
        val cell = runCatching { dataBocBase64.cellFromBase64() }.getOrNull() ?: return null
        val detectedType = KnownCodeHashes.detectKnownType(codeHash, explicitType)

        return runCatching {
            when (detectedType) {
                KnownContractType.FI_WALLET -> DecodedContractStore.FiWallet(FiWalletStore.fromCell(cell))
                KnownContractType.FI_MINTER -> DecodedContractStore.FiMinter(FiStore.fromCell(cell))
                KnownContractType.PERSONAL_MINTER -> DecodedContractStore.PersonalMinter(PersonalStore.fromCell(cell))
                KnownContractType.PERSONAL_WALLET -> DecodedContractStore.PersonalWallet(PersonalWalletStore.fromCell(cell))
                KnownContractType.LOCATION -> DecodedContractStore.Location(LocationStore.fromCell(cell))
                KnownContractType.LOTTERY -> DecodedContractStore.Lottery(LotteryStorage.fromCell(cell))
                KnownContractType.POLL -> DecodedContractStore.Poll(PollStore.fromCell(cell))
                KnownContractType.VOTER -> DecodedContractStore.Voter(VoterStore.fromCell(cell))
                KnownContractType.HOLDING -> DecodedContractStore.Holding(HoldingStore.fromCell(cell))
                KnownContractType.FOLLOWING -> DecodedContractStore.Following(FollowingStore.fromCell(cell))
                KnownContractType.DNS_ITEM -> DecodedContractStore.DnsItem(DnsItemStore.fromCell(cell))
                KnownContractType.WALLET_V5_R1 -> {
                    val s = cell.beginParse()
                    val sigAllowed = s.loadBit()
                    val seqno = s.loadUInt(32).toLong()
                    val walletId = s.loadInt(32).toLong()
                    val pubKeyBytes = s.loadBytes(32)
                    val pubKeyHex = pubKeyBytes.joinToString("") { "%02x".format(it) }
                    DecodedContractStore.WalletV5R1(
                        isSignatureAllowed = sigAllowed,
                        seqno = seqno,
                        walletId = walletId,
                        publicKeyHex = pubKeyHex,
                    )
                }
                KnownContractType.UNKNOWN -> tryHeuristicDecode(cell)
            }
        }.getOrNull()
    }

    private fun tryHeuristicDecode(cell: org.ton.cell.Cell): DecodedContractStore? {
        val slice = cell.beginParse()
        // FiWallet has 4 refs (ProfileInfo, TimeStamps, Addresses, Maps)
        if (slice.refs.size == 4) {
            runCatching { return DecodedContractStore.FiWallet(FiWalletStore.fromCell(cell)) }
        }
        // Poll has 2 refs (PollAddresses, targetMsg) and 164 bits
        if (slice.refs.size == 2 && slice.bits.size == 164) {
            runCatching { return DecodedContractStore.Poll(PollStore.fromCell(cell)) }
        }
        // PersonalWallet has 0 refs and 3 addresses + coins + 10-bit version
        if (slice.refs.size == 0 && slice.bits.size > 800) {
            runCatching { return DecodedContractStore.PersonalWallet(PersonalWalletStore.fromCell(cell)) }
        }
        // Holding has 0 refs and 2 addresses + coins + 64-bit queryId + 32-bit createdAt
        if (slice.refs.size == 0 && slice.bits.size in 630..760) {
            runCatching { return DecodedContractStore.Holding(HoldingStore.fromCell(cell)) }
        }
        // Voter has 0 refs and 2 addresses + 2 bits (536 bits)
        if (slice.refs.size == 0 && slice.bits.size == 536) {
            runCatching { return DecodedContractStore.Voter(VoterStore.fromCell(cell)) }
        }
        return null
    }

    fun encodeToJson(store: DecodedContractStore): String {
        return json.encodeToString(DecodedContractStore.serializer(), store)
    }

    fun decodeFromJson(rawJson: String): DecodedContractStore? {
        return runCatching {
            json.decodeFromString(DecodedContractStore.serializer(), rawJson)
        }.getOrNull()
    }
}

package com.tonapps.wallet.data.brotherhood.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "brotherhood_contract_cache",
    indices = [
        Index(value = ["contractType"]),
        Index(value = ["updatedAtMs"]),
    ],
)
data class ContractCacheEntity(
    @PrimaryKey
    val address: String,
    val contractType: String,
    val codeHash: String?,
    val dataHash: String?,
    val dataBocBase64: String?,
    val balanceNano: String,
    val status: String,
    val lastTxLt: String?,
    val lastTxHash: String?,
    val parsedStoreJson: String?,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_token_metadata_cache",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["isVerifiedEcosystemToken"]),
    ],
)
data class TokenMetadataCacheEntity(
    @PrimaryKey
    val minterAddress: String,
    val name: String,
    val symbol: String,
    val description: String?,
    val imageUrl: String?,
    val decimals: Int,
    val adminAddress: String?,
    val isPersonalToken: Boolean,
    val isVerifiedEcosystemToken: Boolean,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_address_book_cache",
    indices = [
        Index(value = ["username"]),
        Index(value = ["ownerAddress"]),
        Index(value = ["h3Cell"]),
    ],
)
data class AddressBookCacheEntity(
    @PrimaryKey
    val fiWalletAddress: String,
    val ownerAddress: String,
    val username: String,
    val h3Cell: String,
    val countryCode: Int,
    val isActive: Boolean,
    val isAuthority: Boolean,
    val personalMinterAddress: String?,
    val relationType: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_watched_locations",
    indices = [
        Index(value = ["locationContractAddress"]),
    ],
)
data class WatchedLocationEntity(
    @PrimaryKey
    val h3Cell: String,
    val locationContractAddress: String,
    val label: String,
    val countryCode: Int,
    val memberCount: Long,
    val treasuryBalanceNano: String,
    val isPrimaryResidence: Boolean,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_tracked_personal_tokens",
    indices = [
        Index(value = ["ownerWalletAddress"]),
        Index(value = ["isAdminReferenceStablecoin"]),
    ],
)
data class TrackedPersonalTokenEntity(
    @PrimaryKey
    val minterAddress: String,
    val ownerWalletAddress: String,
    val ownerFiWalletAddress: String,
    val userPersonalWalletAddress: String,
    val name: String,
    val symbol: String,
    val imageUrl: String?,
    val totalSupplyRaw: String,
    val userBalanceRaw: String,
    val isAdminReferenceStablecoin: Boolean,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_known_polls",
    indices = [
        Index(value = ["pollId"]),
        Index(value = ["source"]),
    ],
)
data class KnownPollEntity(
    @PrimaryKey
    val pollAddress: String,
    val pollId: Long,
    val proposerAddress: String,
    val description: String,
    val actionType: Int,
    val startTime: Long,
    val durationSeconds: Long,
    val yesCount: Long,
    val noCount: Long,
    val passed: Boolean,
    val ended: Boolean,
    val source: String,
    val locationH3Cell: String?,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "brotherhood_watched_dns_domains",
    indices = [
        Index(value = ["dnsItemAddress"]),
        Index(value = ["isOwnedByUser"]),
    ],
)
data class WatchedDnsDomainEntity(
    @PrimaryKey
    val domainName: String,
    val dnsItemAddress: String,
    val ownerAddress: String?,
    val resolvedWalletAddress: String?,
    val maxBidAmountRaw: String,
    val maxBidAddress: String?,
    val auctionEndTime: Long,
    val lastFillUpTime: Long,
    val isOwnedByUser: Boolean,
    val updatedAtMs: Long,
)

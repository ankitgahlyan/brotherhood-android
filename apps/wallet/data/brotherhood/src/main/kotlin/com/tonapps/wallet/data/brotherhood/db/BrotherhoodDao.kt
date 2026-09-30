package com.tonapps.wallet.data.brotherhood.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrotherhoodDao {

    // =========================================================================
    // Contract Cache
    // =========================================================================

    @Query("SELECT * FROM brotherhood_contract_cache WHERE address = :address LIMIT 1")
    suspend fun getContractCache(address: String): ContractCacheEntity?

    @Query("SELECT * FROM brotherhood_contract_cache WHERE address = :address LIMIT 1")
    fun observeContractCache(address: String): Flow<ContractCacheEntity?>

    @Query("SELECT * FROM brotherhood_contract_cache WHERE address IN (:addresses)")
    suspend fun getContractCaches(addresses: List<String>): List<ContractCacheEntity>

    @Query("SELECT * FROM brotherhood_contract_cache WHERE address IN (:addresses)")
    fun observeContractCaches(addresses: List<String>): Flow<List<ContractCacheEntity>>

    @Query("SELECT * FROM brotherhood_contract_cache WHERE contractType = :contractType ORDER BY updatedAtMs DESC")
    fun observeContractsByType(contractType: String): Flow<List<ContractCacheEntity>>

    @Query("SELECT COUNT(*) FROM brotherhood_contract_cache")
    fun observeContractCacheCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContractCache(entity: ContractCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContractCaches(entities: List<ContractCacheEntity>)

    @Query("DELETE FROM brotherhood_contract_cache WHERE updatedAtMs < :olderThanMs")
    suspend fun pruneContractCache(olderThanMs: Long)

    @Query("DELETE FROM brotherhood_contract_cache")
    suspend fun clearAllContractCache()

    // =========================================================================
    // Token Metadata Cache
    // =========================================================================

    @Query("SELECT * FROM brotherhood_token_metadata_cache WHERE minterAddress = :minterAddress LIMIT 1")
    suspend fun getTokenMetadata(minterAddress: String): TokenMetadataCacheEntity?

    @Query("SELECT * FROM brotherhood_token_metadata_cache WHERE isVerifiedEcosystemToken = 1 ORDER BY symbol ASC")
    fun observeVerifiedEcosystemTokens(): Flow<List<TokenMetadataCacheEntity>>

    @Query("SELECT * FROM brotherhood_token_metadata_cache ORDER BY symbol ASC")
    fun observeAllTokenMetadata(): Flow<List<TokenMetadataCacheEntity>>

    @Query("SELECT * FROM brotherhood_token_metadata_cache ORDER BY symbol ASC")
    suspend fun getAllTokenMetadata(): List<TokenMetadataCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTokenMetadata(entity: TokenMetadataCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTokenMetadataList(entities: List<TokenMetadataCacheEntity>)

    // =========================================================================
    // Address Book Cache (@username -> FiWallet / Owner)
    // =========================================================================

    @Query("SELECT * FROM brotherhood_address_book_cache ORDER BY username ASC")
    fun observeAddressBook(): Flow<List<AddressBookCacheEntity>>

    @Query("SELECT * FROM brotherhood_address_book_cache WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findContactByUsername(username: String): AddressBookCacheEntity?

    @Query(
        "SELECT * FROM brotherhood_address_book_cache " +
            "WHERE LOWER(username) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(ownerAddress) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(fiWalletAddress) LIKE '%' || LOWER(:query) || '%' " +
            "ORDER BY username ASC LIMIT 50"
    )
    suspend fun searchContacts(query: String): List<AddressBookCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContacts(contacts: List<AddressBookCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: AddressBookCacheEntity)

    // =========================================================================
    // Watched H3 Locations (City Network)
    // =========================================================================

    @Query("SELECT * FROM brotherhood_watched_locations ORDER BY isPrimaryResidence DESC, memberCount DESC")
    fun observeWatchedLocations(): Flow<List<WatchedLocationEntity>>

    @Query("SELECT * FROM brotherhood_watched_locations")
    suspend fun getAllWatchedLocations(): List<WatchedLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedLocation(entity: WatchedLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedLocations(entities: List<WatchedLocationEntity>)

    @Query("DELETE FROM brotherhood_watched_locations WHERE h3Cell = :h3Cell")
    suspend fun deleteWatchedLocation(h3Cell: String)

    // =========================================================================
    // Tracked Personal Tokens
    // =========================================================================

    @Query("SELECT * FROM brotherhood_tracked_personal_tokens ORDER BY isAdminReferenceStablecoin DESC, symbol ASC")
    fun observeTrackedPersonalTokens(): Flow<List<TrackedPersonalTokenEntity>>

    @Query("SELECT * FROM brotherhood_tracked_personal_tokens")
    suspend fun getAllTrackedPersonalTokens(): List<TrackedPersonalTokenEntity>

    @Query("SELECT * FROM brotherhood_tracked_personal_tokens WHERE isAdminReferenceStablecoin = 1 LIMIT 1")
    suspend fun getAdminReferenceStablecoin(): TrackedPersonalTokenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackedPersonalToken(entity: TrackedPersonalTokenEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackedPersonalTokens(entities: List<TrackedPersonalTokenEntity>)

    @Query("DELETE FROM brotherhood_tracked_personal_tokens WHERE minterAddress = :minterAddress")
    suspend fun deleteTrackedPersonalToken(minterAddress: String)

    // =========================================================================
    // Known DAO & City Polls
    // =========================================================================

    @Query("SELECT * FROM brotherhood_known_polls ORDER BY pollId DESC, startTime DESC")
    fun observeKnownPolls(): Flow<List<KnownPollEntity>>

    @Query("SELECT * FROM brotherhood_known_polls WHERE source = :source ORDER BY pollId DESC")
    fun observePollsBySource(source: String): Flow<List<KnownPollEntity>>

    @Query("SELECT * FROM brotherhood_known_polls")
    suspend fun getAllKnownPolls(): List<KnownPollEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnownPoll(entity: KnownPollEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnownPolls(entities: List<KnownPollEntity>)

    // =========================================================================
    // Watched .bro DNS Domains
    // =========================================================================

    @Query("SELECT * FROM brotherhood_watched_dns_domains ORDER BY isOwnedByUser DESC, domainName ASC")
    fun observeWatchedDnsDomains(): Flow<List<WatchedDnsDomainEntity>>

    @Query("SELECT * FROM brotherhood_watched_dns_domains WHERE domainName = :domainName LIMIT 1")
    suspend fun getWatchedDnsDomain(domainName: String): WatchedDnsDomainEntity?

    @Query("SELECT * FROM brotherhood_watched_dns_domains")
    suspend fun getAllWatchedDnsDomains(): List<WatchedDnsDomainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedDnsDomain(entity: WatchedDnsDomainEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedDnsDomains(entities: List<WatchedDnsDomainEntity>)

    @Query("DELETE FROM brotherhood_watched_dns_domains WHERE domainName = :domainName")
    suspend fun deleteWatchedDnsDomain(domainName: String)
}

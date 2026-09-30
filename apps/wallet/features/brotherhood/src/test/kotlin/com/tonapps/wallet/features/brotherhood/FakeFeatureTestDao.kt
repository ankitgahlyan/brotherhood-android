package com.tonapps.wallet.features.brotherhood

import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.ContractCacheEntity
import com.tonapps.wallet.data.brotherhood.db.KnownPollEntity
import com.tonapps.wallet.data.brotherhood.db.TokenMetadataCacheEntity
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.db.WatchedDnsDomainEntity
import com.tonapps.wallet.data.brotherhood.db.WatchedLocationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeFeatureTestDao : BrotherhoodDao {
    private val contracts = mutableMapOf<String, ContractCacheEntity>()
    private val contacts = mutableMapOf<String, AddressBookCacheEntity>()
    private val tokenMeta = mutableMapOf<String, TokenMetadataCacheEntity>()
    private val locations = mutableMapOf<String, WatchedLocationEntity>()
    private val personalTokens = mutableMapOf<String, TrackedPersonalTokenEntity>()
    private val polls = mutableMapOf<String, KnownPollEntity>()
    private val dnsDomains = mutableMapOf<String, WatchedDnsDomainEntity>()

    override suspend fun getContractCache(address: String): ContractCacheEntity? = contracts[address]

    override fun observeContractCache(address: String): Flow<ContractCacheEntity?> = flowOf(contracts[address])

    override suspend fun getContractCaches(addresses: List<String>): List<ContractCacheEntity> {
        return addresses.mapNotNull { contracts[it] }
    }

    override fun observeContractCaches(addresses: List<String>): Flow<List<ContractCacheEntity>> {
        return flowOf(addresses.mapNotNull { contracts[it] })
    }

    override fun observeContractsByType(contractType: String): Flow<List<ContractCacheEntity>> {
        return flowOf(contracts.values.filter { it.contractType == contractType })
    }

    override fun observeContractCacheCount(): Flow<Int> = flowOf(contracts.size)

    override suspend fun upsertContractCache(entity: ContractCacheEntity) {
        contracts[entity.address] = entity
    }

    override suspend fun upsertContractCaches(entities: List<ContractCacheEntity>) {
        for (entity in entities) {
            contracts[entity.address] = entity
        }
    }

    override suspend fun pruneContractCache(olderThanMs: Long) {
        contracts.entries.removeIf { it.value.updatedAtMs < olderThanMs }
    }

    override suspend fun clearAllContractCache() {
        contracts.clear()
    }

    override suspend fun getTokenMetadata(minterAddress: String): TokenMetadataCacheEntity? = tokenMeta[minterAddress]

    override fun observeVerifiedEcosystemTokens(): Flow<List<TokenMetadataCacheEntity>> {
        return flowOf(tokenMeta.values.filter { it.isVerifiedEcosystemToken })
    }

    override fun observeAllTokenMetadata(): Flow<List<TokenMetadataCacheEntity>> = flowOf(tokenMeta.values.toList())

    override suspend fun getAllTokenMetadata(): List<TokenMetadataCacheEntity> = tokenMeta.values.toList()

    override suspend fun upsertTokenMetadata(entity: TokenMetadataCacheEntity) {
        tokenMeta[entity.minterAddress] = entity
    }

    override suspend fun upsertTokenMetadataList(entities: List<TokenMetadataCacheEntity>) {
        for (entity in entities) {
            tokenMeta[entity.minterAddress] = entity
        }
    }

    override fun observeAddressBook(): Flow<List<AddressBookCacheEntity>> = flowOf(contacts.values.toList())

    override suspend fun findContactByUsername(username: String): AddressBookCacheEntity? {
        return contacts.values.firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    override suspend fun searchContacts(query: String): List<AddressBookCacheEntity> {
        return contacts.values.filter { it.username.contains(query, ignoreCase = true) }
    }

    override suspend fun upsertContacts(contacts: List<AddressBookCacheEntity>) {
        for (contact in contacts) {
            this.contacts[contact.fiWalletAddress] = contact
        }
    }

    override suspend fun upsertContact(contact: AddressBookCacheEntity) {
        contacts[contact.fiWalletAddress] = contact
    }

    override fun observeWatchedLocations(): Flow<List<WatchedLocationEntity>> = flowOf(locations.values.toList())

    override suspend fun getAllWatchedLocations(): List<WatchedLocationEntity> = locations.values.toList()

    override suspend fun upsertWatchedLocation(entity: WatchedLocationEntity) {
        locations[entity.h3Cell] = entity
    }

    override suspend fun upsertWatchedLocations(entities: List<WatchedLocationEntity>) {
        for (entity in entities) {
            locations[entity.h3Cell] = entity
        }
    }

    override suspend fun deleteWatchedLocation(h3Cell: String) {
        locations.remove(h3Cell)
    }

    override fun observeTrackedPersonalTokens(): Flow<List<TrackedPersonalTokenEntity>> {
        return flowOf(personalTokens.values.toList())
    }

    override suspend fun getAllTrackedPersonalTokens(): List<TrackedPersonalTokenEntity> {
        return personalTokens.values.toList()
    }

    override suspend fun getAdminReferenceStablecoin(): TrackedPersonalTokenEntity? {
        return personalTokens.values.firstOrNull { it.isAdminReferenceStablecoin }
    }

    override suspend fun upsertTrackedPersonalToken(entity: TrackedPersonalTokenEntity) {
        personalTokens[entity.minterAddress] = entity
    }

    override suspend fun upsertTrackedPersonalTokens(entities: List<TrackedPersonalTokenEntity>) {
        for (entity in entities) {
            personalTokens[entity.minterAddress] = entity
        }
    }

    override suspend fun deleteTrackedPersonalToken(minterAddress: String) {
        personalTokens.remove(minterAddress)
    }

    override fun observeKnownPolls(): Flow<List<KnownPollEntity>> = flowOf(polls.values.toList())

    override fun observePollsBySource(source: String): Flow<List<KnownPollEntity>> {
        return flowOf(polls.values.filter { it.source == source })
    }

    override suspend fun getAllKnownPolls(): List<KnownPollEntity> = polls.values.toList()

    override suspend fun upsertKnownPoll(entity: KnownPollEntity) {
        polls[entity.pollAddress] = entity
    }

    override suspend fun upsertKnownPolls(entities: List<KnownPollEntity>) {
        for (entity in entities) {
            polls[entity.pollAddress] = entity
        }
    }

    override fun observeWatchedDnsDomains(): Flow<List<WatchedDnsDomainEntity>> {
        return flowOf(dnsDomains.values.toList())
    }

    override suspend fun getWatchedDnsDomain(domainName: String): WatchedDnsDomainEntity? = dnsDomains[domainName]

    override suspend fun getAllWatchedDnsDomains(): List<WatchedDnsDomainEntity> = dnsDomains.values.toList()

    override suspend fun upsertWatchedDnsDomain(entity: WatchedDnsDomainEntity) {
        dnsDomains[entity.domainName] = entity
    }

    override suspend fun upsertWatchedDnsDomains(entities: List<WatchedDnsDomainEntity>) {
        for (entity in entities) {
            dnsDomains[entity.domainName] = entity
        }
    }

    override suspend fun deleteWatchedDnsDomain(domainName: String) {
        dnsDomains.remove(domainName)
    }
}

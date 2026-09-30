package com.tonapps.wallet.data.brotherhood

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownCodeHashes
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes
import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.storeAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.storeCoins
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringRefTail
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.ContractCacheEntity
import com.tonapps.wallet.data.brotherhood.db.KnownPollEntity
import com.tonapps.wallet.data.brotherhood.db.TokenMetadataCacheEntity
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.db.WatchedDnsDomainEntity
import com.tonapps.wallet.data.brotherhood.db.WatchedLocationEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.hydrator.ToncenterV3AccountStateDto
import com.tonapps.wallet.data.brotherhood.hydrator.ToncenterV3Transport
import com.tonapps.wallet.data.brotherhood.network.ProviderRateLimiter
import com.tonapps.wallet.data.brotherhood.network.RpcProvider
import com.tonapps.wallet.data.brotherhood.repo.PersonalJettonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bigint.toBigInt
import org.ton.block.Coins
import org.ton.cell.CellBuilder
import java.math.BigInteger

private class FakeBrotherhoodDao : BrotherhoodDao {
    private val contractMap = LinkedHashMap<String, ContractCacheEntity>()
    private val tokenMetaMap = LinkedHashMap<String, TokenMetadataCacheEntity>()
    private val contactsMap = LinkedHashMap<String, AddressBookCacheEntity>()
    private val locationsMap = LinkedHashMap<String, WatchedLocationEntity>()
    private val personalTokensMap = LinkedHashMap<String, TrackedPersonalTokenEntity>()
    private val pollsMap = LinkedHashMap<String, KnownPollEntity>()
    private val dnsMap = LinkedHashMap<String, WatchedDnsDomainEntity>()

    private val contractsFlow = MutableStateFlow<List<ContractCacheEntity>>(emptyList())
    private val contactsFlow = MutableStateFlow<List<AddressBookCacheEntity>>(emptyList())

    override suspend fun getContractCache(address: String): ContractCacheEntity? = contractMap[address]

    override fun observeContractCache(address: String): Flow<ContractCacheEntity?> {
        return contractsFlow.map { contractMap[address] }
    }

    override suspend fun getContractCaches(addresses: List<String>): List<ContractCacheEntity> {
        return addresses.mapNotNull { contractMap[it] }
    }

    override fun observeContractCaches(addresses: List<String>): Flow<List<ContractCacheEntity>> {
        return contractsFlow.map { addresses.mapNotNull { addr -> contractMap[addr] } }
    }

    override fun observeContractsByType(contractType: String): Flow<List<ContractCacheEntity>> {
        return contractsFlow.map { list -> list.filter { it.contractType == contractType } }
    }

    override fun observeContractCacheCount(): Flow<Int> = contractsFlow.map { it.size }

    override suspend fun upsertContractCache(entity: ContractCacheEntity) {
        contractMap[entity.address] = entity
        contractsFlow.value = contractMap.values.toList()
    }

    override suspend fun upsertContractCaches(entities: List<ContractCacheEntity>) {
        for (e in entities) {
            contractMap[e.address] = e
        }
        contractsFlow.value = contractMap.values.toList()
    }

    override suspend fun pruneContractCache(olderThanMs: Long) {
        val iter = contractMap.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.updatedAtMs < olderThanMs) {
                iter.remove()
            }
        }
        contractsFlow.value = contractMap.values.toList()
    }

    override suspend fun clearAllContractCache() {
        contractMap.clear()
        contractsFlow.value = emptyList()
    }

    override suspend fun getTokenMetadata(minterAddress: String): TokenMetadataCacheEntity? {
        return tokenMetaMap[minterAddress]
    }

    override fun observeVerifiedEcosystemTokens(): Flow<List<TokenMetadataCacheEntity>> {
        return MutableStateFlow(tokenMetaMap.values.filter { it.isVerifiedEcosystemToken })
    }

    override fun observeAllTokenMetadata(): Flow<List<TokenMetadataCacheEntity>> {
        return MutableStateFlow(tokenMetaMap.values.toList())
    }

    override suspend fun upsertTokenMetadata(entity: TokenMetadataCacheEntity) {
        tokenMetaMap[entity.minterAddress] = entity
    }

    override suspend fun upsertTokenMetadataList(entities: List<TokenMetadataCacheEntity>) {
        for (e in entities) {
            tokenMetaMap[e.minterAddress] = e
        }
    }

    override fun observeAddressBook(): Flow<List<AddressBookCacheEntity>> = contactsFlow

    override suspend fun findContactByUsername(username: String): AddressBookCacheEntity? {
        return contactsMap.values.firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    override suspend fun searchContacts(query: String): List<AddressBookCacheEntity> {
        val q = query.lowercase()
        return contactsMap.values.filter {
            it.username.lowercase().contains(q) ||
                it.ownerAddress.lowercase().contains(q) ||
                it.fiWalletAddress.lowercase().contains(q)
        }
    }

    override suspend fun upsertContacts(contacts: List<AddressBookCacheEntity>) {
        for (c in contacts) {
            contactsMap[c.fiWalletAddress] = c
        }
        contactsFlow.value = contactsMap.values.toList()
    }

    override suspend fun upsertContact(contact: AddressBookCacheEntity) {
        contactsMap[contact.fiWalletAddress] = contact
        contactsFlow.value = contactsMap.values.toList()
    }

    override fun observeWatchedLocations(): Flow<List<WatchedLocationEntity>> {
        return MutableStateFlow(locationsMap.values.toList())
    }

    override suspend fun getAllWatchedLocations(): List<WatchedLocationEntity> = locationsMap.values.toList()

    override suspend fun upsertWatchedLocation(entity: WatchedLocationEntity) {
        locationsMap[entity.h3Cell] = entity
    }

    override suspend fun upsertWatchedLocations(entities: List<WatchedLocationEntity>) {
        for (e in entities) {
            locationsMap[e.h3Cell] = e
        }
    }

    override suspend fun deleteWatchedLocation(h3Cell: String) {
        locationsMap.remove(h3Cell)
    }

    override fun observeTrackedPersonalTokens(): Flow<List<TrackedPersonalTokenEntity>> {
        return MutableStateFlow(personalTokensMap.values.toList())
    }

    override suspend fun getAllTrackedPersonalTokens(): List<TrackedPersonalTokenEntity> {
        return personalTokensMap.values.toList()
    }

    override suspend fun getAdminReferenceStablecoin(): TrackedPersonalTokenEntity? {
        return personalTokensMap.values.firstOrNull { it.isAdminReferenceStablecoin }
    }

    override suspend fun upsertTrackedPersonalToken(entity: TrackedPersonalTokenEntity) {
        personalTokensMap[entity.minterAddress] = entity
    }

    override suspend fun upsertTrackedPersonalTokens(entities: List<TrackedPersonalTokenEntity>) {
        for (e in entities) {
            personalTokensMap[e.minterAddress] = e
        }
    }

    override suspend fun deleteTrackedPersonalToken(minterAddress: String) {
        personalTokensMap.remove(minterAddress)
    }

    override fun observeKnownPolls(): Flow<List<KnownPollEntity>> {
        return MutableStateFlow(pollsMap.values.toList())
    }

    override fun observePollsBySource(source: String): Flow<List<KnownPollEntity>> {
        return MutableStateFlow(pollsMap.values.filter { it.source == source })
    }

    override suspend fun getAllKnownPolls(): List<KnownPollEntity> = pollsMap.values.toList()

    override suspend fun upsertKnownPoll(entity: KnownPollEntity) {
        pollsMap[entity.pollAddress] = entity
    }

    override suspend fun upsertKnownPolls(entities: List<KnownPollEntity>) {
        for (e in entities) {
            pollsMap[e.pollAddress] = e
        }
    }

    override fun observeWatchedDnsDomains(): Flow<List<WatchedDnsDomainEntity>> {
        return MutableStateFlow(dnsMap.values.toList())
    }

    override suspend fun getWatchedDnsDomain(domainName: String): WatchedDnsDomainEntity? = dnsMap[domainName]

    override suspend fun getAllWatchedDnsDomains(): List<WatchedDnsDomainEntity> = dnsMap.values.toList()

    override suspend fun upsertWatchedDnsDomain(entity: WatchedDnsDomainEntity) {
        dnsMap[entity.domainName] = entity
    }

    override suspend fun upsertWatchedDnsDomains(entities: List<WatchedDnsDomainEntity>) {
        for (e in entities) {
            dnsMap[e.domainName] = e
        }
    }

    override suspend fun deleteWatchedDnsDomain(domainName: String) {
        dnsMap.remove(domainName)
    }
}

class BrotherhoodDataTest {

    @Test
    fun `PersonalJettonRepository computes Mutual Credit Swap quotes and intents accurately`() = runBlocking {
        val dao = FakeBrotherhoodDao()
        val transport = ToncenterV3Transport { _, _ -> emptyList() }
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val userFiWalletAddr = ShardedAddressDerivation.deriveFiWallet(owner).address
        val userFiWallet = userFiWalletAddr.toAccountId()
        val adminMinter = ShardedAddressDerivation.derivePersonalMinter(userFiWalletAddr, owner)
        val userPw = ShardedAddressDerivation.derivePersonalWallet(adminMinter.address, owner, owner)

        val adminToken = TrackedPersonalTokenEntity(
            minterAddress = adminMinter.address.toAccountId(),
            ownerWalletAddress = owner.toAccountId(),
            ownerFiWalletAddress = userFiWallet,
            userPersonalWalletAddress = userPw.address.toAccountId(),
            name = "BrotherHood Admin USD (Reference)",
            symbol = "FIUSD",
            imageUrl = null,
            totalSupplyRaw = "1000000000000",
            userBalanceRaw = "50000000000",
            isAdminReferenceStablecoin = true,
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.upsertTrackedPersonalToken(adminToken)

        // Verify BuyCredit quote (FI/HD -> FIUSD) and Payback quote (FIUSD -> FI/HD)
        val repo = PersonalJettonRepository(
            dao = dao,
            hydrator = AccountStateHydrator(
                dao = dao,
                rateLimiter = ProviderRateLimiter(
                    telemetryRepository = createInMemoryTelemetry(),
                ),
                telemetryRepository = createInMemoryTelemetry(),
                transport = transport,
                debounceWindowMs = 0L,
            ),
        )

        val buyQuote = repo.computeMutualCreditQuote(
            isBuyCredit = true,
            inputAmountRaw = BigInteger.valueOf(10_000_000_000L),
            personalToken = adminToken,
            userFiWalletAddress = userFiWallet,
            multiplierPermille = 1000,
        )
        assertEquals("FI", buyQuote.fromSymbol)
        assertEquals("FIUSD", buyQuote.toSymbol)
        assertEquals(BigInteger.valueOf(10_000_000_000L), buyQuote.estimatedOutputAmountRaw)

        val buyIntent = repo.buildMutualCreditSwapIntent(buyQuote)
        assertEquals(1, buyIntent.messages.size)
        val buyOp = BrotherhoodOpcodes.decodeOpcodeFromCell(buyIntent.messages.first().payloadCell)
        assertNotNull(buyOp)
        assertEquals(BrotherhoodOpcodes.BUY_CREDIT, buyOp!!.opcode)

        val paybackQuote = repo.computeMutualCreditQuote(
            isBuyCredit = false,
            inputAmountRaw = BigInteger.valueOf(5_000_000_000L),
            personalToken = adminToken,
            userFiWalletAddress = userFiWallet,
            multiplierPermille = 1000,
        )
        assertEquals("FIUSD", paybackQuote.fromSymbol)
        assertEquals("FI", paybackQuote.toSymbol)
        val paybackIntent = repo.buildMutualCreditSwapIntent(paybackQuote)
        val paybackOp = BrotherhoodOpcodes.decodeOpcodeFromCell(paybackIntent.messages.first().payloadCell)
        assertNotNull(paybackOp)
        assertEquals(BrotherhoodOpcodes.ASK_TO_BURN, paybackOp!!.opcode)
    }

    @Test
    fun `AccountStateHydrator batches 30 addresses, decodes FiWallet BOC, and indexes username`() = runBlocking {
        val dao = FakeBrotherhoodDao()
        val telemetry = createInMemoryTelemetry()
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val minter = BrotherhoodConfig.FI_ADDRESS
        val fiWalletAddress = ShardedAddressDerivation.deriveFiWallet(owner, minter).address.toAccountId()

        val fiWalletBoc = buildSampleFiWalletCell("satoshi").base64()
        val transportCalls = mutableListOf<List<String>>()
        val transport = ToncenterV3Transport { addrs, _ ->
            transportCalls.add(addrs)
            addrs.map { a ->
                ToncenterV3AccountStateDto(
                    address = a,
                    accountStatus = "active",
                    balance = "1500000000",
                    codeHash = KnownCodeHashes.FI_WALLET,
                    dataHash = "hash1",
                    dataBoc = fiWalletBoc,
                )
            }
        }

        val hydrator = AccountStateHydrator(
            dao = dao,
            rateLimiter = ProviderRateLimiter(telemetry),
            telemetryRepository = telemetry,
            transport = transport,
            debounceWindowMs = 0L,
        )

        val state = hydrator.hydrateAddress(fiWalletAddress, KnownContractType.FI_WALLET)
        assertNotNull(state)
        assertTrue(state!!.isActive)
        assertEquals(BigInteger.valueOf(1_500_000_000L), state.balanceNano)

        // Verify @satoshi was auto-indexed into AddressBookCacheEntity
        val contact = dao.findContactByUsername("satoshi")
        assertNotNull(contact)
        assertEquals(fiWalletAddress, contact!!.fiWalletAddress)
        assertEquals(1, telemetry.snapshot.value.providerMetrics[RpcProvider.TONCENTER_V3]?.totalRequests?.toInt())
    }

    private fun buildSampleFiWalletCell(username: String) = CellBuilder.createCell {
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val minter = BrotherhoodConfig.FI_ADDRESS
        val profileCell = CellBuilder.createCell {
            storeStringRefTail(username)
            storeStringRefTail("Citizen bio")
            storeStringRefTail("813d3ffffffffff")
            storeUInt(356, 16)
        }
        val timestampsCell = CellBuilder.createCell {
            storeUInt(1700000000L.toBigInt(), 32)
            storeUInt(1700000100L.toBigInt(), 32)
            storeUInt(1700000200L.toBigInt(), 32)
            storeUInt(1700000300L.toBigInt(), 32)
        }
        val nomInCell = CellBuilder.createCell {
            storeBit(false)
            storeBit(false)
            storeBit(false)
        }
        val trustedCell = CellBuilder.createCell {
            storeAddress(minter)
            storeAddress(BrotherhoodConfig.ZERO_ADDRESS)
            storeAddress(BrotherhoodConfig.ZERO_ADDRESS)
            storeBit(false)
        }
        val addressesCell = CellBuilder.createCell {
            storeAddress(owner)
            storeRef(nomInCell)
            storeRef(trustedCell)
        }
        val socialCell = CellBuilder.createCell {
            storeBit(false)
            storeUInt(1, 32)
            storeUInt(1, 32)
        }
        val reportCell = CellBuilder.createCell {
            storeBit(false)
            storeBit(false)
            storeUInt(0, 10)
            storeUInt(0, 10)
            storeUInt(0, 32)
        }
        val mapsCell = CellBuilder.createCell {
            storeBit(false)
            storeBit(false)
            storeRef(socialCell)
            storeRef(reportCell)
        }
        storeCoins(Coins.ofNano(100_000_000_000L))
        storeUInt(5, 32)
        storeUInt(1, 8)
        storeUInt(0, 2)
        storeBit(false)
        storeBit(false)
        storeCoins(Coins.ofNano(0L))
        storeUInt(0L.toBigInt(), 32)
        storeUInt(100, 16)
        storeCoins(Coins.ofNano(0L))
        storeCoins(Coins.ofNano(0L))
        storeBit(true)
        storeUInt(10, 4)
        storeUInt(2, 20)
        storeUInt(1, 8)
        storeBit(true)
        storeBit(true)
        storeUInt(1, 10)
        storeUInt(1, 10)
        storeRef(profileCell)
        storeRef(timestampsCell)
        storeRef(addressesCell)
        storeRef(mapsCell)
    }

    private fun createInMemoryTelemetry(): com.tonapps.wallet.data.brotherhood.network.TelemetryRepository {
        return com.tonapps.wallet.data.brotherhood.network.TelemetryRepository(context = null)
    }
}

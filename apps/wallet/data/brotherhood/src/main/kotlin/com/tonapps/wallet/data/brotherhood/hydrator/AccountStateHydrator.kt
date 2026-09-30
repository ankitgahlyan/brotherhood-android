package com.tonapps.wallet.data.brotherhood.hydrator

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownCodeHashes
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.UniversalBocDeserializer
import com.tonapps.wallet.data.brotherhood.db.AddressBookCacheEntity
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.ContractCacheEntity
import com.tonapps.wallet.data.brotherhood.db.TokenMetadataCacheEntity
import com.tonapps.wallet.data.brotherhood.network.ProviderRateLimiter
import com.tonapps.wallet.data.brotherhood.network.RateLimitedHttpException
import com.tonapps.wallet.data.brotherhood.network.RpcProvider
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigInteger
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class ToncenterV3AccountStateDto(
    @SerialName("address")
    val address: String = "",
    @SerialName("account_status")
    val accountStatus: String? = null,
    @SerialName("status")
    val statusAlt: String? = null,
    @SerialName("balance")
    val balance: String? = null,
    @SerialName("code_hash")
    val codeHash: String? = null,
    @SerialName("data_hash")
    val dataHash: String? = null,
    @SerialName("data_boc")
    val dataBoc: String? = null,
    @SerialName("last_transaction_lt")
    val lastTransactionLt: String? = null,
    @SerialName("last_transaction_hash")
    val lastTransactionHash: String? = null,
) {
    val effectiveStatus: String
        get() = accountStatus ?: statusAlt ?: "uninit"
}

@Serializable
data class ToncenterV3AccountStatesResponse(
    @SerialName("accounts")
    val accounts: List<ToncenterV3AccountStateDto> = emptyList(),
)

data class HydratedAccountState(
    val address: String,
    val status: String,
    val balanceNano: BigInteger,
    val codeHash: String?,
    val dataHash: String?,
    val dataBocBase64: String?,
    val lastTxLt: String?,
    val lastTxHash: String?,
    val contractType: KnownContractType,
    val decodedStore: DecodedContractStore?,
    val updatedAtMs: Long,
) {
    val isActive: Boolean
        get() = status.equals("active", ignoreCase = true)
}

fun interface ToncenterV3Transport {
    suspend fun fetchAccountStates(
        addresses: List<String>,
        testnet: Boolean,
    ): List<ToncenterV3AccountStateDto>
}

class OkHttpToncenterV3Transport(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) : ToncenterV3Transport {

    override suspend fun fetchAccountStates(
        addresses: List<String>,
        testnet: Boolean,
    ): List<ToncenterV3AccountStateDto> = withContext(Dispatchers.IO) {
        if (addresses.isEmpty()) {
            return@withContext emptyList()
        }
        val baseUrl = BrotherhoodConfig.toncenterV3Url(testnet)
        val queryParams = addresses.joinToString("&") { addr ->
            "address=${URLEncoder.encode(addr, Charsets.UTF_8.name())}"
        }
        val url = "$baseUrl/accountStates?$queryParams&include_boc=true"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            if (code == 429) {
                val retryAfterHeader = response.header("Retry-After")?.toLongOrNull()
                val retryAfterMs = if (retryAfterHeader != null) {
                    retryAfterHeader * 1_000L
                } else {
                    null
                }
                throw RateLimitedHttpException(
                    statusCode = 429,
                    retryAfterMs = retryAfterMs,
                    message = "Toncenter v3 rate limited (HTTP 429)",
                )
            }
            if (!response.isSuccessful) {
                throw RateLimitedHttpException(
                    statusCode = code,
                    message = "Toncenter v3 HTTP $code",
                )
            }
            val bodyString = response.body.string()
            if (bodyString.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<ToncenterV3AccountStatesResponse>(bodyString).accounts
            }
        }
    }
}

/**
 * Zero-getter batch BOC account state hydrator ported from web `accountStateHydrator.ts` + `contractCache.ts`.
 *
 * - Coalesces requests within a 50ms debounce window
 * - Batches up to 30 addresses per Toncenter v3 `/api/v3/accountStates?include_boc=true` call
 * - Strips `code_boc` immediately (never stored in memory or SQLite)
 * - Decodes raw `data_boc` in-memory via `UniversalBocDeserializer` (`fromSlice`) on `Dispatchers.Default`
 * - Persists to `BrotherhoodDao` (`ContractCacheEntity`) and indexes `@username` & token metadata
 */
class AccountStateHydrator(
    private val dao: BrotherhoodDao,
    private val rateLimiter: ProviderRateLimiter,
    private val telemetryRepository: TelemetryRepository,
    private val transport: ToncenterV3Transport = OkHttpToncenterV3Transport(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceWindowMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private data class QueuedRequest(
        val normalizedAddress: String,
        val explicitType: KnownContractType?,
        val forceRefresh: Boolean,
        val deferred: CompletableDeferred<HydratedAccountState?>,
    )

    private val queueMutex = Mutex()
    private val pendingQueue = mutableListOf<QueuedRequest>()
    private var flushScheduled = false

    private val _hydratedStates = MutableStateFlow<Map<String, HydratedAccountState>>(emptyMap())
    val hydratedStates: StateFlow<Map<String, HydratedAccountState>> = _hydratedStates.asStateFlow()

    fun observeAccountState(address: String): Flow<HydratedAccountState?> {
        val norm = BrotherhoodConfig.normalizeAddress(address)
        return hydratedStates.map { it[norm] }
    }

    suspend fun hydrateAddress(
        address: String,
        explicitType: KnownContractType? = null,
        forceRefresh: Boolean = false,
        ttlMs: Long = DEFAULT_CACHE_TTL_MS,
    ): HydratedAccountState? {
        val norm = BrotherhoodConfig.normalizeAddress(address)
        if (norm.isBlank() || BrotherhoodConfig.isZeroAddress(norm)) {
            return null
        }

        if (!forceRefresh) {
            val cached = getFreshCachedState(norm, explicitType, ttlMs)
            if (cached != null) {
                telemetryRepository.recordCacheHit(1)
                return cached
            }
        }
        telemetryRepository.recordCacheMiss(1)

        val deferred = CompletableDeferred<HydratedAccountState?>()
        queueMutex.withLock {
            pendingQueue.add(
                QueuedRequest(
                    normalizedAddress = norm,
                    explicitType = explicitType,
                    forceRefresh = forceRefresh,
                    deferred = deferred,
                )
            )
            if (!flushScheduled) {
                flushScheduled = true
                scope.launch {
                    if (debounceWindowMs > 0L) {
                        delay(debounceWindowMs)
                    }
                    flushPendingQueue()
                }
            }
        }
        return deferred.await()
    }

    suspend fun hydrateBatch(
        addresses: List<String>,
        explicitTypes: Map<String, KnownContractType> = emptyMap(),
        forceRefresh: Boolean = false,
        ttlMs: Long = DEFAULT_CACHE_TTL_MS,
    ): Map<String, HydratedAccountState> {
        val normalizedList = addresses
            .map { BrotherhoodConfig.normalizeAddress(it) }
            .filter { it.isNotBlank() && !BrotherhoodConfig.isZeroAddress(it) }
            .distinct()
        if (normalizedList.isEmpty()) {
            return emptyMap()
        }

        val normalizedHints = explicitTypes.entries.associate { (k, v) ->
            BrotherhoodConfig.normalizeAddress(k) to v
        }

        val results = LinkedHashMap<String, HydratedAccountState>()
        val toFetch = mutableListOf<String>()

        if (!forceRefresh) {
            for (addr in normalizedList) {
                val cached = getFreshCachedState(addr, normalizedHints[addr], ttlMs)
                if (cached != null) {
                    results[addr] = cached
                } else {
                    toFetch.add(addr)
                }
            }
            telemetryRepository.recordCacheHit(results.size)
            telemetryRepository.recordCacheMiss(toFetch.size)
        } else {
            toFetch.addAll(normalizedList)
            telemetryRepository.recordCacheMiss(toFetch.size)
        }

        if (toFetch.isNotEmpty()) {
            val fetched = executeBatchHydration(toFetch, normalizedHints)
            results.putAll(fetched)
        }
        return results
    }

    private suspend fun getFreshCachedState(
        normalizedAddress: String,
        explicitType: KnownContractType?,
        ttlMs: Long,
    ): HydratedAccountState? {
        val now = System.currentTimeMillis()
        val inMemory = _hydratedStates.value[normalizedAddress]
        if (inMemory != null && now - inMemory.updatedAtMs <= ttlMs) {
            if (explicitType != null &&
                inMemory.contractType == KnownContractType.UNKNOWN &&
                !inMemory.dataBocBase64.isNullOrBlank()
            ) {
                val redecoded = UniversalBocDeserializer.deserializeAccountDataBoc(
                    dataBocBase64 = inMemory.dataBocBase64,
                    codeHash = inMemory.codeHash,
                    explicitType = explicitType,
                )
                val upgraded = inMemory.copy(
                    contractType = explicitType,
                    decodedStore = redecoded,
                )
                _hydratedStates.update { it + (normalizedAddress to upgraded) }
                return upgraded
            }
            return inMemory
        }

        val entity = dao.getContractCache(normalizedAddress) ?: return null
        if (now - entity.updatedAtMs > ttlMs) {
            return null
        }
        val decoded = entity.parsedStoreJson?.let { UniversalBocDeserializer.decodeFromJson(it) }
            ?: entity.dataBocBase64?.let {
                UniversalBocDeserializer.deserializeAccountDataBoc(
                    dataBocBase64 = it,
                    codeHash = entity.codeHash,
                    explicitType = explicitType ?: KnownContractType.fromWireName(entity.contractType),
                )
            }
        val state = HydratedAccountState(
            address = normalizedAddress,
            status = entity.status,
            balanceNano = entity.balanceNano.toBigIntegerOrNull() ?: BigInteger.ZERO,
            codeHash = entity.codeHash,
            dataHash = entity.dataHash,
            dataBocBase64 = entity.dataBocBase64,
            lastTxLt = entity.lastTxLt,
            lastTxHash = entity.lastTxHash,
            contractType = decoded?.contractType ?: KnownContractType.fromWireName(entity.contractType),
            decodedStore = decoded,
            updatedAtMs = entity.updatedAtMs,
        )
        _hydratedStates.update { it + (normalizedAddress to state) }
        return state
    }

    private suspend fun flushPendingQueue() {
        val snapshot = queueMutex.withLock {
            val copy = pendingQueue.toList()
            pendingQueue.clear()
            flushScheduled = false
            copy
        }
        if (snapshot.isEmpty()) {
            return
        }

        val typeHints = mutableMapOf<String, KnownContractType>()
        val uniqueAddresses = LinkedHashSet<String>()
        for (req in snapshot) {
            uniqueAddresses.add(req.normalizedAddress)
            if (req.explicitType != null) {
                typeHints[req.normalizedAddress] = req.explicitType
            }
        }

        val hydratedMap = runCatching {
            executeBatchHydration(uniqueAddresses.toList(), typeHints)
        }.getOrElse { emptyMap() }

        for (req in snapshot) {
            req.deferred.complete(hydratedMap[req.normalizedAddress])
        }
    }

    private suspend fun executeBatchHydration(
        addresses: List<String>,
        explicitTypes: Map<String, KnownContractType>,
    ): Map<String, HydratedAccountState> = withContext(Dispatchers.Default) {
        val out = LinkedHashMap<String, HydratedAccountState>()
        val chunks = addresses.chunked(MAX_BATCH_SIZE)
        val isTestnet = telemetryRepository.isTestnetEnabled.value

        for (chunk in chunks) {
            val startMs = System.currentTimeMillis()
            val dtos = try {
                val result = rateLimiter.withRateLimit(RpcProvider.TONCENTER_V3) {
                    transport.fetchAccountStates(chunk, isTestnet)
                }
                val latency = System.currentTimeMillis() - startMs
                telemetryRepository.recordRpcEvent(
                    provider = RpcProvider.TONCENTER_V3,
                    endpoint = "/api/v3/accountStates",
                    method = "GET",
                    addressCount = chunk.size,
                    statusCode = 200,
                    latencyMs = latency,
                    isSuccess = true,
                    isBatchHydration = true,
                )
                result
            } catch (e: RateLimitedHttpException) {
                val latency = System.currentTimeMillis() - startMs
                telemetryRepository.recordRpcEvent(
                    provider = RpcProvider.TONCENTER_V3,
                    endpoint = "/api/v3/accountStates",
                    method = "GET",
                    addressCount = chunk.size,
                    statusCode = e.statusCode,
                    latencyMs = latency,
                    isSuccess = false,
                    errorMessage = e.message,
                    isBatchHydration = true,
                )
                emptyList()
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startMs
                telemetryRepository.recordRpcEvent(
                    provider = RpcProvider.TONCENTER_V3,
                    endpoint = "/api/v3/accountStates",
                    method = "GET",
                    addressCount = chunk.size,
                    statusCode = 500,
                    latencyMs = latency,
                    isSuccess = false,
                    errorMessage = e.message,
                    isBatchHydration = true,
                )
                emptyList()
            }

            val dtoByAddress = dtos.associateBy { BrotherhoodConfig.normalizeAddress(it.address) }
            val now = System.currentTimeMillis()
            val cacheEntities = mutableListOf<ContractCacheEntity>()
            val addressBookEntries = mutableListOf<AddressBookCacheEntity>()
            val tokenMetadataEntries = mutableListOf<TokenMetadataCacheEntity>()

            for (addr in chunk) {
                val dto = dtoByAddress[addr]
                val explicitType = explicitTypes[addr]
                val normalizedCodeHash = KnownCodeHashes.normalizeCodeHash(dto?.codeHash)
                val detectedType = KnownCodeHashes.detectKnownType(normalizedCodeHash, explicitType)
                val dataBoc = dto?.dataBoc?.takeIf { it.isNotBlank() }

                val decodedStore = if (dataBoc != null) {
                    UniversalBocDeserializer.deserializeAccountDataBoc(
                        dataBocBase64 = dataBoc,
                        codeHash = normalizedCodeHash,
                        explicitType = detectedType,
                    )
                } else {
                    null
                }
                val finalType = decodedStore?.contractType ?: detectedType
                val balanceBigInt = dto?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO
                val statusStr = dto?.effectiveStatus ?: "nonexist"

                val state = HydratedAccountState(
                    address = addr,
                    status = statusStr,
                    balanceNano = balanceBigInt,
                    codeHash = normalizedCodeHash,
                    dataHash = dto?.dataHash,
                    dataBocBase64 = dataBoc,
                    lastTxLt = dto?.lastTransactionLt,
                    lastTxHash = dto?.lastTransactionHash,
                    contractType = finalType,
                    decodedStore = decodedStore,
                    updatedAtMs = now,
                )
                out[addr] = state

                val parsedJson = decodedStore?.let {
                    runCatching { UniversalBocDeserializer.encodeToJson(it) }.getOrNull()
                }
                cacheEntities.add(
                    ContractCacheEntity(
                        address = addr,
                        contractType = finalType.wireName,
                        codeHash = normalizedCodeHash,
                        dataHash = dto?.dataHash,
                        dataBocBase64 = dataBoc,
                        balanceNano = balanceBigInt.toString(),
                        status = statusStr,
                        lastTxLt = dto?.lastTransactionLt,
                        lastTxHash = dto?.lastTransactionHash,
                        parsedStoreJson = parsedJson,
                        updatedAtMs = now,
                    )
                )

                // Auto-index FiWallet profile into AddressBookCacheEntity
                if (decodedStore is DecodedContractStore.FiWallet) {
                    val fw = decodedStore.store
                    if (fw.profile.username.isNotBlank()) {
                        val pmAddr = fw.addresses.trustedJettonAddrs.personalJettonMinter
                            .takeIf { fw.addresses.trustedJettonAddrs.hasPersonalMinter }
                        addressBookEntries.add(
                            AddressBookCacheEntity(
                                fiWalletAddress = addr,
                                ownerAddress = fw.addresses.owner,
                                username = fw.profile.username,
                                h3Cell = fw.profile.h3Cell,
                                countryCode = fw.profile.country,
                                isActive = fw.active,
                                isAuthority = fw.isAuthorityAccount,
                                personalMinterAddress = pmAddr,
                                relationType = "directory",
                                updatedAtMs = now,
                            )
                        )
                    }
                }

                // Auto-index PersonalMinter into TokenMetadataCacheEntity
                if (decodedStore is DecodedContractStore.PersonalMinter) {
                    val pm = decodedStore.store
                    val meta = pm.parsedMetadata
                    tokenMetadataEntries.add(
                        TokenMetadataCacheEntity(
                            minterAddress = addr,
                            name = meta?.name ?: "Personal Token",
                            symbol = meta?.symbol ?: "PT",
                            description = meta?.description,
                            imageUrl = meta?.image,
                            decimals = meta?.decimals ?: BrotherhoodConfig.PERSONAL_TOKEN_DECIMALS,
                            adminAddress = pm.adminAddress,
                            isPersonalToken = true,
                            isVerifiedEcosystemToken = pm.fiJettonAddress == BrotherhoodConfig.FI_ADDRESS_RAW,
                            updatedAtMs = now,
                        )
                    )
                }
            }

            if (cacheEntities.isNotEmpty()) {
                dao.upsertContractCaches(cacheEntities)
            }
            if (addressBookEntries.isNotEmpty()) {
                dao.upsertContacts(addressBookEntries)
            }
            if (tokenMetadataEntries.isNotEmpty()) {
                dao.upsertTokenMetadataList(tokenMetadataEntries)
            }
        }

        _hydratedStates.update { current -> current + out }
        out
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 50L
        const val DEFAULT_CACHE_TTL_MS = 8_000L
        const val MAX_BATCH_SIZE = 30
    }
}

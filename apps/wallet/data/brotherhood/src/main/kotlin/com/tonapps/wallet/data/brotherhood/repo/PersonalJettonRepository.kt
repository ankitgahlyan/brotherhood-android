package com.tonapps.wallet.data.brotherhood.repo

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.KnownContractType
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.JettonContentCodec
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDao
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodOutgoingMessage
import com.tonapps.wallet.data.brotherhood.tx.BrotherhoodTransferIntent
import kotlinx.coroutines.flow.Flow
import org.ton.block.AddrStd
import java.math.BigInteger

data class MutualCreditSwapQuote(
    val isBuyCredit: Boolean,
    val fromSymbol: String,
    val toSymbol: String,
    val targetPersonalMinterAddress: String,
    val targetOwnerFiWalletAddress: String,
    val userFiWalletAddress: String,
    val userPersonalWalletAddress: String,
    val inputAmountRaw: BigInteger,
    val estimatedOutputAmountRaw: BigInteger,
    val exchangeMultiplierPermille: Int,
    val gasFeeNano: BigInteger,
)

/**
 * Manages Personal Jettons (`PersonalMinter` & `PersonalWallet`) and the Testnet Mutual Credit Swap system
 * (`FI`/`HD` <-> Personal Token of `FI` Admin treated as reference stablecoin like USDT, or any citizen's Personal Token).
 */
class PersonalJettonRepository(
    private val dao: BrotherhoodDao,
    private val hydrator: AccountStateHydrator,
) {
    fun observeTrackedPersonalTokens(): Flow<List<TrackedPersonalTokenEntity>> {
        return dao.observeTrackedPersonalTokens()
    }

    /**
     * Derives and hydrates the FI Admin's Personal Token (treated as the default reference stablecoin like USDT in Swap),
     * plus the user's own Personal Token and any tracked Personal Tokens.
     */
    suspend fun syncPersonalTokens(
        userWalletAddress: String,
        forceRefresh: Boolean = false,
    ): List<TrackedPersonalTokenEntity> {
        val userOwner = AddrStd(userWalletAddress)
        val userFiWallet = ShardedAddressDerivation.deriveFiWallet(userOwner).address

        // 1. Hydrate FiMinter to discover FiStore.adminAddress
        val fiMinterState = hydrator.hydrateAddress(
            address = BrotherhoodConfig.FI_ADDRESS_RAW,
            explicitType = KnownContractType.FI_MINTER,
            forceRefresh = forceRefresh,
        )
        val fiStore = (fiMinterState?.decodedStore as? DecodedContractStore.FiMinter)?.store
        val fiAdminOwnerAddr = fiStore?.adminAddress
            ?.takeIf { !BrotherhoodConfig.isZeroAddress(it) }
            ?.let { runCatching { AddrStd(it) }.getOrNull() }
            ?: BrotherhoodConfig.DAO_PROXY_ADDRESS

        val fiAdminFiWallet = ShardedAddressDerivation.deriveFiWallet(fiAdminOwnerAddr).address
        val adminPersonalMinterInit = ShardedAddressDerivation.derivePersonalMinter(
            issuerFiWallet = fiAdminFiWallet,
            adminAddress = fiAdminOwnerAddr,
        )
        val userOwnPersonalMinterInit = ShardedAddressDerivation.derivePersonalMinter(
            issuerFiWallet = userFiWallet,
            adminAddress = userOwner,
        )

        val existingTracked = dao.getAllTrackedPersonalTokens()
        val candidateMinters = buildMap {
            put(
                adminPersonalMinterInit.address.toAccountId(),
                Triple(fiAdminOwnerAddr, fiAdminFiWallet, true),
            )
            put(
                userOwnPersonalMinterInit.address.toAccountId(),
                Triple(userOwner, userFiWallet, false),
            )
            for (item in existingTracked) {
                val mAddr = BrotherhoodConfig.normalizeAddress(item.minterAddress)
                if (!containsKey(mAddr)) {
                    val oAddr = runCatching { AddrStd(item.ownerWalletAddress) }.getOrDefault(userOwner)
                    val fwAddr = runCatching { AddrStd(item.ownerFiWalletAddress) }.getOrDefault(userFiWallet)
                    put(mAddr, Triple(oAddr, fwAddr, item.isAdminReferenceStablecoin))
                }
            }
        }

        // Derive user's PersonalWallet for each candidate PersonalMinter
        val userPersonalWalletsByMinter = candidateMinters.mapValues { (minterRaw, triple) ->
            val (adminOwner, _, _) = triple
            ShardedAddressDerivation.derivePersonalWallet(
                personalMinter = AddrStd(minterRaw),
                owner = userOwner,
                adminAddress = adminOwner,
            ).address.toAccountId()
        }

        val allAddressesToHydrate = mutableListOf<String>()
        val explicitTypes = mutableMapOf<String, KnownContractType>()
        for ((minterRaw, _) in candidateMinters) {
            allAddressesToHydrate.add(minterRaw)
            explicitTypes[minterRaw] = KnownContractType.PERSONAL_MINTER
        }
        for ((_, pwRaw) in userPersonalWalletsByMinter) {
            allAddressesToHydrate.add(pwRaw)
            explicitTypes[pwRaw] = KnownContractType.PERSONAL_WALLET
        }

        val hydrated = hydrator.hydrateBatch(
            addresses = allAddressesToHydrate,
            explicitTypes = explicitTypes,
            forceRefresh = forceRefresh,
        )

        val now = System.currentTimeMillis()
        val updatedEntities = mutableListOf<TrackedPersonalTokenEntity>()

        for ((minterRaw, triple) in candidateMinters) {
            val (ownerAddr, ownerFiAddr, isAdminStablecoin) = triple
            val minterState = hydrated[minterRaw]
            val minterStore = (minterState?.decodedStore as? DecodedContractStore.PersonalMinter)?.store
            val pwRaw = userPersonalWalletsByMinter.getValue(minterRaw)
            val pwState = hydrated[pwRaw]
            val pwStore = (pwState?.decodedStore as? DecodedContractStore.PersonalWallet)?.store

            val defaultName = if (isAdminStablecoin) {
                "BrotherHood Admin USD (Reference)"
            } else {
                "Personal Token"
            }
            val defaultSymbol = if (isAdminStablecoin) {
                "FIUSD"
            } else {
                "PT"
            }

            updatedEntities.add(
                TrackedPersonalTokenEntity(
                    minterAddress = minterRaw,
                    ownerWalletAddress = ownerAddr.toAccountId(),
                    ownerFiWalletAddress = ownerFiAddr.toAccountId(),
                    userPersonalWalletAddress = pwRaw,
                    name = minterStore?.parsedMetadata?.name ?: defaultName,
                    symbol = minterStore?.parsedMetadata?.symbol ?: defaultSymbol,
                    imageUrl = minterStore?.parsedMetadata?.image,
                    totalSupplyRaw = minterStore?.totalSupplyRaw ?: "0",
                    userBalanceRaw = pwStore?.jettonBalanceRaw ?: "0",
                    isAdminReferenceStablecoin = isAdminStablecoin,
                    updatedAtMs = now,
                )
            )
        }

        if (updatedEntities.isNotEmpty()) {
            dao.upsertTrackedPersonalTokens(updatedEntities)
        }
        return updatedEntities
    }

    suspend fun addCustomPersonalTokenByMinter(
        userWalletAddress: String,
        minterAddress: String,
    ): TrackedPersonalTokenEntity? {
        val userOwner = AddrStd(userWalletAddress)
        val minterAddr = runCatching { AddrStd(minterAddress.trim()) }.getOrNull() ?: return null
        val minterRaw = minterAddr.toAccountId()

        val minterState = hydrator.hydrateAddress(
            address = minterRaw,
            explicitType = KnownContractType.PERSONAL_MINTER,
            forceRefresh = true,
        )
        val minterStore = (minterState?.decodedStore as? DecodedContractStore.PersonalMinter)?.store
        val adminAddr = minterStore?.adminAddress
            ?.let { runCatching { AddrStd(it) }.getOrNull() }
            ?: userOwner
        val adminFiWallet = ShardedAddressDerivation.deriveFiWallet(adminAddr).address
        val userPw = ShardedAddressDerivation.derivePersonalWallet(
            personalMinter = minterAddr,
            owner = userOwner,
            adminAddress = adminAddr,
        ).address

        val pwState = hydrator.hydrateAddress(
            address = userPw.toAccountId(),
            explicitType = KnownContractType.PERSONAL_WALLET,
            forceRefresh = true,
        )
        val pwStore = (pwState?.decodedStore as? DecodedContractStore.PersonalWallet)?.store

        val entity = TrackedPersonalTokenEntity(
            minterAddress = minterRaw,
            ownerWalletAddress = adminAddr.toAccountId(),
            ownerFiWalletAddress = adminFiWallet.toAccountId(),
            userPersonalWalletAddress = userPw.toAccountId(),
            name = minterStore?.parsedMetadata?.name ?: "Personal Token",
            symbol = minterStore?.parsedMetadata?.symbol ?: "PT",
            imageUrl = minterStore?.parsedMetadata?.image,
            totalSupplyRaw = minterStore?.totalSupplyRaw ?: "0",
            userBalanceRaw = pwStore?.jettonBalanceRaw ?: "0",
            isAdminReferenceStablecoin = false,
            updatedAtMs = System.currentTimeMillis(),
        )
        dao.upsertTrackedPersonalToken(entity)
        return entity
    }

    // =========================================================================
    // Mutual Credit Swap (FI/HD <-> Personal Token)
    // =========================================================================

    fun computeMutualCreditQuote(
        isBuyCredit: Boolean,
        inputAmountRaw: BigInteger,
        personalToken: TrackedPersonalTokenEntity,
        userFiWalletAddress: String,
        multiplierPermille: Int = 1000,
    ): MutualCreditSwapQuote {
        val safeMultiplier = multiplierPermille.coerceAtLeast(1)
        val estimatedOut = if (isBuyCredit) {
            inputAmountRaw.multiply(BigInteger.valueOf(safeMultiplier.toLong()))
                .divide(BigInteger.valueOf(1000L))
        } else {
            inputAmountRaw.multiply(BigInteger.valueOf(1000L))
                .divide(BigInteger.valueOf(safeMultiplier.toLong()))
        }
        return MutualCreditSwapQuote(
            isBuyCredit = isBuyCredit,
            fromSymbol = if (isBuyCredit) {
                BrotherhoodConfig.FI_SYMBOL
            } else {
                personalToken.symbol
            },
            toSymbol = if (isBuyCredit) {
                personalToken.symbol
            } else {
                BrotherhoodConfig.FI_SYMBOL
            },
            targetPersonalMinterAddress = personalToken.minterAddress,
            targetOwnerFiWalletAddress = personalToken.ownerFiWalletAddress,
            userFiWalletAddress = userFiWalletAddress,
            userPersonalWalletAddress = personalToken.userPersonalWalletAddress,
            inputAmountRaw = inputAmountRaw,
            estimatedOutputAmountRaw = estimatedOut,
            exchangeMultiplierPermille = safeMultiplier,
            gasFeeNano = BigInteger.valueOf(BrotherhoodConfig.CREDIT_ACTION_GAS_NANO),
        )
    }

    /**
     * Builds the Mutual Credit Swap transaction:
     * - Direction 1 (`isBuyCredit == true`): `FI/HD` -> Personal Token via `BuyCredit` (`0x00001147`) sent to user's `FiWallet`.
     * - Direction 2 (`isBuyCredit == false`): Personal Token -> `FI/HD` via `AskToBurn` (`0x595f07bc`) on user's `PersonalWallet`.
     */
    fun buildMutualCreditSwapIntent(quote: MutualCreditSwapQuote): BrotherhoodTransferIntent {
        return if (quote.isBuyCredit) {
            val payload = BrotherhoodMessages.buildBuyCredit(
                jettonAmountRaw = quote.inputAmountRaw,
                transferRecipient = AddrStd(quote.targetOwnerFiWalletAddress),
            )
            BrotherhoodTransferIntent(
                title = "Mutual Credit Swap: ${quote.fromSymbol} → ${quote.toSymbol}",
                subtitle = "Buy credit backed by ${quote.fromSymbol}",
                messages = listOf(
                    BrotherhoodOutgoingMessage(
                        destination = AddrStd(quote.userFiWalletAddress),
                        amountNano = quote.gasFeeNano,
                        payloadCell = payload,
                    )
                ),
            )
        } else {
            val burnPayload = BrotherhoodMessages.buildAskToBurn(
                jettonAmountRaw = quote.inputAmountRaw,
                sendExcessesTo = AddrStd(quote.userFiWalletAddress),
            )
            BrotherhoodTransferIntent(
                title = "Mutual Credit Payback: ${quote.fromSymbol} → ${quote.toSymbol}",
                subtitle = "Burn ${quote.fromSymbol} to redeem ${quote.toSymbol}",
                messages = listOf(
                    BrotherhoodOutgoingMessage(
                        destination = AddrStd(quote.userPersonalWalletAddress),
                        amountNano = quote.gasFeeNano,
                        payloadCell = burnPayload,
                    )
                ),
            )
        }
    }

    // =========================================================================
    // Personal Token Management Intents (7 Sub-Tabs)
    // =========================================================================

    fun buildDeployPersonalTokenIntent(
        ownerWalletAddress: String,
        name: String,
        symbol: String,
        description: String,
        imageUrl: String = "",
    ): BrotherhoodTransferIntent {
        val owner = AddrStd(ownerWalletAddress)
        val fiWallet = ShardedAddressDerivation.deriveFiWallet(owner).address
        val metadataCell = JettonContentCodec.buildTolkOnchainMetadata(
            name = name.trim(),
            symbol = symbol.trim().uppercase(),
            description = description.trim(),
            image = imageUrl.trim(),
        )
        val minterInit = ShardedAddressDerivation.derivePersonalMinter(
            issuerFiWallet = fiWallet,
            adminAddress = owner,
            metadataUriCell = metadataCell,
        )
        val personalWalletInit = ShardedAddressDerivation.derivePersonalWallet(
            personalMinter = minterInit.address,
            owner = owner,
            adminAddress = owner,
        )
        val changeMetadataCell = BrotherhoodMessages.buildChangeMinterMetadata(metadataCell)
        val registerOnFiWalletCell = BrotherhoodMessages.buildActSetPersonalJetton(
            personalJettonMinter = minterInit.address,
            personalJettonWallet = personalWalletInit.address,
        )

        return BrotherhoodTransferIntent(
            title = "Create Personal Token (${symbol.trim().uppercase()})",
            subtitle = "Deploy PersonalMinter and link to your FiWallet",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = minterInit.address,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.DEPLOY_PERSONAL_MINTER_TON_NANO),
                    payloadCell = changeMetadataCell,
                    stateInitCell = minterInit.stateInitCell,
                ),
                BrotherhoodOutgoingMessage(
                    destination = fiWallet,
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.LINK_PERSONAL_JETTON_GAS_NANO),
                    payloadCell = registerOnFiWalletCell,
                ),
            ),
        )
    }

    fun buildMintPersonalTokenIntent(
        personalMinterAddress: String,
        recipientWalletAddress: String,
        amountRaw: BigInteger,
    ): BrotherhoodTransferIntent {
        val minter = AddrStd(personalMinterAddress)
        val recipient = AddrStd(recipientWalletAddress)
        val mintGas = BigInteger.valueOf(BrotherhoodConfig.PERSONAL_MINT_GAS_NANO)
        return BrotherhoodTransferIntent(
            title = "Mint Personal Token",
            subtitle = "Mint tokens from your PersonalMinter",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = minter,
                    amountNano = mintGas,
                    payloadCell = BrotherhoodMessages.buildMintPersonal(
                        mintRecipient = recipient,
                        tonAmountNano = BigInteger.valueOf(100_000_000L),
                        jettonAmountRaw = amountRaw,
                    ),
                )
            ),
        )
    }

    fun buildTransferPersonalTokenIntent(
        senderPersonalWalletAddress: String,
        recipientOwnerAddress: String,
        senderOwnerAddress: String,
        amountRaw: BigInteger,
        comment: String? = null,
    ): BrotherhoodTransferIntent {
        val payload = BrotherhoodMessages.buildAskToTransfer(
            jettonAmountRaw = amountRaw,
            transferRecipient = AddrStd(recipientOwnerAddress),
            sendExcessesTo = AddrStd(senderOwnerAddress),
            comment = comment,
        )
        return BrotherhoodTransferIntent(
            title = "Send Personal Token",
            subtitle = "TEP-74 transfer from PersonalWallet",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(senderPersonalWalletAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.JETTON_TRANSFER_GAS_NANO),
                    payloadCell = payload,
                )
            ),
        )
    }

    fun buildUpdatePersonalMetadataIntent(
        personalMinterAddress: String,
        name: String,
        symbol: String,
        description: String,
        imageUrl: String,
    ): BrotherhoodTransferIntent {
        val metadataCell = JettonContentCodec.buildTolkOnchainMetadata(
            name = name.trim(),
            symbol = symbol.trim().uppercase(),
            description = description.trim(),
            image = imageUrl.trim(),
        )
        return BrotherhoodTransferIntent(
            title = "Update Personal Token Metadata",
            subtitle = "$name ($symbol)",
            messages = listOf(
                BrotherhoodOutgoingMessage(
                    destination = AddrStd(personalMinterAddress),
                    amountNano = BigInteger.valueOf(BrotherhoodConfig.SIMPLE_WALLET_ACTION_GAS_NANO),
                    payloadCell = BrotherhoodMessages.buildChangeMinterMetadata(metadataCell),
                )
            ),
        )
    }
}

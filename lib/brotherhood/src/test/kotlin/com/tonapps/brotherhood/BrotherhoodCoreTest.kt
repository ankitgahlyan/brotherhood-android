package com.tonapps.brotherhood

import com.tonapps.brotherhood.store.TolkSliceUtils.base64
import com.tonapps.brotherhood.store.TolkSliceUtils.storeAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.storeCoins
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringRefTail
import com.tonapps.brotherhood.store.TolkSliceUtils.toAccountId
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.BrotherhoodCountries
import com.tonapps.brotherhood.config.BrotherhoodErrors
import com.tonapps.brotherhood.config.ContractCodeCells
import com.tonapps.brotherhood.config.KnownCodeHashes
import com.tonapps.brotherhood.derivation.ShardedAddressDerivation
import com.tonapps.brotherhood.h3.H3Helper
import com.tonapps.brotherhood.messages.BrotherhoodMessages
import com.tonapps.brotherhood.messages.BrotherhoodOpcodes
import com.tonapps.brotherhood.store.DecodedContractStore
import com.tonapps.brotherhood.store.HoldingStore
import com.tonapps.brotherhood.store.JettonContentCodec
import com.tonapps.brotherhood.store.LocationStore
import com.tonapps.brotherhood.store.PersonalWalletStore
import com.tonapps.brotherhood.store.TolkSliceUtils.toBigInt
import com.tonapps.brotherhood.store.UniversalBocDeserializer
import com.tonapps.brotherhood.store.VoterStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bigint.toBigInt
import org.ton.block.Coins
import org.ton.cell.CellBuilder
import java.math.BigInteger

class BrotherhoodCoreTest {

    @Test
    fun `all ContractCodeCells deserialize cleanly`() {
        assertFalse(ContractCodeCells.baseFiWalletCode.isEmpty())
        assertFalse(ContractCodeCells.basePersonalWalletCode.isEmpty())
        assertFalse(ContractCodeCells.personalMinterCode.isEmpty())
        assertFalse(ContractCodeCells.personalWalletCode.isEmpty())
        assertFalse(ContractCodeCells.locationCode.isEmpty())
        assertFalse(ContractCodeCells.lotteryCode.isEmpty())
        assertFalse(ContractCodeCells.pollCode.isEmpty())
        assertFalse(ContractCodeCells.voterCode.isEmpty())
        assertFalse(ContractCodeCells.holdingCode.isEmpty())
        assertFalse(ContractCodeCells.followingCode.isEmpty())
        assertFalse(ContractCodeCells.dnsItemCode.isEmpty())
    }

    @Test
    fun `ShardedAddressDerivation enforces 8-bit shard prefix matching closeTo`() {
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val fiWallet = ShardedAddressDerivation.deriveFiWalletAddress(owner)
        assertEquals(owner.address.toByteArray()[0], fiWallet.address.toByteArray()[0])

        val personalMinter = ShardedAddressDerivation.derivePersonalMinterAddress(fiWallet, owner)
        assertEquals(owner.address.toByteArray()[0], personalMinter.address.toByteArray()[0])

        val personalWallet = ShardedAddressDerivation.derivePersonalWalletAddress(personalMinter, owner, owner)
        assertEquals(owner.address.toByteArray()[0], personalWallet.address.toByteArray()[0])

        val location = ShardedAddressDerivation.deriveLocationAddress("813d3ffffffffff", BrotherhoodConfig.FI_ADDRESS)
        assertEquals(
            BrotherhoodConfig.FI_ADDRESS.address.toByteArray()[0],
            location.address.toByteArray()[0]
        )

        val holding = ShardedAddressDerivation.deriveHoldingAddress(
            payer = owner,
            payee = BrotherhoodConfig.BRO_TREASURY_ADDRESS,
            amountNano = BigInteger.valueOf(1_000_000_000L),
            queryId = 42L,
        )
        assertEquals(owner.address.toByteArray()[0], holding.address.toByteArray()[0])

        val voter = ShardedAddressDerivation.deriveVoterAddress(
            voterOwner = owner,
            pollAddress = BrotherhoodConfig.BRO_COLLECTION_RESOLVER,
        )
        assertEquals(owner.address.toByteArray()[0], voter.address.toByteArray()[0])
    }

    @Test
    fun `FiWalletStore and child stores decode accurately from BOC`() {
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val minter = BrotherhoodConfig.FI_ADDRESS

        val profileCell = CellBuilder.createCell {
            storeStringRefTail("ankit")
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
            storeUInt(3, 32)
            storeUInt(7, 32)
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
        val rootCell = CellBuilder.createCell {
            storeCoins(Coins.ofNano(250_000_000_000L))
            storeUInt(15, 32) // goldCoins
            storeUInt(4, 8) // txnCount
            storeUInt(0, 2) // status = GoodCitizen
            storeBit(true) // isAuthorityAccount
            storeBit(false) // isPrevilegedAccount
            storeCoins(Coins.ofNano(50_000_000_000L)) // creditNeed
            storeUInt(1800000000L.toBigInt(), 32) // creditMaturity
            storeUInt(200, 16) // multiplier
            storeCoins(Coins.ofNano(1_000_000L)) // accumulatedFees
            storeCoins(Coins.ofNano(0L)) // debt
            storeBit(true) // allowDeferred
            storeUInt(8, 4) // votes
            storeUInt(12, 20) // receivedVotes
            storeUInt(5, 8) // connections
            storeBit(true) // active
            storeBit(true) // mintable
            storeUInt(1, 10) // version
            storeUInt(1, 10) // storeVersion
            storeRef(profileCell)
            storeRef(timestampsCell)
            storeRef(addressesCell)
            storeRef(mapsCell)
        }

        val decoded = UniversalBocDeserializer.deserializeAccountDataBoc(
            dataBocBase64 = rootCell.base64(),
            codeHash = KnownCodeHashes.FI_WALLET,
        )
        assertNotNull(decoded)
        val fiWallet = (decoded as DecodedContractStore.FiWallet).store
        assertEquals("ankit", fiWallet.profile.username)
        assertEquals("813d3ffffffffff", fiWallet.profile.h3Cell)
        assertEquals(356, fiWallet.profile.country)
        assertEquals(BigInteger.valueOf(250_000_000_000L), fiWallet.jettonBalance)
        assertEquals(15L, fiWallet.goldCoins)
        assertTrue(fiWallet.isAuthorityAccount)
        assertTrue(fiWallet.active)
        assertEquals(owner.toAccountId(), fiWallet.addresses.owner)
        assertFalse(fiWallet.addresses.trustedJettonAddrs.hasPersonalMinter)

        val jsonStr = UniversalBocDeserializer.encodeToJson(decoded)
        val roundTrip = UniversalBocDeserializer.decodeFromJson(jsonStr)
        assertEquals(decoded, roundTrip)
    }

    @Test
    fun `PersonalWalletStore LocationStore HoldingStore VoterStore decode accurately`() {
        val owner = BrotherhoodConfig.DAO_PROXY_ADDRESS
        val minter = BrotherhoodConfig.FI_ADDRESS

        val pwCell = CellBuilder.createCell {
            storeCoins(Coins.ofNano(99_000_000_000L))
            storeAddress(owner)
            storeAddress(owner)
            storeAddress(minter)
            storeUInt(1, 10)
        }
        val pw = PersonalWalletStore.fromCell(pwCell)
        assertEquals(BigInteger.valueOf(99_000_000_000L), pw.jettonBalance)
        assertEquals(owner.toAccountId(), pw.owner)

        val locInit = ShardedAddressDerivation.deriveLocation("813d3ffffffffff", minter)
        val locStore = LocationStore.fromCell(locInit.data)
        assertEquals("813d3ffffffffff", locStore.h3Cell)
        assertEquals(0L, locStore.memberCount)

        val holdingInit = ShardedAddressDerivation.deriveHolding(
            payer = owner,
            payee = minter,
            amountNano = BigInteger.valueOf(5_000_000_000L),
            queryId = 999L,
        )
        val holdingStore = HoldingStore.fromCell(holdingInit.data)
        assertEquals(BigInteger.valueOf(5_000_000_000L), holdingStore.amount)
        assertEquals(999L, holdingStore.queryId)

        val voterInit = ShardedAddressDerivation.deriveVoter(owner, minter)
        val voterStore = VoterStore.fromCell(voterInit.data)
        assertEquals(owner.toAccountId(), voterStore.voterOwner)
        assertFalse(voterStore.voted)
    }

    @Test
    fun `JettonContentCodec round-trips Tolk 4-ref metadata`() {
        val cell = JettonContentCodec.buildTolkOnchainMetadata(
            name = "Ankit Personal Token",
            symbol = "ANK",
            description = "Personal mutual credit token",
            image = "https://example.com/ank.png",
        )
        val parsed = JettonContentCodec.parseJettonContent(cell)
        assertEquals("Ankit Personal Token", parsed.name)
        assertEquals("ANK", parsed.symbol)
        assertEquals("Personal mutual credit token", parsed.description)
        assertEquals("https://example.com/ank.png", parsed.image)
    }

    @Test
    fun `BrotherhoodMessages and BrotherhoodOpcodes match expected opcodes`() {
        val recipient = BrotherhoodConfig.BRO_TREASURY_ADDRESS
        val inviteCell = BrotherhoodMessages.buildActInvite(
            transferRecipient = recipient,
            username = "alice",
            h3Cell = "813d3ffffffffff",
            country = 356,
        )
        val info = BrotherhoodOpcodes.decodeOpcodeFromCell(inviteCell)
        assertNotNull(info)
        assertEquals("ActInvite", info?.structName)
        assertEquals("Invite Member", info?.friendlyTitle)
        assertEquals("Brotherhood Member", info?.contextBadge)

        val buyCreditCell = BrotherhoodMessages.buildBuyCredit(
            jettonAmountRaw = BigInteger.valueOf(10_000_000_000L),
            transferRecipient = recipient,
        )
        val buyCreditInfo = BrotherhoodOpcodes.decodeOpcodeFromCell(buyCreditCell)
        assertEquals("BuyCredit", buyCreditInfo?.structName)
        assertEquals("Buy Credit", buyCreditInfo?.friendlyTitle)

        assertEquals("Wallet Not Onboarded (exit 766)", BrotherhoodErrors.decodeExitCode(766))
        assertEquals("India", BrotherhoodCountries.getCountryByCode(356).name)
        assertTrue(H3Helper.isValidH3Cell("813d3ffffffffff"))
        assertEquals(1, H3Helper.getResolution("813d3ffffffffff"))
    }
}

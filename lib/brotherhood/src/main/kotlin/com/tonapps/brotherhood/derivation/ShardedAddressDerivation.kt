package com.tonapps.brotherhood.derivation

import com.tonapps.brotherhood.store.TolkSliceUtils.storeAddress
import com.tonapps.brotherhood.store.TolkSliceUtils.storeCoins
import com.tonapps.brotherhood.store.TolkSliceUtils.storeStringRefTail
import com.tonapps.brotherhood.store.TolkSliceUtils.toBigInt
import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.brotherhood.config.ContractCodeCells
import org.ton.bigint.toBigInt
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import java.math.BigInteger

data class DeployedContractInit(
    val address: AddrStd,
    val code: Cell,
    val data: Cell,
    val stateInitCell: Cell,
)

object ShardedAddressDerivation {

    fun buildStateInitCell(
        code: Cell,
        data: Cell,
        splitDepth: Int? = null,
    ): Cell {
        return CellBuilder.createCell {
            if (splitDepth != null) {
                storeBit(true)
                storeUInt(splitDepth, 5)
            } else {
                storeBit(false)
            }
            // special = null
            storeBit(false)
            // code = Just(code)
            storeBit(true)
            storeRef(code)
            // data = Just(data)
            storeBit(true)
            storeRef(data)
            // libraries = empty dict
            storeBit(false)
        }
    }

    fun calculateDeployedAddress(
        code: Cell,
        data: Cell,
        splitDepth: Int? = BrotherhoodConfig.SHARD_DEPTH,
        closeTo: AddrStd? = null,
        workchain: Int = 0,
    ): DeployedContractInit {
        val effectiveSplitDepth = if (closeTo != null) {
            splitDepth
        } else {
            null
        }
        val stateInitCell = buildStateInitCell(
            code = code,
            data = data,
            splitDepth = effectiveSplitDepth,
        )
        val initHashBits = stateInitCell.hash()
        val finalHashBits = if (effectiveSplitDepth != null && closeTo != null) {
            val closeToBits = closeTo.address
            BitString(
                (0 until 256).map { idx ->
                    if (idx < effectiveSplitDepth) {
                        closeToBits[idx]
                    } else {
                        initHashBits[idx]
                    }
                }
            )
        } else {
            initHashBits
        }
        return DeployedContractInit(
            address = AddrStd(workchain, finalHashBits),
            code = code,
            data = data,
            stateInitCell = stateInitCell,
        )
    }

    /**
     * Derives the user's FossFiWallet (`FiWallet`) address using `BaseFiWallet.CodeCell`
     * and 8-bit shard prefix matching `owner`.
     */
    fun deriveFiWallet(
        owner: AddrStd,
        minterAddr: AddrStd = BrotherhoodConfig.FI_ADDRESS,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeAddress(owner)
            storeAddress(minterAddr)
            storeUInt(0, 10)
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.baseFiWalletCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = owner,
        )
    }

    fun deriveFiWalletAddress(
        owner: AddrStd,
        minterAddr: AddrStd = BrotherhoodConfig.FI_ADDRESS,
    ): AddrStd = deriveFiWallet(owner, minterAddr).address

    /**
     * Derives a user's PersonalMinter contract address using `PersonalMinter.CodeCell`,
     * `issuerFiWallet` (`fiJettonAddress`), and `adminAddress` (owner wallet),
     * sharded 8-bit closeTo `adminAddress`.
     */
    fun derivePersonalMinter(
        issuerFiWallet: AddrStd,
        adminAddress: AddrStd,
        metadataUriCell: Cell? = null,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeCoins(Coins.ofNano(0L))
            storeAddress(issuerFiWallet)
            storeAddress(adminAddress)
            if (metadataUriCell != null) {
                storeBit(true)
                storeRef(metadataUriCell)
            } else {
                storeBit(false)
            }
            storeUInt(1, 10) // version = 1
            storeRef(ContractCodeCells.personalWalletCode)
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.personalMinterCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = adminAddress,
        )
    }

    fun derivePersonalMinterAddress(
        issuerFiWallet: AddrStd,
        adminAddress: AddrStd,
        metadataUriCell: Cell? = null,
    ): AddrStd = derivePersonalMinter(issuerFiWallet, adminAddress, metadataUriCell).address

    /**
     * Derives a holder's PersonalWallet address for a given `personalMinter`,
     * `owner` (holder wallet), and `adminAddress` (minter's admin/deployer),
     * sharded 8-bit closeTo `owner`.
     */
    fun derivePersonalWallet(
        personalMinter: AddrStd,
        owner: AddrStd,
        adminAddress: AddrStd = owner,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeAddress(owner)
            storeAddress(adminAddress)
            storeAddress(personalMinter)
            storeUInt(0, 10)
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.basePersonalWalletCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = owner,
        )
    }

    fun derivePersonalWalletAddress(
        personalMinter: AddrStd,
        owner: AddrStd,
        adminAddress: AddrStd = owner,
    ): AddrStd = derivePersonalWallet(personalMinter, owner, adminAddress).address

    /**
     * Derives the H3 spatial Location child contract address for `h3Cell`,
     * sharded 8-bit closeTo `minterAddress`.
     */
    fun deriveLocation(
        h3Cell: String,
        minterAddress: AddrStd = BrotherhoodConfig.FI_ADDRESS,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeStringRefTail(h3Cell)
            storeAddress(minterAddress)
            storeUInt(0, 32) // memberCount = 0
            storeBit(false) // members = empty dict
            storeUInt(0, 10) // version = 0
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.locationCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = minterAddress,
        )
    }

    fun deriveLocationAddress(
        h3Cell: String,
        minterAddress: AddrStd = BrotherhoodConfig.FI_ADDRESS,
    ): AddrStd = deriveLocation(h3Cell, minterAddress).address

    /**
     * Derives a Deferred Payment `Holding` contract address,
     * sharded 8-bit closeTo `payer`.
     */
    fun deriveHolding(
        payer: AddrStd,
        payee: AddrStd,
        amountNano: BigInteger,
        queryId: Long,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeAddress(payer)
            storeAddress(payee)
            storeCoins(Coins(amountNano.toBigInt()))
            storeUInt(queryId.toBigInt(), 64)
            storeUInt(0, 32) // createdAt = 0
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.holdingCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = payer,
        )
    }

    fun deriveHoldingAddress(
        payer: AddrStd,
        payee: AddrStd,
        amountNano: BigInteger,
        queryId: Long,
    ): AddrStd = deriveHolding(payer, payee, amountNano, queryId).address

    /**
     * Derives a DAO `Voter` child contract address for `voterOwner` and `pollAddress`,
     * sharded 8-bit closeTo `voterOwner`.
     */
    fun deriveVoter(
        voterOwner: AddrStd,
        pollAddress: AddrStd,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeAddress(voterOwner)
            storeAddress(pollAddress)
            storeBit(false) // voted = false
            storeBit(false) // vote = false
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.voterCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = voterOwner,
        )
    }

    fun deriveVoterAddress(
        voterOwner: AddrStd,
        pollAddress: AddrStd,
    ): AddrStd = deriveVoter(voterOwner, pollAddress).address

    /**
     * Derives a Social `Following` child contract address for `follower` and `followee`,
     * sharded 8-bit closeTo `follower`.
     */
    fun deriveFollowing(
        follower: AddrStd,
        followee: AddrStd,
    ): DeployedContractInit {
        val data = CellBuilder.createCell {
            storeAddress(follower)
            storeAddress(followee)
            storeCoins(Coins.ofNano(0L))
            storeBit(false)
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.followingCode,
            data = data,
            splitDepth = BrotherhoodConfig.SHARD_DEPTH,
            closeTo = follower,
        )
    }

    fun deriveFollowingAddress(
        follower: AddrStd,
        followee: AddrStd,
    ): AddrStd = deriveFollowing(follower, followee).address

    /**
     * Computes the 256-bit SHA256 domain index for a bare `.bro` domain name (without `.bro` suffix)
     * and derives the unsharded `DnsItem` NFT contract address.
     */
    fun encodeDomainCell(bareDomain: String): Cell {
        val clean = bareDomain.trim().lowercase().removeSuffix(".bro")
        return CellBuilder.createCell {
            for (ch in clean) {
                storeUInt(ch.code, 8)
            }
        }
    }

    fun domainItemIndex(bareDomain: String): BigInteger {
        val domainCell = encodeDomainCell(bareDomain)
        return BigInteger(1, domainCell.hash().toByteArray())
    }

    fun deriveDnsItem(
        bareDomain: String,
        collectionAddress: AddrStd = BrotherhoodConfig.BRO_COLLECTION_RESOLVER,
    ): DeployedContractInit {
        val index = domainItemIndex(bareDomain)
        val data = CellBuilder.createCell {
            storeUInt(index.toBigInt(), 256)
            storeAddress(collectionAddress)
        }
        return calculateDeployedAddress(
            code = ContractCodeCells.dnsItemCode,
            data = data,
            splitDepth = null,
            closeTo = null,
        )
    }

    fun deriveDnsItemAddress(
        bareDomain: String,
        collectionAddress: AddrStd = BrotherhoodConfig.BRO_COLLECTION_RESOLVER,
    ): AddrStd = deriveDnsItem(bareDomain, collectionAddress).address
}

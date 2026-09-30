package com.tonapps.wallet.features.brotherhood

import com.tonapps.brotherhood.config.BrotherhoodConfig
import com.tonapps.wallet.data.brotherhood.autofund.AutoFiWalletFunder
import com.tonapps.wallet.data.brotherhood.db.TrackedPersonalTokenEntity
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.network.ProviderRateLimiter
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import com.tonapps.wallet.data.brotherhood.repo.BroDnsRepository
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.CityNetworkRepository
import com.tonapps.wallet.data.brotherhood.repo.DaoRepository
import com.tonapps.wallet.data.brotherhood.repo.LotteryRepository
import com.tonapps.wallet.data.brotherhood.repo.PersonalJettonRepository
import com.tonapps.wallet.features.brotherhood.city.CityNetworkFeature
import com.tonapps.wallet.features.brotherhood.city.H3Res1Grid
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodUiFormatters
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import com.tonapps.wallet.features.brotherhood.dao.DaoFeature
import com.tonapps.wallet.features.brotherhood.dev.DeveloperBubbleFeature
import com.tonapps.wallet.features.brotherhood.dev.DeveloperPanelTab
import com.tonapps.wallet.features.brotherhood.dns.BroDnsFeature
import com.tonapps.wallet.features.brotherhood.dock.BrotherhoodMainTab
import com.tonapps.wallet.features.brotherhood.hub.BrotherhoodHubFeature
import com.tonapps.wallet.features.brotherhood.hub.BrotherhoodHubSubTab
import com.tonapps.wallet.features.brotherhood.lottery.LotteryFeature
import com.tonapps.wallet.features.brotherhood.personal.PersonalTokenFeature
import com.tonapps.wallet.features.brotherhood.personal.PersonalTokenSubTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class BrotherhoodFeaturesTest {

    init {
        System.setProperty("kotlinx.coroutines.fast.service.loader", "false")
    }

    @Test
    fun `7-tab bottom dock and 11 hub sub-tabs and 7 personal token sub-tabs match specification`() {
        assertEquals(7, BrotherhoodMainTab.entries.size)
        assertEquals(
            listOf("wallet", "brotherhood", "personal", "city", "dao", "lottery", "dns"),
            BrotherhoodMainTab.entries.map { it.id },
        )

        assertEquals(11, BrotherhoodHubSubTab.entries.size)
        assertEquals(
            listOf(
                "account",
                "network",
                "claim",
                "invite",
                "vote",
                "credit",
                "allowance",
                "gold",
                "profile",
                "deferred",
                "authority",
            ),
            BrotherhoodHubSubTab.entries.map { it.key },
        )

        assertEquals(7, PersonalTokenSubTab.entries.size)
        assertEquals(
            listOf("overview", "create", "mint", "credit", "transfer", "burn", "admin"),
            PersonalTokenSubTab.entries.map { it.id },
        )
    }

    @Test
    fun `H3Res1Grid provides 84 resolution-1 cells and GPS auto-detect resolves preset cells`() {
        val cells = H3Res1Grid.ALL_RES1_CELLS
        assertEquals(84, cells.size)

        val detected = H3Res1Grid.latLngToRes1H3Cell(28.6139, 77.2090)
        assertEquals(15, detected.length)
        assertTrue(detected.startsWith("81"))
    }

    @Test
    fun `BrotherhoodUiFormatters parses and formats nano amounts accurately`() {
        val parsed = BrotherhoodUiFormatters.parseDecimalToNano("12.5", 9)
        assertNotNull(parsed)
        assertEquals(BigInteger.valueOf(12_500_000_000L), parsed)

        val formatted = BrotherhoodUiFormatters.formatNanoAmount(
            rawNano = BigInteger.valueOf(12_500_000_000L),
            symbol = BrotherhoodConfig.FI_SYMBOL,
        )
        assertEquals("12.5 FI", formatted)

        val shortAddr = BrotherhoodUiFormatters.shortAddress(BrotherhoodConfig.FI_ADDRESS_RAW)
        assertTrue(shortAddr.contains("…"))
    }

    @Test
    fun `BrotherhoodWalletSessionHolder updates active wallet address and testnet flag`() {
        val holder = BrotherhoodWalletSessionHolder()
        assertEquals(null, holder.walletAddressFlow.value)
        assertTrue(holder.isTestnetFlow.value)

        holder.updateActiveWallet(BrotherhoodConfig.FI_ADDRESS_RAW, testnet = true)
        assertEquals(BrotherhoodConfig.FI_ADDRESS_RAW, holder.walletAddressFlow.value)
    }

    @Test
    fun `TrackedPersonalTokenEntity distinguishes FI Admin reference stablecoin`() {
        val stablecoin = TrackedPersonalTokenEntity(
            minterAddress = BrotherhoodConfig.FI_ADDRESS_RAW,
            ownerWalletAddress = BrotherhoodConfig.FI_ADDRESS_RAW,
            ownerFiWalletAddress = BrotherhoodConfig.FI_ADDRESS_RAW,
            userPersonalWalletAddress = BrotherhoodConfig.FI_ADDRESS_RAW,
            name = "BrotherHood Admin USD (Reference)",
            symbol = "FIUSD",
            imageUrl = null,
            totalSupplyRaw = "1000000000000",
            userBalanceRaw = "25000000000",
            isAdminReferenceStablecoin = true,
            updatedAtMs = 1_700_000_000_000L,
        )
        assertTrue(stablecoin.isAdminReferenceStablecoin)
        assertEquals("FIUSD", stablecoin.symbol)
    }

    @Test
    fun `DeveloperBubbleFeature unlocks after 7 taps and toggles telemetry sheet`() {
        val telemetry = TelemetryRepository(context = null)
        val rateLimiter = ProviderRateLimiter(telemetry)
        val fakeDao = FakeFeatureTestDao()
        val hydrator = AccountStateHydrator(
            dao = fakeDao,
            rateLimiter = rateLimiter,
            telemetryRepository = telemetry,
            transport = { _, _ -> emptyList() },
        )
        val devFeature = DeveloperBubbleFeature(
            telemetryRepository = telemetry,
            hydrator = hydrator,
        )

        assertFalse(devFeature.isDeveloperModeEnabled.value)
        repeat(6) {
            val remaining = devFeature.registerSecretVersionTap()
            assertTrue(remaining > 0)
            assertFalse(devFeature.isDeveloperModeEnabled.value)
        }
        val finalRemaining = devFeature.registerSecretVersionTap()
        assertEquals(0, finalRemaining)
        assertTrue(devFeature.isDeveloperModeEnabled.value)

        devFeature.toggleSheetExpanded()
        assertTrue(devFeature.isSheetExpanded.value)
        devFeature.selectTab(DeveloperPanelTab.CACHE)
        assertEquals(DeveloperPanelTab.CACHE, devFeature.selectedTab.value)
        devFeature.closeSheet()
        assertFalse(devFeature.isSheetExpanded.value)
    }

    @Test
    fun `All 6 BrotherHood feature ViewModels build intents and manage state cleanly`() {
        val telemetry = TelemetryRepository(context = null)
        val rateLimiter = ProviderRateLimiter(telemetry)
        val fakeDao = FakeFeatureTestDao()
        val hydrator = AccountStateHydrator(
            dao = fakeDao,
            rateLimiter = rateLimiter,
            telemetryRepository = telemetry,
            transport = { _, _ -> emptyList() },
        )
        val funder = AutoFiWalletFunder(hydrator, telemetry)
        val brotherhoodRepo = BrotherhoodRepository(fakeDao, hydrator)
        val personalRepo = PersonalJettonRepository(fakeDao, hydrator)
        val cityRepo = CityNetworkRepository(fakeDao, hydrator)
        val daoRepo = DaoRepository(fakeDao, hydrator)
        val lotteryRepo = LotteryRepository(hydrator)
        val dnsRepo = BroDnsRepository(fakeDao, hydrator)
        val sessionHolder = BrotherhoodWalletSessionHolder()
        sessionHolder.updateActiveWallet(BrotherhoodConfig.FI_ADDRESS_RAW, testnet = true)

        // 1. BrotherhoodHubFeature
        val hubFeature = BrotherhoodHubFeature(brotherhoodRepo, funder, sessionHolder)
        hubFeature.selectSubTab(BrotherhoodHubSubTab.CLAIM.key)
        assertEquals("claim", hubFeature.selectedSubTab.value)
        hubFeature.claimWeeklyGrant()
        var attempts = 0
        while (hubFeature.pendingIntent.value == null && attempts < 50) {
            Thread.sleep(10)
            attempts++
        }
        assertNotNull(hubFeature.pendingIntent.value)
        hubFeature.consumePendingIntent()

        // 2. PersonalTokenFeature + MutualCreditSwapSheet
        val personalFeature = PersonalTokenFeature(personalRepo, brotherhoodRepo, sessionHolder)
        assertTrue(personalFeature.isBuyCreditDirection.value)
        personalFeature.toggleSwapDirection()
        assertFalse(personalFeature.isBuyCreditDirection.value)
        personalFeature.updateSwapAmountInput("25")
        assertEquals("25", personalFeature.swapAmountInput.value)

        // 3. CityNetworkFeature
        val cityFeature = CityNetworkFeature(cityRepo, brotherhoodRepo, sessionHolder)
        cityFeature.autoDetectFromLatLng(28.6139, 77.2090)
        assertTrue(cityFeature.selectedH3Cell.value.startsWith("81"))

        // 4. DaoFeature
        val daoFeature = DaoFeature(daoRepo, brotherhoodRepo, sessionHolder)
        daoFeature.selectSubTab("treasury")
        assertEquals("treasury", daoFeature.subTab.value)

        // 5. LotteryFeature
        val lotteryFeature = LotteryFeature(lotteryRepo, brotherhoodRepo, sessionHolder)
        lotteryFeature.selectRound(2L)
        assertEquals(2L, lotteryFeature.selectedRoundId.value)

        // 6. BroDnsFeature
        val dnsFeature = BroDnsFeature(dnsRepo, brotherhoodRepo, sessionHolder)
        dnsFeature.updateSearchQuery("satoshi.bro")
        assertEquals("satoshi.bro", dnsFeature.searchQuery.value)
    }
}

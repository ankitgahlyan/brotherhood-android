package com.tonapps.wallet.features.brotherhood

import com.tonapps.wallet.features.brotherhood.city.CityNetworkFeature
import com.tonapps.wallet.features.brotherhood.common.BrotherhoodWalletSessionHolder
import com.tonapps.wallet.features.brotherhood.dao.DaoFeature
import com.tonapps.wallet.features.brotherhood.dev.DeveloperBubbleFeature
import com.tonapps.wallet.features.brotherhood.dns.BroDnsFeature
import com.tonapps.wallet.features.brotherhood.hub.BrotherhoodHubFeature
import com.tonapps.wallet.features.brotherhood.lottery.LotteryFeature
import com.tonapps.wallet.features.brotherhood.personal.PersonalTokenFeature
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val brotherhoodFeaturesModule = module {
    single { BrotherhoodWalletSessionHolder() }

    viewModel { BrotherhoodHubFeature(get(), get(), get()) }
    viewModel { PersonalTokenFeature(get(), get(), get()) }
    viewModel { CityNetworkFeature(get(), get(), get()) }
    viewModel { DaoFeature(get(), get(), get()) }
    viewModel { LotteryFeature(get(), get(), get()) }
    viewModel { BroDnsFeature(get(), get(), get()) }
    viewModel { DeveloperBubbleFeature(get(), get()) }
}

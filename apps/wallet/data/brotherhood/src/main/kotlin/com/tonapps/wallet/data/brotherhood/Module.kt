package com.tonapps.wallet.data.brotherhood

import com.tonapps.wallet.data.brotherhood.autofund.AutoFiWalletFunder
import com.tonapps.wallet.data.brotherhood.db.BrotherhoodDatabase
import com.tonapps.wallet.data.brotherhood.hydrator.AccountStateHydrator
import com.tonapps.wallet.data.brotherhood.hydrator.OkHttpToncenterV3Transport
import com.tonapps.wallet.data.brotherhood.hydrator.ToncenterV3Transport
import com.tonapps.wallet.data.brotherhood.network.ProviderRateLimiter
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import com.tonapps.wallet.data.brotherhood.repo.BroDnsRepository
import com.tonapps.wallet.data.brotherhood.repo.BrotherhoodRepository
import com.tonapps.wallet.data.brotherhood.repo.CityNetworkRepository
import com.tonapps.wallet.data.brotherhood.repo.DaoRepository
import com.tonapps.wallet.data.brotherhood.repo.LotteryRepository
import com.tonapps.wallet.data.brotherhood.repo.PersonalJettonRepository
import org.koin.dsl.module

val brotherhoodDataModule = module {
    single { BrotherhoodDatabase.getInstance(get()) }
    single { get<BrotherhoodDatabase>().brotherhoodDao() }

    single { TelemetryRepository(get()) }
    single { ProviderRateLimiter(get()) }
    single<ToncenterV3Transport> { OkHttpToncenterV3Transport() }
    single {
        AccountStateHydrator(
            dao = get(),
            rateLimiter = get(),
            telemetryRepository = get(),
            transport = get(),
        )
    }
    single {
        AutoFiWalletFunder(
            hydrator = get(),
            telemetryRepository = get(),
        )
    }

    single { BrotherhoodRepository(get(), get()) }
    single { PersonalJettonRepository(get(), get()) }
    single { CityNetworkRepository(get(), get()) }
    single { DaoRepository(get(), get()) }
    single { LotteryRepository(get()) }
    single { BroDnsRepository(get(), get()) }
}

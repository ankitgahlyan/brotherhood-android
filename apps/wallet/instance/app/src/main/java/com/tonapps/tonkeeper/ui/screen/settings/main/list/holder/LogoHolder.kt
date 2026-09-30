package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.tonapps.extensions.appVersionCode
import com.tonapps.extensions.appVersionName
import com.tonapps.tonkeeper.ui.screen.dev.DevScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.wallet.data.brotherhood.network.TelemetryRepository
import com.tonapps.wallet.localization.Localization
import org.koin.core.context.GlobalContext
import uikit.navigation.Navigation

class LogoHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
) : Holder<Item.Logo>(parent, R.layout.view_settings_logo, onClick) {

    private val logoView = findViewById<LottieAnimationView>(R.id.logo)
    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    init {
        itemView.setOnClickListener {
            val telemetry = GlobalContext.getOrNull()?.getOrNull<TelemetryRepository>()
            if (telemetry != null && !telemetry.isDeveloperModeEnabled.value) {
                val remaining = telemetry.registerSecretVersionTap()
                if (remaining == 0) {
                    Toast.makeText(
                        context,
                        "BrotherHood Developer Bubble Unlocked ⚡",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else if (remaining <= 3) {
                    Toast.makeText(
                        context,
                        "$remaining taps to unlock BrotherHood Developer Mode",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                Navigation.from(context)?.add(DevScreen.newInstance())
            }
        }
        val color = context.iconPrimaryColor
        logoView.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
    }

    override fun onBind(item: Item.Logo) {
        val builder = StringBuilder()
        builder.append("brotherhood • ")
        builder.append(context.getString(Localization.version, context.appVersionName, context.appVersionCode))
        builder.append("\n")
        builder.append(item.installerSource.title)

        versionView.text = builder
    }
}

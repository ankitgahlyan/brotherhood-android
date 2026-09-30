plugins {
    id("target.android.compose")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tonapps.wallet.features.brotherhood"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.debugTooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tvm)

    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.data.brotherhood)
    implementation(projects.lib.brotherhood)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.core)
    implementation(projects.ui.uikit.color)
    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.list)

    implementation(projects.lib.icu)
    implementation(projects.lib.extensions)
    implementation(projects.lib.base64)
    implementation(projects.lib.log)
    implementation(projects.lib.network)
    implementation(projects.lib.qr)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    enabled = true
}

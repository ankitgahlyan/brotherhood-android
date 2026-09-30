plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.lib.base64)
    api(projects.lib.extensions)

    api(libs.ton.tvm)
    api(libs.ton.crypto)
    api(libs.ton.tlb)
    api(libs.ton.blockTlb)
    api(libs.ton.contract)
    api(libs.kotlin.bignum)
    api(libs.kotlinx.io.core)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    enabled = true
}

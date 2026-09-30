plugins {
    id("target.android.library")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.lib.brotherhood)

    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)

    implementation(projects.lib.base64)
    implementation(projects.lib.extensions)
    implementation(projects.lib.log)
    implementation(projects.lib.network)

    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    enabled = true
}

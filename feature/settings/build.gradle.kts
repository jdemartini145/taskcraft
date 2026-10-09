plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.settings"
}

dependencies {
    implementation(libs.billing.ktx)
}

plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.systems"
}

dependencies {
    implementation(libs.coil.compose)
}

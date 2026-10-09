plugins {
    alias(libs.plugins.aphid.android.library)
    alias(libs.plugins.aphid.android.compose)
}

android {
    namespace = "pe.aphid.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3.adaptive)
    api(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.core.ktx)
}

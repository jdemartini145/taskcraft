plugins {
    alias(libs.plugins.aphid.android.library)
    alias(libs.plugins.aphid.hilt)
}

android {
    namespace = "pe.aphid.core.notifications"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}

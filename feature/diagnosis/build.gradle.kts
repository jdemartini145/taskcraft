plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.diagnosis"
}

dependencies {
    implementation(project(":core:ai"))
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.coil.compose)
}

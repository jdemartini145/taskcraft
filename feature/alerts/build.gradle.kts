plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.alerts"
}

dependencies {
    implementation(project(":core:notifications"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.androidx.work.testing)
}

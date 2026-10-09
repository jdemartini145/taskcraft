plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.log"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.vico.compose.m3)
}

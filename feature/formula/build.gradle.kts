plugins {
    alias(libs.plugins.aphid.android.feature)
}

android {
    namespace = "pe.aphid.feature.formula"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
}

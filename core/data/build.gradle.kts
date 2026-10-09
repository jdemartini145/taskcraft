plugins {
    alias(libs.plugins.aphid.android.library)
    alias(libs.plugins.aphid.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pe.aphid.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}

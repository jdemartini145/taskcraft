plugins {
    alias(libs.plugins.aphid.android.library)
    alias(libs.plugins.aphid.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pe.aphid.core.ai"
    buildFeatures.buildConfig = true
    // Solo la URL del proxy (pública). Nunca se incluye una clave de API en el APK.
    defaultConfig.buildConfigField(
        "String",
        "AI_PROXY_URL",
        "\"" + (providers.gradleProperty("aphid.aiProxyUrl").orNull ?: "") + "\"",
    )
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.litert)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    testImplementation(libs.ktor.client.mock)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Los JSON semilla viven en los assets de :core:database; las pruebas los leen desde ahí.
    systemProperty("aphid.seedDir", file("../database/src/main/assets").absolutePath)
}

// Alias para que `./gradlew testDebugUnitTest` también ejecute las pruebas del motor.
tasks.register("testDebugUnitTest") { dependsOn(tasks.test) }

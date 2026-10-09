import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureApplication(ext: ApplicationExtension) {
    ext.compileSdk = AphidSdk.COMPILE
    ext.defaultConfig.minSdk = AphidSdk.MIN
    ext.defaultConfig.targetSdk = AphidSdk.TARGET
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    ext.lint.abortOnError = true
    ext.lint.checkReleaseBuilds = true
    configureKotlinCommon()
}

internal fun Project.configureLibrary(ext: LibraryExtension) {
    ext.compileSdk = AphidSdk.COMPILE
    ext.defaultConfig.minSdk = AphidSdk.MIN
    ext.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    ext.testOptions.targetSdk = AphidSdk.TARGET
    ext.lint.targetSdk = AphidSdk.TARGET
    ext.lint.abortOnError = true
    configureKotlinCommon()
}

/** Kotlin, JUnit 5 (Jupiter) y motor vintage para pruebas JUnit 4 (Robolectric / Compose). */
private fun Project.configureKotlinCommon() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxHeapSize = "2g"
        // Módulos sin pruebas propias no deben romper el build.
        failOnNoDiscoveredTests.set(false)
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("junit4"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testRuntimeOnly", libs.lib("junit-vintage-engine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        when (val android = extensions.getByName("android")) {
            is ApplicationExtension -> android.buildFeatures.compose = true
            is LibraryExtension -> android.buildFeatures.compose = true
        }
        dependencies {
            val bom = libs.lib("compose-bom")
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("testImplementation", platform(bom))
            add("implementation", libs.lib("compose-ui"))
            add("implementation", libs.lib("compose-foundation"))
            add("implementation", libs.lib("compose-material3"))
            add("implementation", libs.lib("compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("compose-ui-tooling"))
            add("debugImplementation", libs.lib("compose-ui-test-manifest"))
            add("testImplementation", libs.lib("compose-ui-test-junit4"))
            add("testImplementation", libs.lib("robolectric"))
            add("androidTestImplementation", libs.lib("compose-ui-test-junit4"))
        }
    }
}

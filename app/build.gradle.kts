plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.liquidmessages"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.liquidmessages"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.kyant0:backdrop:2.0.1")
    constraints {
        implementation("androidx.compose.ui:ui:1.7.5")
        implementation("androidx.compose.foundation:foundation:1.7.5")
        implementation("androidx.compose.animation:animation:1.7.5")
        implementation("androidx.compose.runtime:runtime:1.7.5")
    }
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Keep the transitive Compose stack aligned with the current Android plugin.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "androidx.compose.ui" || requested.group == "androidx.compose.foundation" || requested.group == "androidx.compose.animation" || requested.group == "androidx.compose.runtime") {
                useVersion("1.7.5")
            }
        }
    }
}

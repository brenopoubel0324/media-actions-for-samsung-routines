plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.local.mediaactions.core"
    compileSdk = 36
    defaultConfig { minSdk = 33 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint {
        warningsAsErrors = true
        // Pinned dependency upgrades require explicit review. This Maven advisory
        // depends on network metadata and must not change a fixed source's result.
        disable += "NewerVersionAvailable"
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api("com.google.android.gms:play-services-wearable:20.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

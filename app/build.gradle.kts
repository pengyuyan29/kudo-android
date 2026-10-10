import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val path = providers.gradleProperty("signingPropertiesFile").orElse("signing.properties").get()
    val configuration = rootProject.file(path)
    if (configuration.isFile) {
        configuration.inputStream().use { load(it) }
    } else if (providers.gradleProperty("signingPropertiesFile").isPresent) {
        throw GradleException("The specified signingPropertiesFile does not exist.")
    }
}

fun signingOptions(variant: String): Map<String, String> = mapOf(
    "storeFile" to "KEYSTORE_PATH",
    "storePassword" to "STORE_PASSWORD",
    "keyAlias" to "KEY_ALIAS",
    "keyPassword" to "KEY_PASSWORD",
    "storeType" to "KEYSTORE_TYPE",
).mapNotNull { (property, environment) ->
    val value = providers.environmentVariable("${variant.uppercase()}_$environment").orNull
        ?: signingProperties.getProperty("$variant.$property")
    value?.takeIf { it.isNotBlank() }?.let { property to it }
}.toMap()

val requiredSigningOptions = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val debugSigning = signingOptions("debug")
val releaseSigning = signingOptions("release")

fun validateSigning(variant: String, options: Map<String, String>) {
    val missing = requiredSigningOptions.filter { it !in options }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "$variant signing is incomplete: ${missing.joinToString()}. " +
                "Configure signing.properties or the ${variant.uppercase()}_* environment variables.",
        )
    }
    if (!rootProject.file(options.getValue("storeFile")).isFile) {
        throw GradleException("$variant keystore does not exist. Check the signing storeFile configuration.")
    }
}

android {
    namespace = "com.kudo.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kudo.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get()
            .toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
            ?: throw GradleException("versionCode must be an integer between 1 and 2100000000.")
        versionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (debugSigning.isNotEmpty()) {
            validateSigning("Debug", debugSigning)
            getByName("debug") {
                storeFile = rootProject.file(debugSigning.getValue("storeFile"))
                storePassword = debugSigning.getValue("storePassword")
                keyAlias = debugSigning.getValue("keyAlias")
                keyPassword = debugSigning.getValue("keyPassword")
                storeType = debugSigning["storeType"] ?: "PKCS12"
            }
        }
        create("release") {
            storeFile = releaseSigning["storeFile"]?.let { rootProject.file(it) }
            storePassword = releaseSigning["storePassword"]
            keyAlias = releaseSigning["keyAlias"]
            keyPassword = releaseSigning["keyPassword"]
            storeType = releaseSigning["storeType"] ?: "PKCS12"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Rejects Release builds without a complete, private signing configuration."
    doLast { validateSigning("Release", releaseSigning) }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-gif:3.3.0")
    implementation("com.github.penfeizhou.android.animation:apng:3.0.5")
    implementation("com.github.penfeizhou.android.animation:awebp:3.0.5")
    implementation("com.github.penfeizhou.android.animation:avif:3.0.5")
    implementation("org.aomedia.avif.android:avif:1.1.1.14d8e3c4")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

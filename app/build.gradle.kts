import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
val keystoreProps = Properties().apply {
    // signing secrets stay outside the repository: ~/.chessdude-signing/keystore.properties (or KEYSTORE_PROPERTIES)
    val f = file(System.getenv("KEYSTORE_PROPERTIES") ?: (System.getProperty("user.home") + "/.chessdude-signing/keystore.properties"))
    if (f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "org.offlinechess"
    compileSdk = 35
    defaultConfig {
        applicationId = "in.xiox.chessdude"
        minSdk = 28
        targetSdk = 35
        versionCode = 10
        versionName = "1.3.6"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions { annotationProcessorOptions { arguments += mapOf("room.schemaLocation" to "$projectDir/schemas") } }
    }
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile")); storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias"); keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
    }
    androidResources { noCompress += "nnue" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/libstockfish.so" } }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":chessrules"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
tasks.register<Exec>("buildStockfish") {
    workingDir(rootDir)
    commandLine("bash", "scripts/build-native.sh")
    inputs.dir(rootProject.file("vendor/stockfish/src"))
    inputs.file(rootProject.file("native/CMakeLists.txt"))
    outputs.file(file("src/main/jniLibs/arm64-v8a/libstockfish.so"))
}
tasks.named("preBuild") { dependsOn("buildStockfish") }

import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val lp = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun sec(env: String, prop: String): String? = System.getenv(env) ?: lp.getProperty(prop)
val ks = sec("PF_KEYSTORE", "pf.keystore")

android {
    namespace = "id.yanuar.bf01"
    compileSdk = 36
    defaultConfig {
        applicationId = "id.yanuarmoo.pocketfriend"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.3.8"
    }
    // Signing rilis: dibaca dari env (PF_KEYSTORE, PF_KEYSTORE_PASSWORD, PF_KEY_ALIAS, PF_KEY_PASSWORD)
    // atau dari local.properties (pf.keystore, pf.keystorePassword, pf.keyAlias, pf.keyPassword). Rahasia TIDAK di repo.
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks); storePassword = sec("PF_KEYSTORE_PASSWORD", "pf.keystorePassword")
            keyAlias = sec("PF_KEY_ALIAS", "pf.keyAlias"); keyPassword = sec("PF_KEY_PASSWORD", "pf.keyPassword")
        }
    }
    buildTypes {
        debug {
            // Build debug (sideload) dapat id terpisah supaya bisa terpasang berdampingan dengan rilis.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 dimatikan: minify gagal (missing class tink/errorprone, ktor, slf4j); butuh keep rules + uji di HP dulu.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("roborazzi.test.record", "true"); it.systemProperty("robolectric.pixelCopyRenderMode", "hardware"); it.maxHeapSize = "3g"; it.forkEvery = 1 } } }
    packaging { resources { excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/DEPENDENCIES") } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-websockets:2.3.12")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("io.github.jaredmdobson:concentus:1.0.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.46.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

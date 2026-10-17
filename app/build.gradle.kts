import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------------------------
// Automatic versioning (see SPEC.md → "Versioning & release").
// app/version.properties holds VERSION_CODE, VERSION_NAME and SOURCE_HASH. When the hash of the
// app's source (src/main, this build file, the version catalog) differs from SOURCE_HASH, the
// build bumps VERSION_CODE by 1 and the patch part of VERSION_NAME, then records the new hash.
// ---------------------------------------------------------------------------------------------

/** Fingerprint of everything that changes what ships in the APK. Re-evaluated on every build. */
abstract class SourceHashValueSource : ValueSource<String, SourceHashValueSource.Params> {
    interface Params : ValueSourceParameters {
        val baseDir: DirectoryProperty
        val inputs: ConfigurableFileCollection
    }

    override fun obtain(): String {
        val base = parameters.baseDir.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        parameters.inputs.files
            .flatMap { root -> if (root.isDirectory) root.walkTopDown().filter { it.isFile }.toList() else listOf(root) }
            .filter { it.exists() }
            .sortedBy { it.relativeTo(base).invariantSeparatorsPath }
            .forEach { f ->
                digest.update(f.relativeTo(base).invariantSeparatorsPath.toByteArray())
                // Normalise line endings so a CRLF/LF checkout difference isn't a "code change".
                digest.update(f.readBytes().filter { it != '\r'.code.toByte() }.toByteArray())
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val versionFile: File = file("version.properties")
val versionProps = Properties().apply {
    providers.fileContents(layout.projectDirectory.file("version.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
val currentSourceHash: String = providers.of(SourceHashValueSource::class) {
    parameters {
        baseDir.set(rootProject.layout.projectDirectory)
        inputs.from(file("src/main"), file("build.gradle.kts"), rootProject.file("gradle/libs.versions.toml"))
    }
}.get()

var appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
var appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0")
val recordedHash = versionProps.getProperty("SOURCE_HASH", "")

if (recordedHash != currentSourceHash) {
    if (recordedHash.isNotEmpty()) {
        appVersionCode += 1
        val parts = appVersionName.split(".").map { it.toIntOrNull() ?: 0 }.toMutableList()
        while (parts.size < 3) parts.add(0)
        parts[2] += 1
        appVersionName = parts.joinToString(".")
        logger.lifecycle("BikersThrill: code changed -> version bumped to $appVersionName ($appVersionCode)")
    }
    versionFile.writeText(
        """
        |# Managed by the build – see SPEC.md "Versioning & release".
        |# Edit VERSION_NAME major/minor by hand if you like; the build bumps the patch and VERSION_CODE
        |# automatically whenever the source hash changes. Never lower VERSION_CODE.
        |VERSION_CODE=$appVersionCode
        |VERSION_NAME=$appVersionName
        |SOURCE_HASH=$currentSourceHash
        |""".trimMargin(),
    )
}

// Release signing: create keystore.properties in the project root (never commit it) with
// storeFile, storePassword, keyAlias, keyPassword. Without it, release builds are unsigned.
val keystoreProps = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(it.reader()) }
}

android {
    namespace = "com.satya.biker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.satya.biker"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // ./gradlew :app:assembleDebug -PuseEmulators=true -> talk to the local Firebase emulators
            // (used to capture store screenshots with demo data). Never on in release builds.
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS",
                (providers.gradleProperty("useEmulators").orNull == "true").toString())
            // Host of the emulators as seen from the device (-PemulatorHost=<PC LAN IP> if 10.0.2.2 is unreachable).
            buildConfigField("String", "FIREBASE_EMULATOR_HOST",
                "\"" + (providers.gradleProperty("emulatorHost").orNull ?: "10.0.2.2") + "\"")
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            optimization {
                enable = false
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.osmdroid)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

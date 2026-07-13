import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

kotlin {
    jvmToolchain(21)
}

// --- shiroikuma-nekokan fork: signing + versioning (see gradle.properties + build-apk skill) ---
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val forkVersionName = "${project.property("VERSION_NAME")}+${project.property("BUILD_NUMBER")}"
val forkVersionCode = project.property("VERSION_CODE").toString().toInt() * 10000 +
    project.property("BUILD_NUMBER").toString().toInt()

base {
    archivesName = "shiroikuma-nekokan_${forkVersionName}_arm64-v8a"
}

android {
    namespace = project.property("APP_NAMESPACE").toString()
    compileSdk = 36

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = 23
        targetSdk = 36
        versionCode = forkVersionCode
        versionName = forkVersionName

        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true

        resourceConfigurations += listOf("ar", "be", "bg", "bn", "bn-rIN", "bs", "cs", "da", "de", "el-rGR", "en", "eo", "es", "es-rAR", "et", "fa", "fi", "fr", "gl", "he-rIL", "hi", "hr", "hu", "in-rID", "is", "it", "ja", "ko", "lt", "lv", "nb-rNO", "nl", "oc", "peo", "pl", "pt", "pt-rBR", "pt-rPT", "ro-rRO", "ru", "sk", "sl", "sr", "sv", "ta", "tr", "uk", "vi", "zh-rCN", "zh-rTW")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "showDonate", "true")
        buildConfigField("boolean", "showRateOnGooglePlay", "false")
        buildConfigField("boolean", "useAcraCrashReporter", "true")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    flavorDimensions.add("type")
    productFlavors {
        create("foss") {
            dimension = "type"
            isDefault = true
        }
        create("gplay") {
            dimension = "type"

            // Google doesn't allow donation links
            buildConfigField("boolean", "showDonate", "false")
            buildConfigField("boolean", "showRateOnGooglePlay", "true")

            // Google Play already sends crashes to the Google Play Console
            buildConfigField("boolean", "useAcraCrashReporter", "false")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    sourceSets {
        getByName("test") {
            resources.srcDirs("src/test/res")
        }
    }

    // Starting with Android Studio 3 Robolectric is unable to find resources.
    // The following allows it to find the resources.
    testOptions.unitTests.isIncludeAndroidResources = true
    tasks.withType<Test>().configureEach {
        testLogging {
            events("started", "passed", "skipped", "failed")
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    compileOptions {
        encoding = "UTF-8"

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.constraintlayout.constraintlayout)
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.core.core.remoteviews)
    implementation(libs.androidx.core.core.splashscreen)
    implementation(libs.androidx.exifinterface.exifinterface)
    implementation(libs.androidx.palette.palette)
    implementation(libs.androidx.preference.preference)
    implementation(libs.com.google.android.material.material)
    coreLibraryDesugaring(libs.com.android.tools.desugar.jdk.libs)

    // Compose
    implementation(libs.androidx.activity.activity.compose)
    val composeBom = platform(libs.androidx.compose.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.compose.material.material.icons.extended)
    implementation(libs.androidx.compose.ui.ui.tooling.preview.android)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.ui.test.junit4)

    // Third-party
    implementation(libs.com.journeyapps.zxing.android.embedded)
    implementation(libs.com.github.yalantis.ucrop)
    implementation(libs.com.google.zxing.core)
    implementation(libs.org.apache.commons.commons.csv)
    implementation(libs.com.jaredrummler.colorpicker)
    implementation(libs.net.lingala.zip4j.zip4j)

    // Crash reporting
    implementation(libs.bundles.acra)

    // Testing
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit.junit)
    testImplementation(libs.org.robolectric.robolectric)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.junit.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso.espresso.core)
}

// --- shiroikuma-nekokan fork: build the release APK (foss flavor), copy to ~/tmp, bump BUILD_NUMBER ---
tasks.register("buildApk") {
    description = "Build the foss release APK, copy it to ~/tmp, and bump BUILD_NUMBER for next time."
    dependsOn("assembleFossRelease")
    // Capture project state at configuration time so the action is configuration-cache compatible.
    val fvName = forkVersionName
    val fvCode = forkVersionCode
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/foss/release")
    val userHome = providers.systemProperty("user.home")
    val propsFile = rootProject.file("gradle.properties")
    val currentBuildNumber = project.property("BUILD_NUMBER").toString().toInt()
    doLast {
        val apkName = "shiroikuma-nekokan_${fvName}_arm64-v8a.apk"
        val outputDir = releaseApkDir.get().asFile
        val targetDir = File(userHome.get(), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("[1;36m>>> ${targetFile.absolutePath}[0m")
            println("[1;36m>>> versionCode $fvCode[0m")
        } ?: throw GradleException("No APK found in $outputDir")

        // Auto-increment BUILD_NUMBER for the next build.
        val nextBuildNumber = currentBuildNumber + 1
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$currentBuildNumber",
                "BUILD_NUMBER=$nextBuildNumber"
            )
        )
        println("[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber[0m")
    }
}

tasks.register("copyRawResFiles", Copy::class) {
    from(
        layout.projectDirectory.file("../CHANGELOG.md"),
        layout.projectDirectory.file("../PRIVACY.md")
    )
    into(layout.projectDirectory.dir("src/main/res/raw"))
    rename { it.lowercase() }
}.also {
    tasks.preBuild.dependsOn(it)
    tasks.getByName<Delete>("clean") {
        val filesNamesToDelete = listOf("CHANGELOG", "PRIVACY")
        filesNamesToDelete.forEach { fileName ->
            delete(layout.projectDirectory.file("src/main/res/raw/${fileName.lowercase()}.md"))
        }
    }
}

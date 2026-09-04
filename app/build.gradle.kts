plugins {
    // KSP is not yet compatible with AGP 9's built-in Kotlin (google/ksp#2615), so
    // android.builtInKotlin=false (gradle.properties) is set and the classic
    // kotlin-android plugin supplies Kotlin support instead.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.scholze.saldo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scholze.saldo"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    // runGlanceAppWidgetUnitTest (glance-appwidget-testing) builds a real android.os.Bundle
    // under the hood to carry the fake widget size; without this the plain android.jar stub
    // throws "Method putInt not mocked" on the very first call.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Cada versão do schema fica versionada em app/schemas: é o que a AutoMigration usa para
// gerar a migração e o que o MigrationTest lê para criar um banco na versão antiga.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    constraints {
        // room-testing (room-migration 2.8.4) precisa de kotlinx-serialization >= 1.8.1, mas o
        // classpath principal resolve 1.7.3 via lifecycle-viewmodel-savedstate, e a resolução
        // consistente do AGP força a MESMA versão no androidTest — o MigrationTestHelper caía
        // com AbstractMethodError. Uma constraint não adiciona dependência: só levanta a versão
        // do artefato que já vem transitivamente.
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing 2.8.4 exige kotlinx-serialization >= 1.8.1 no androidTest")
        }
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    androidTestImplementation(libs.androidx.datastore.preferences.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.rules)

    testImplementation(libs.junit)
    testImplementation(libs.json.jvm)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

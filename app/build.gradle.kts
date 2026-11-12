plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zeta.fondo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zeta.fondo"
        // v44.26: Android 10 o más nuevo (94 % de los teléfonos en uso, oct. 2026). Quita los caminos
        // de Android 8 y 9, que eran los más frágiles (imágenes, Galería, permisos).
        minSdk = 29
        targetSdk = 36
        versionCode = 76
        versionName = "44.28.1"
        manifestPlaceholders["zetaAppLabel"] = "@string/app_name"
        // v48: solo procesadores ARM (todos los teléfonos Android reales). Quita las librerías
        // x86/x86_64 de ONNX Runtime, que solo sirven para emuladores de PC.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Una variante BETA, separada de instalaciones anteriores, SIEMPRE firmada.
    // Esta clave es PUBLICA, exclusiva de pruebas, y NUNCA se usará para Google Play.
    // Conserva el mismo certificado entre compilaciones para poder actualizar la beta.
    // NO confundir esta clave de pruebas con la clave privada de producción.
    val prodStore = System.getenv("ZETA_KEYSTORE_PATH")
    val prodPass = System.getenv("ZETA_KEYSTORE_PASSWORD")
    val prodAlias = System.getenv("ZETA_KEY_ALIAS")
    val prodKeyPass = System.getenv("ZETA_KEY_PASSWORD")
    val prodSigningConfigured = listOf(prodStore, prodPass, prodAlias, prodKeyPass)
        .all { !it.isNullOrBlank() }

    signingConfigs {
        create("betaPublicTestOnly") {
            storeFile = file("zeta-beta-public-only.jks")
            storePassword = "publicBetaOnly-2026"
            keyAlias = "zeta_beta"
            keyPassword = "publicBetaOnly-2026"
        }
        if (prodSigningConfigured) {
            create("zetaPrivateRelease") {
                storeFile = file(prodStore!!)
                storePassword = prodPass!!
                keyAlias = prodAlias!!
                keyPassword = prodKeyPass!!
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".beta"
            manifestPlaceholders["zetaAppLabel"] = "Zeta Motion Beta"
            signingConfig = signingConfigs.getByName("betaPublicTestOnly")
        }
        release {
            // Incluso con un workflow antiguo que ejecute assembleRelease,
            // NUNCA entregar un app-release-unsigned.apk.
            if (prodSigningConfigured) {
                signingConfig = signingConfigs.getByName("zetaPrivateRelease")
            } else {
                applicationIdSuffix = ".beta"
                manifestPlaceholders["zetaAppLabel"] = "Zeta Motion Beta"
                signingConfig = signingConfigs.getByName("betaPublicTestOnly")
            }
            // v44.25: R8 quita el código y los recursos que no se usan (sobre todo los miles de
            // iconos de material-icons-extended): APK más liviano y arranque más rápido.
            // Sin ofuscar y conservando el código de la app (ver proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // El modelo de «Mejorar imagen» va sin comprimir: se copia más rápido la primera vez.
    androidResources { noCompress += "onnx" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // ONNX Runtime (MIT): solo para «Mejorar imagen» (modelo incluido en el APK; sin descargas).
    // v44.25: 1.23.2 (oct. 2025) trae libonnxruntime4j_jni.so alineada a 16 KB (PR #24947):
    // Google Play lo exige a las apps con target 35+ (la 1.20.0 no lo cumplía).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
}


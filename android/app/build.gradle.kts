plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions の Secrets から渡される。無ければ debug 鍵で署名する
// （インストールは出来るが、鍵が毎回変わるので上書き更新は不可）。
val keystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.xlinkfixer"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.xlinkfixer"
        minSdk = 29          // Android 10 以降（クリップボード制限が入ったバージョン）
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // lint の警告で CI を落とさない（手元で Android Studio を開いたときに確認できる）
        abortOnError = false
    }
}

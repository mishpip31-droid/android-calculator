plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.calculator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.calculator"
        minSdk = 24
        targetSdk = 35
        // В CI номер версии растёт с каждой сборкой, чтобы новый APK ставился поверх старого.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    signingConfigs {
        // Постоянный ключ из репозитория: все сборки подписаны одинаково,
        // поэтому обновления ставятся поверх без удаления приложения.
        // Ключ публичный — для Google Play создайте свой и храните его в секрете.
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "calculator"
            keyAlias = "calculator"
            keyPassword = "calculator"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Список товаров со свайпом для удаления (ItemTouchHelper).
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    // В юнит-тестах на JVM нет android.jar с org.json — подключаем настоящую библиотеку.
    testImplementation("org.json:json:20240303")
}

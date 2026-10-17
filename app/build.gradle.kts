plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.taskreminder.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taskreminder.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // توقيع ببصمة الـ debug ليكون ملف release قابلًا للتثبيت مباشرة للاستخدام الشخصي.
            // للنشر في Google Play أنشئ keystore خاصًا بك واستبدل هذا السطر.
            signingConfig = signingConfigs.getByName("debug")
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
        abortOnError = false
    }
}

dependencies {
    // لا توجد مكتبات AndroidX/Material عمدًا: التطبيق خفيف جدًا ويعمل على Android Go.
    testImplementation("junit:junit:4.13.2")
}

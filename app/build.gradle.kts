plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bungbungi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bungbungi.app"
        minSdk = 28 // 공모전 요구사항: Android API 28 이상
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // 차량 속도(PERF_VEHICLE_SPEED)를 AAOS CarPropertyManager로도 시도해 보기 위해 사용
    useLibrary("android.car")

    signingConfigs {
        // 공모전 제출용 release APK. 별도 키가 없으면 디버그 키로 서명해서 바로 설치할 수 있게 한다.
        getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    // PERF_VEHICLE_SPEED를 Pleos 차량 서비스에 직접 요청하려고 AIDL 인터페이스를 쓴다 (Vehicle SDK가 런타임으로만 가져옴)
    implementation(libs.pleos.vehicle.aidl)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}

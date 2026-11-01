import java.util.Properties

// 서버 주소와 키: local.properties(커밋하지 않음)에 rocky.serverUrl=... rocky.apiKey=... 로 넣는다.
// 없으면 빈 값으로 빌드되고, 앱 업로드 화면의 '서버'에서 넣을 수 있다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun rockyProp(name: String): String =
    (findProperty(name) as String?) ?: localProps.getProperty(name) ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.team3.rocky"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.team3.rocky"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ROCKY_SERVER_URL", "\"${rockyProp("rocky.serverUrl")}\"")
        buildConfigField("String", "ROCKY_API_KEY", "\"${rockyProp("rocky.apiKey")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    // 안드로이드의 org.json은 로컬 단위 테스트에서 비어 있어서, 테스트에서는 원본 라이브러리를 쓴다.
    testImplementation(libs.orgjson)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
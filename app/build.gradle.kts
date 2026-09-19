import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.ksp)
}

// 릴리스 서명 정보는 저장소에 커밋하지 않는 keystore.properties 에서 읽는다.
// 파일이 없으면 서명 설정 없이 빌드된다(= 서명되지 않은 산출물).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
// 파일만 있고 값이 비어 있으면(=아직 채우지 않은 템플릿) 서명을 건너뛴다.
// 그래야 값을 채우기 전에도 릴리스 빌드가 '서명 없이' 성공한다.
val hasSigningConfig = run {
    val storeFile = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
    val storePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
    val keyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
    storeFile != null && storePassword != null && keyAlias != null &&
        rootProject.file(storeFile).exists()
}

android {
    namespace = "com.gameitstudio.dwbookmarkapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gameitstudio.dwbookmarkapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                // 키스토어 파일에 확장자가 없어 형식 자동 판별에 기대지 않고 명시한다.
                storeType = keystoreProps.getProperty("storeType") ?: "PKCS12"
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Google 공식 테스트 배너 광고 단위 ID.
            // 개발 중에는 반드시 테스트 ID를 써야 한다. 실제 ID로 직접 클릭하면 계정이 정지될 수 있다.
            resValue("string", "admob_banner_unit_id", "ca-app-pub-3940256099942544/9214589741")
        }
        release {
            // AdMob 콘솔에서 발급받은 실제 상단 배너 광고 단위 ID.
            // AndroidManifest 의 APPLICATION_ID(물결 '~')와는 다른 값이므로 혼동하지 말 것.
            // -PuseTestAds=true 로 빌드하면 난독화된 릴리스 빌드를 테스트 광고로 기기 검증할 수 있다.
            val useTestAds = (project.findProperty("useTestAds") as String?)?.toBoolean() == true
            resValue(
                "string", "admob_banner_unit_id",
                if (useTestAds) "ca-app-pub-3940256099942544/9214589741"
                else "ca-app-pub-4364320147278105/6094390818"
            )

            // keystore.properties 가 있을 때만 서명한다.
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            // R8 로 코드 축소·최적화·난독화 (Play 콘솔의 DEX 최적화 기준 충족)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    buildFeatures {
        viewBinding = true
        // AGP 9 부터는 resValue 사용 시 명시적으로 켜야 한다 (광고 단위 ID 주입에 사용)
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.activity.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.glide)
    ksp(libs.glide.compiler)
    implementation(libs.jsoup)
    implementation(libs.play.services.ads)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

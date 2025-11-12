plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdkVersion(30)
    buildToolsVersion("30.0.3")

    lintOptions {
        isAbortOnError = false
    }

    defaultConfig {
        applicationId("com.funshion.funautosend")
        minSdkVersion(21)
        targetSdkVersion(30)
        versionCode(3)
        versionName("1.0.2")

        testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "funshion"
            keyAlias = "funshionreleasekey"
            keyPassword = "funshion"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isTestCoverageEnabled = false
        }
        // 添加一个新的构建类型，用于解决签名不匹配的问题
        create("debugInstallable") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    dexOptions {
        javaMaxHeapSize = "4g"
        preDexLibraries = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    // 解决JavaMail库中的文件冲突
    packagingOptions {
        exclude("META-INF/NOTICE.md")
        exclude("META-INF/LICENSE.md")
    }
    
    // 修改APK输出文件名
    applicationVariants.all {
        outputs.all {
            val outputFileName = if (buildType.name == "release") {
                "funautosend-release.apk"
            } else {
                "${applicationId}-${versionName}-${buildType.name}.apk"
            }
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = outputFileName
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0")
    testImplementation("junit:junit:4.+")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    
    // JavaMail相关库，用于自动发送邮件
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    
    // OkHttp库，用于网络请求
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    
    // Gson库，用于JSON解析
    implementation("com.google.code.gson:gson:2.9.0")
    
    // RecyclerView库，用于列表显示
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    
    // WorkManager库，用于后台任务调度和保活
    implementation("androidx.work:work-runtime:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.6.0")
}
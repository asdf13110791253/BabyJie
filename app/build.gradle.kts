plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.probilliards.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.probilliards.ai"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        dataBinding = true
    }
    
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            // 显式将 libs 目录下的 JAR 加入 classpath
            java.srcDirs("src/main/java")
            resources.srcDirs("src/main/resources")
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // OpenCV - 使用 fileTree 包含 libs 下所有 jar，并强制刷新
    implementation(fileTree("libs") {
        include("*.jar")
        // 显式声明，确保 Gradle 不会忽略
    })
    
    // CardView
    implementation("androidx.cardview:cardview:1.0.0")
}

kapt {
    correctErrorTypes = true
}

// 添加一个任务，在编译前确认 jar 存在
tasks.register("checkOpenCvJar") {
    doLast {
        val jarFile = file("libs/opencv-java4.jar")
        if (jarFile.exists()) {
            println("✅ opencv-java4.jar 存在，大小: ${jarFile.length()} bytes")
        } else {
            println("❌ opencv-java4.jar 不存在！")
            throw GradleException("OpenCV JAR not found!")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("checkOpenCvJar")
    // 增加编译时内存
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

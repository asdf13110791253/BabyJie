
# File: app/proguard-rules.pro
# ProGuard混淆规则

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# 保留行号信息
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# 保留ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    *;
}

# HeatMapV1Android proguard rules

# Autel SDK (loaded reflectively; keep everything if AAR present)
-keep class com.autel.** { *; }
-dontwarn com.autel.**

# Ktor / kotlinx
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# iText
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**

# ZXing
-keep class com.google.zxing.** { *; }

# Gson reflection on model classes
-keep class com.yanfeng.thermaldrone.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

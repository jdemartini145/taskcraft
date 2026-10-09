# kotlinx.serialization: conserva serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class pe.aphid.**$$serializer { *; }
-keepclassmembers class pe.aphid.** { *** Companion; }
-keepclasseswithmembers class pe.aphid.** { kotlinx.serialization.KSerializer serializer(...); }

# LiteRT / TensorFlow Lite usa JNI.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ML Kit GenAI (opcional en tiempo de ejecución).
-dontwarn com.google.mlkit.genai.**

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

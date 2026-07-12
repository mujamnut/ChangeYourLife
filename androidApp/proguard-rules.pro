# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\kumar\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# Keep Room/SQLite models and serializers if needed
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep serializable classes for kotlinx.serialization
-keepclassmembers class * {
    *** Companion;
}
-keep class com.changeyourlife.cyl.domain.model.** { *; }
-keep class com.changeyourlife.cyl.data.local.entity.** { *; }

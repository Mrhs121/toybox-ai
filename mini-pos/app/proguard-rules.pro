# Keep Room entities
-keep class com.toybox.minipos.data.model.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.toybox.minipos.**$$serializer { *; }
-keepclassmembers class com.toybox.minipos.** { *** Companion; }
-keepclasseswithmembers class com.toybox.minipos.** { kotlinx.serialization.KSerializer serializer(...); }

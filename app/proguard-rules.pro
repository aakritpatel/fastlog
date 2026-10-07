-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.aakrit.fastlog.** {
    *** Companion;
}
-keepclasseswithmembers class dev.aakrit.fastlog.** {
    kotlinx.serialization.KSerializer serializer(...);
}

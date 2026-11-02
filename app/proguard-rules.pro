# kotlinx.serialization: keep generated serializers for our persisted DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class app.latch.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.latch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

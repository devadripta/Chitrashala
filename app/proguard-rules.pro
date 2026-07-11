# Preserve Room entities and DAOs
-keep class com.dripta.galleryformoto.data.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Telephoto (zoomable-image)
-keep class me.saket.telephoto.** { *; }
-dontwarn me.saket.telephoto.**

# Remove Compose debug metadata in release
-assumenosideeffects class androidx.compose.ui.tooling.preview.PreviewParameterProvider {
    *;
}
-assumenosideeffects class androidx.compose.ui.tooling.ComposableInvoker {
    *;
}

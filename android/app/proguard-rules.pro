-keep class com.ludomasterpro.engine.** { *; }
-keep class androidx.compose.** { *; }
-keepattributes *Annotation*,SourceFile,LineNumberTable
-assumenosideeffects class android.util.Log {
    public static int v(...); public static int d(...); public static int i(...);
}

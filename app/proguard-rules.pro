# Spendly release shrinking rules.
#
# Libraries used (AndroidX, Room, Navigation, kotlinx.serialization, SQLCipher) ship their own
# consumer keep rules; the release dex was inspected to confirm route serializers, backup DTO
# serializers, SpendlyDatabase_Impl and SQLCipher JNI methods survive minification.

# Strip verbose/debug/info logging from release builds (Spendly itself never logs; this also
# silences any library debug output). Warnings and errors are kept for crash diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}

# Keep file/line information out of release stack traces' source names, but keep line
# numbers so a user-provided crash report can still be retraced with the mapping file.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

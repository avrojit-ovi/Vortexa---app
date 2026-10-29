# Vortexa release rules.
# Room, Hilt, OkHttp and Media3 all ship consumer rules; these only cover our own
# reflection-free Compose entry points and keep crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata is needed for kotlinx.serialization/coroutines reflection paths.
-keep class kotlin.Metadata { *; }

# Enums used through Room type converters survive by name.
-keepclassmembers enum com.vortexa.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

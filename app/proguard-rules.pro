# ---------------------------------------------------------------------------
# catter
#
# the app reaches nothing through reflection: no keep rules for its own code.
# entry points come from the manifest (MainActivity, DownloadService,
# FileProvider) and from the consumer rules that ship with compose, lifecycle
# and core.
# ---------------------------------------------------------------------------

# readable stack traces in release crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# generics, inner class links and annotations are cheap to keep and are what
# libraries read when they walk the shrunk bytecode
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# enum constant names are persisted (ThemeMode.name in SharedPreferences), so
# they must survive renaming
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# jaudiotagger resolves its readers, writers and id3 frames by name
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# jaudiotagger probes for desktop apis that android does not ship
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn javax.swing.**

# obfuscation: let r8 rename freely, then flatten the result into a single
# package so the output no longer reveals the original layout
-allowaccessmodification
-repackageclasses 'o'

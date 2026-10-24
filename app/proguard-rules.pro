# readable stack traces in release crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# compose, lifecycle and coroutines ship their own consumer rules.
# the app keeps nothing extra: no reflection, no serialization, no JNI.

# jaudiotagger builds tag readers and writers by name
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

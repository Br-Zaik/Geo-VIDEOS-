# Geo Videos
# Keep the JavaScript engine used by NewPipe Extractor when release shrinking is enabled.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

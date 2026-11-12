# v44.25 · R8 en la versión final (release): quita lo que no se usa (sobre todo los miles de
# iconos de material-icons-extended) → APK más liviano y arranque más rápido.

# Sin ofuscar: los nombres siguen legibles en Ajustes › Diagnóstico y en los registros.
-dontobfuscate

# ONNX Runtime («Mejorar imagen»): su parte nativa crea y llama clases Java por su nombre.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Código de la app: se conserva entero. No usa reflexión, pero así ningún cambio futuro
# puede romperse solo en la versión final.
-keep class com.zeta.fondo.** { *; }

# Anotaciones que algunas bibliotecas citan pero que no existen en Android: no son código que
# se ejecute. Con -ignorewarnings, una referencia así nunca detiene la compilación en GitHub.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-ignorewarnings

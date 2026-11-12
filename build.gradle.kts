// Compatible con el flujo de GitHub que todavía utiliza Gradle 8.9.
// AGP 8.7.x requiere Gradle >= 8.9 y Java 17.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false

    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false

    id("com.google.gms.google-services") version "4.4.4" apply false
    id("maven-publish")
}

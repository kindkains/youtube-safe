pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // NewPipeExtractor und seine nanojson-Abhängigkeit
    }
}

rootProject.name = "safetube"

include(":core")

// Die Android-Module nur mit SDK einbinden. Auf dem Pi gibt es keins (aapt2 läuft nur auf x86),
// dort genügt :core.
val localProperties = file("local.properties")
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    (localProperties.exists() && localProperties.readText().contains("sdk.dir"))
if (hasAndroidSdk) {
    include(":core-android", ":parent")
}

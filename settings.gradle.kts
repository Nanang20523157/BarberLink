@file:Suppress("DEPRECATION")

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

        maven { url = uri("https://jitpack.io") }
        // Tambahkan JCenter repository
        jcenter() // Menambahkan repository JCenter untuk dependensi lama
    }
}

rootProject.name = "BarberLink"
include(":app")
 
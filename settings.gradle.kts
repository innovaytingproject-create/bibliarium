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
        // PDF-адаптер Readium тянет PdfiumAndroid и AndroidPdfViewer, а они
        // живут только на JitPack — на Maven Central их нет.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Bibliarium"
include(":app")

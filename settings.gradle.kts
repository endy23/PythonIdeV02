pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    
    maven { url = uri("https://jitpack.io") } // Add JitPack
    maven { url = uri("https://chaquo.com/maven") }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // Add JitPack
  }
}

rootProject.name = "PythonIDEv02"

include(":app")
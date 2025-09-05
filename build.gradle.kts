buildDir = File("/data/data/com.itsaky.androidide/files/gradle-build")
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false     
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

// ✅ Custom task to copy release APK
tasks.register<Copy>("exportReleaseApk") {
    dependsOn(":app:assembleRelease") // Make sure APK is built first
    from("/data/data/com.itsaky.androidide/files/gradle-build/app/outputs/apk/release/app-release.apk")
    into("/storage/emulated/0/AndroidIDEProjects/PythonIDEv02/app/build/outputs/apk/release/")
    rename("app-release.apk", "PythonIdev02.apk")
}

// ✅ Custom task to copy release APK
tasks.register<Copy>("exportDebugApk") {
    dependsOn(":app:assembleDebug") // Make sure APK is built first
    from("/data/data/com.itsaky.androidide/files/gradle-build/app/outputs/apk/debug/app-debug.apk")
    into("/storage/emulated/0/AndroidIDEProjects/PythonIDEv02/app/build/outputs/apk/debug/")
    rename("app-debug.apk", "PythonIdev02-debug.apk")
}

// ✅ Hook both tasks so they run after assemble tasks
//gradle.projectsEvaluated {
 //   tasks.named(":app:assembleRelease") {
     //   finalizedBy("exportReleaseApk")
  //  }
   // tasks.named("assembleDebug") {
  //      finalizedBy("exportDebugApk")
  //  }
//}

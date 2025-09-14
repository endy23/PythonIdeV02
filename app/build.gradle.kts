buildDir = File("/data/data/com.itsaky.androidide/files/gradle-build/app")
plugins {
    id("com.android.application")
    id("kotlin-android")
   // id("com.chaquo.python") // Add this
    
    id("com.chaquo.python") version "15.0.1"
}

android {
    namespace = "com.endyaris.pythonidev02"
    compileSdk = 33

    defaultConfig {
        
        //buildPython("/data/data/com.itsaky.androidide/files/usr/bin/python")
        applicationId = "com.endyaris.pythonidev02"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
       
    }
    
     signingConfigs {
        create("release") {
            storeFile = file(project.properties["MYAPP_STORE_FILE"] as String)
            storePassword = project.properties["MYAPP_STORE_PASSWORD"] as String
            keyAlias = project.properties["MYAPP_KEY_ALIAS"] as String
            keyPassword = project.properties["MYAPP_KEY_PASSWORD"] as String
        }
    }

    

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
}

chaquopy {
    defaultConfig {
        buildPython("/data/data/com.itsaky.androidide/files/usr/bin/python")

        pip {
            install("scipy")
            install("numpy")
            install("requests")
            install("pip")
            install("setuptools")
            install("wheel")
            install("lxml")
            install("pandas")
            install("Pillow")
            install("scapy")
        }
    }
}

dependencies {


    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    //implementation("io.github.Rosemoe.sora-editor:editor:0.21.1")
    //implementation("io.github.Rosemoe.sora-editor:language-textmate:0.21.1")
    
  //  implementation(platform("io.github.rosemoe:editor-bom:0.23.7"))
  //  implementation("io.github.rosemoe:editor")
  //  implementation("io.github.rosemoe:language-textmate")

    implementation("io.github.Rosemoe.sora-editor:editor:0.22.0")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.22.0")


    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("io.github.amrdeveloper:codeview:1.3.9") // Check for the latest version
    
    implementation("androidx.core:core-ktx:1.10.1") // ή την τελευταία έκδοση
    
    implementation("com.itsaky.androidide:logsender:2.7.1-beta")


}


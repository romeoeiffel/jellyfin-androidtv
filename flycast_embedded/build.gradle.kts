plugins {
    id("com.android.library")
}

android {
    namespace = "com.flycast.emulator"
    ndkVersion = "29.0.14206865"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

		ndk {
			abiFilters += setOf("armeabi-v7a")
		}
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_ARM_MODE=arm"
                arguments += "-DSENTRY_UPLOAD_URL=" + (System.getenv("SENTRY_UPLOAD_URL") ?: "")
                arguments += "-DUSE_OPENMP=OFF"
                arguments += "-DUSE_BREAKPAD=OFF"
                arguments += "-DANDROID_WEAK_API_DEFS=ON"
            }
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isJniDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
			path = file("../third_party/flycast/CMakeLists.txt")
			version = "3.31.6"
		}
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libz.so"
            useLegacyPackaging = true
        }
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.0.3")
    implementation("org.slf4j:slf4j-android:1.7.35")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(fileTree("libs") { include("*.aar", "*.jar") })
}

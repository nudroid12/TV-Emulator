plugins {
    id("com.android.application")
}

android {
    namespace = "com.nudroidlabs.tvemulator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nudroidlabs.tvemulator"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }
}

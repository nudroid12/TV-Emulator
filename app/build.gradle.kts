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
        versionCode = 5
        versionName = "0.5.0"
    }
}

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.se_proj"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.se_proj"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// ---------------------------------------------------------------------------
// Javadoc generation task
// Run with:  ./gradlew generateJavadoc
// Output:    app/build/docs/javadoc/
//
// Strategy: depend on compileDebugJavaWithJavac and borrow its fully-resolved
// classpath. AGP 8.x places the Android SDK JARs, AndroidX, and Firebase all
// inside that classpath, so we never need to reference android.bootClasspath
// (removed from the public API in AGP 8) or configurations directly.
// We also add the compile output directory so generated ViewBinding / R classes
// are visible to javadoc.
// ---------------------------------------------------------------------------
afterEvaluate {
    val compileDebug = tasks.named("compileDebugJavaWithJavac", JavaCompile::class.java)

    tasks.register<Javadoc>("generateJavadoc") {
        description = "Generates Javadoc for the main source set."
        group = "documentation"

        // Compile first so ViewBinding .class files exist before javadoc runs.
        dependsOn(compileDebug)

        // AGP 8.x: source directories live in .java.directories (Set<File>).
        setSource(android.sourceSets["main"].java.directories)

        // classpath = everything javac used (Android SDK + all deps) +
        //             compiled output dir (ViewBinding, R classes).
        classpath = compileDebug.get().classpath +
                    project.files(compileDebug.get().destinationDirectory)

        destinationDir = layout.buildDirectory.dir("docs/javadoc").get().asFile

        (options as StandardJavadocDocletOptions).apply {
            encoding("UTF-8")
            charSet("UTF-8")
            author(true)
            version(true)
            splitIndex(true)
            windowTitle = "Campus Gate Access System"
            docTitle    = "Campus Gate Access System — API Reference"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}


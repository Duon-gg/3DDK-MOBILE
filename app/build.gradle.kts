import java.util.Properties
plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun configuration(key: String) = System.getenv(key) ?: local.getProperty(key, "")
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val signing = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
 namespace = "com.threeddk.tasks"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.threeddk.tasks"
  minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1.0.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  listOf("FIREBASE_API_KEY","FIREBASE_APP_ID","FIREBASE_PROJECT_ID","FIREBASE_SENDER_ID","SUPABASE_URL").forEach {
   buildConfigField("String", it, quoted(configuration(it)))
  }
 }
 signingConfigs { if(signing.isNotEmpty()) create("release") { storeFile=file(signing.getProperty("storeFile")); storePassword=signing.getProperty("storePassword"); keyAlias=signing.getProperty("keyAlias"); keyPassword=signing.getProperty("keyPassword") } }
 buildTypes {
  release {
   isMinifyEnabled = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   if(signing.isNotEmpty()) signingConfig=signingConfigs.getByName("release")
  }
 }
 buildFeatures { viewBinding = true; buildConfig = true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled=true }
 testOptions { unitTests.isIncludeAndroidResources=true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("androidx.constraintlayout:constraintlayout:2.2.0")
 implementation("androidx.recyclerview:recyclerview:1.3.2")
 implementation("androidx.fragment:fragment-ktx:1.8.6")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
 implementation("androidx.navigation:navigation-ui-ktx:2.8.9")
 implementation("androidx.room:room-runtime:2.7.2")
 implementation("androidx.room:room-ktx:2.7.2")
 ksp("androidx.room:room-compiler:2.7.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.squareup.retrofit2:converter-gson:2.11.0")
 implementation("com.google.code.gson:gson:2.11.0")
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-messaging")
 implementation("com.github.bumptech.glide:glide:4.16.0")
 implementation("androidx.security:security-crypto:1.1.0")
 debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 testImplementation("junit:junit:4.13.2")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

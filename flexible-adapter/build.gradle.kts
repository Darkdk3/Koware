// Vendored from arkon/FlexibleAdapter @ c8013533 (the J2K-fork commit this app's
// download UI targets), Apache-2.0. JitPack can no longer rebuild that commit because
// its eu.davidea:grabver plugin depends on nu.studer:java-ordered-properties:1.0.1,
// which was removed from Maven Central, so the base library is checked in here to keep
// builds reproducible on machines without a pre-populated Gradle cache.
plugins {
    alias(mihonx.plugins.android.library)
}

android {
    namespace = "eu.davidea.flexibleadapter"
}

dependencies {
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
}

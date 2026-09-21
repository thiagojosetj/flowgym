// Pure Java business rules (ADR-0004). No Android dependencies allowed here.
plugins {
    `java-library`
    // Lets the app's lint analyze this module against Android API levels (checkDependencies).
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    // Compile against the Java 17 API (the Gradle JDK is 21). Android support is
    // still narrower than Java 17 - see ADR-0004 for the APIs we avoid.
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    testImplementation(libs.junit)
}

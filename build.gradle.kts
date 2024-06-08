plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

group = "net.minestom"
version = rootProject.version
description = "Minestom Felix edition"

configurations.all {
    // We only use Jetbrains Annotations
    exclude("org.checkerframework", "checker-qual")
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

sourceSets.main.get().java.srcDir(file("src/main/java"))

dependencies {
    // Core dependencies
    api(libs.bundles.logging)
    api(libs.jetbrainsAnnotations)
    api(libs.bundles.adventure)
    implementation(libs.minestomData)

    // Performance/data structures
    implementation(libs.caffeine)
    api(libs.fastutil)
    implementation(libs.bundles.flare)
    api(libs.gson)
    implementation(libs.jcTools)

    // Testing
    testImplementation(libs.bundles.junit)
    api(libs.junit.api)
    api(libs.junit.params)
    api(libs.junit.suite.api)
    runtimeOnly(libs.junit.engine)
    runtimeOnly(libs.junit.suite.engine)
}

tasks {
    jar {
        manifest {
            attributes("Automatic-Module-Name" to "net.minestom.server")
        }
    }
    withType<Javadoc> {
        (options as? StandardJavadocDocletOptions)?.apply {
            encoding = "UTF-8"

            // Custom options
            addBooleanOption("html5", true)
            addStringOption("-release", "21")
            // Links to external javadocs
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
            links("https://jd.advntr.dev/api/${libs.versions.adventure.get()}/")
        }
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    withType<Test> {
        useJUnitPlatform()

        // Viewable packets make tracking harder. Could be re-enabled later.
        jvmArgs("-Dminestom.viewable-packet=false")
        jvmArgs("-Dminestom.inside-test=true")
        minHeapSize = "512m"
        maxHeapSize = "1024m"
    }
    withType<Zip> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

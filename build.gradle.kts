import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
}

group = "de.nielstron"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.zxing:core:3.3.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

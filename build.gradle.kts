import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.iitsaii"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.iitsaii.printagent.PrintAgentApplication")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    manifest {
        attributes(
            "Main-Class" to "com.iitsaii.printagent.PrintAgentApplication"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
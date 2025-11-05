val mavenGroup: String by rootProject
val packageVersion: String by rootProject

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version("9.2.2")
    `maven-publish`
}

group = mavenGroup
version = packageVersion

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:33.5.0-jre")

    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    shadow("com.moandjiezana.toml:toml4j:0.7.2") {
        isTransitive = false
    }

    implementation("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(kotlin("test"))
}

repositories {
    mavenCentral()
    mavenLocal()
}

tasks {
    java {
        withSourcesJar()
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveBaseName.set("config")
        archiveClassifier.set("")

        relocate("com.moandjiezana.toml", "su.plo.config.toml")
    }

    build {
        dependsOn(shadowJar)
    }

    java { toolchain.languageVersion.set(JavaLanguageVersion.of("8")) }
}

publishing {
    publications {
        register("config", MavenPublication::class) {
            artifact(tasks.shadowJar.get())
            artifact(tasks["sourcesJar"])
        }
    }

    repositories {
        maven {
            name = "plasmoverseReleases"
            url = uri("https://repo.plasmoverse.com/releases")
            credentials(PasswordCredentials::class)
        }
    }
}

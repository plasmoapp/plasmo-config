import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val mavenGroup: String by rootProject
val packageVersion: String by rootProject

plugins {
    java
    id("com.github.johnrengelman.shadow") version("7.0.0")
    id("net.linguica.maven-settings") version("0.5")
    `maven-publish`
}

group = mavenGroup
version = packageVersion

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:31.0.1-jre")

    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    shadow("com.moandjiezana.toml:toml4j:0.7.2") {
        isTransitive = false
    }

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    // Lombok
    implementation("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
    testImplementation("org.projectlombok:lombok:1.18.20")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
}

repositories {
    mavenCentral()
    mavenLocal()
}

publishing {
    publications {
        register("config", MavenPublication::class) {
            artifact(tasks.shadowJar.get())
        }
    }

    repositories {
        maven {
            name = "plasmo-repo"
            url = uri("https://repo.plo.su/public/")
        }
    }
}

tasks {
    getByName<Test>("test") {
        useJUnitPlatform()
    }

    shadowJar {
        configurations = listOf(project.configurations.getByName("shadow"))
        archiveBaseName.set("config")
        archiveClassifier.set("")

        relocate("com.moandjiezana.toml", "su.plo.config.toml")
    }

    build {
        dependsOn(getByName<ShadowJar>("shadowJar"))
    }

    java { toolchain.languageVersion.set(JavaLanguageVersion.of("8")) }
}

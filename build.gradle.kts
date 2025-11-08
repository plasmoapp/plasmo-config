val mavenGroup: String by rootProject
val packageVersion: String by rootProject

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "9.2.2"
    `maven-publish`
    signing
}

group = mavenGroup
version = packageVersion

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    api("com.google.code.findbugs:jsr305:3.0.2")
    api("com.google.guava:guava:33.5.0-jre")

    compileOnly("com.moandjiezana.toml:toml4j:0.7.2")
    shadow("com.moandjiezana.toml:toml4j:0.7.2") {
        isTransitive = false
    }

    api("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(kotlin("test"))
    testImplementation("com.moandjiezana.toml:toml4j:0.7.2")
}

repositories {
    mavenCentral()
    mavenLocal()
}

shadow {
    addShadowVariantIntoJavaComponent = false
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier.set("")

        relocate("com.moandjiezana.toml", "su.plo.config.toml")
    }

    build {
        dependsOn(shadowJar)
    }

    java { toolchain.languageVersion.set(JavaLanguageVersion.of("8")) }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "config"
            version = project.version.toString()

            artifact(tasks.shadowJar.get())
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("plasmo-config")
                description.set("Plasmo Voice config library")
                url.set("https://github.com/plasmoapp/plasmo-config")

                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("https://opensource.org/license/mit")
                    }
                }

                developers {
                    developer {
                        id.set("plasmo")
                        name.set("Plasmo")
                        url.set("https://github.com/plasmoapp")
                    }
                }

                scm {
                    url.set("https://github.com/plasmoapp/plasmo-config/")
                    connection.set("scm:git:git://github.com/plasmoapp/plasmo-config.git")
                    developerConnection.set("scm:git:ssh://git@github.com:plasmoapp/plasmo-config.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "central"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

            credentials {
                username = project.findProperty("mavenCentralUsername") as String?
                password = project.findProperty("mavenCentralPassword") as String?
            }
        }
    }
}

signing {
    val signingKey = project.findProperty("signingInMemoryKey") as String?
    val signingPassword = project.findProperty("signingInMemoryKeyPassword") as String?

    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword ?: "")

        publishing.publications.forEach { publication ->
            sign(publication)
        }
    } else {
        logger.warn("Signing credentials not found. Publications will not be signed.")
        logger.warn("Configure signing properties:")
        logger.warn("  - signingInMemoryKey (required)")
        logger.warn("  - signingInMemoryKeyPassword (if key has password)")
    }
}

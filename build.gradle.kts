import org.jreleaser.model.Active

plugins {
    id("java-library")
    id("application")
    id("eclipse")
    id("maven-publish")
    id("org.jreleaser") version "1.22.0"
}

val mlGhBase                   = "github.com/maamissiniva/distance-field-font"
val mlArName                   = "maamissiniva-distance-field-font"
val mlDesc                     = "Distance field font generation."
val mlYear                     = "2025"
val groupName                  = "io.github.maamissiniva"
val stagingDir                 = project.layout.buildDirectory.dir("staging-deploy").get()

eclipse.project.name           = "_github_maamissiniva_distance-field-font"
java.toolchain.languageVersion = JavaLanguageVersion.of(8)
tasks.jar {
    archiveBaseName                = mlArName
}
group                          = groupName
version                        = "0.1.1"

application.mainClass = "maamissiniva.distancefieldfont.DistanceFieldFont"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.maamissiniva:maamissiniva-bitmap-font-descriptor:0.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:[2.13,)")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    testImplementation("junit:junit:4.13")
}

//test {
//    testLogging {
//        showStandardStreams = true
//    }
//}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId    = groupName
            artifactId = mlArName

            from(components["java"])

            pom {
                name          = mlArName
                description   = mlDesc
                url           = "https://${mlGhBase}"
                inceptionYear = mlYear
                licenses {
                    license {
                        name = "LGPL-2.1"
                        url  = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html"
                    }
                }
                developers {
                    developer {
                        id    = "maamissiniva"
                        name  = "Maamissiniva"
                        email = "maamissiniva@gmail.com"
                    }
                }
                scm {
                    connection          = "scm:git:https://${mlGhBase}.git"
                    developerConnection = "scm:git:ssh://${mlGhBase}.git"
                    url                 = "https://${mlGhBase}"
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(stagingDir.asFile.path)
        }
    }
}

jreleaser {
    signing {
        pgp {
            active  = Active.ALWAYS
            armored = true
        }
    }
    deploy {
        maven {
            mavenCentral {
                register("release-deploy") {
                    active = Active.RELEASE
                    url    = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository(stagingDir.asFile.path)
                }
            }
            nexus2 {
                register("snapshot-deploy") {
                    active                 = Active.SNAPSHOT
                    snapshotUrl            = "https://central.sonatype.com/repository/maven-snapshots/"
                    applyMavenCentralRules = true
                    snapshotSupported      = true
                    closeRepository        = true
                    releaseRepository      = true
                    stagingRepository(stagingDir.asFile.path)
                }
            }
        }
    }
}


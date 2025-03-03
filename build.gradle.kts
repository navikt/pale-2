import com.diffplug.gradle.spotless.SpotlessTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21

val ktorVersion="3.1.1"
val coroutinesVersion="1.10.1"
val prometheusVersion="0.16.0"
val junitJupiterVersion="5.12.0"
val logbackVersion="1.5.17"
val logstashEncoderVersion="8.0"
val jacksonVersion="2.18.3"
val jaxwsApiVersion="2.3.1"
val javaxAnnotationApiVersion="1.3.2"
val jaxbRuntimeVersion="2.4.0-b180830.0438"
val jaxbApiVersion="2.4.0-b180830.0359"
val javaxActivationVersion="1.1.1"
val commonsTextVersion="1.13.0"
val javaTimeAdapterVersion="1.1.3"
val syfoxmlcodegen="2.0.1"
val jfairyVersion="0.6.5"
val kafkaVersion="3.9.0"
val mockkVersion="1.13.17"
val kotlinVersion="2.1.10"
val googleCloudStorageVersion="2.49.0"
val flywayVersion="11.3.4"
val hikariVersion="6.2.1"
val postgresVersion="42.7.5"
val testcontainersPostgresVersion="1.20.5"
val ktfmtVersion="0.44"
val ibmMqVersion = "9.4.2.0"

///Due to vulnerabilities
val commonsCodecVersion = "1.18.0"
val commonsCompressVersion = "1.27.1"


plugins {
    id("application")
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "8.3.6"
    id("com.diffplug.spotless") version "7.0.2"
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven(url= "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")

    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")

    implementation("no.nav.helse.xml:xmlfellesformat:$syfoxmlcodegen")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoxmlcodegen")
    implementation("no.nav.helse.xml:kith-apprec:$syfoxmlcodegen")
    implementation("no.nav.helse.xml:legeerklaering:$syfoxmlcodegen")
    implementation("no.nav.helse.xml:arenainfo-2:$syfoxmlcodegen")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.devskiller:jfairy:$jfairyVersion") {
        exclude(group = "org.apache.commons", module = "commons-text")
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresVersion")
    constraints {
        testImplementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("overrides vulnerable dependency from org.testcontainers:postgresql")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

tasks {

    withType<ShadowJar>{
     mergeServiceFiles {
        setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
      }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.ApplicationKt",
                ),
            )
        }
    }

    withType<Test>{
        useJUnitPlatform {}
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    withType<Exec> {
        println("⚈ ⚈ ⚈ Running Add Pre Commit Git Hook Script on Build ⚈ ⚈ ⚈")
        commandLine("cp", "./.scripts/pre-commit", "./.git/hooks")
        println("✅ Added Pre Commit Git Hook Script.")

    }

    withType<SpotlessTask>{
        spotless{
            kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
            check {
                dependsOn("spotlessApply")
            }
        }
    }
}

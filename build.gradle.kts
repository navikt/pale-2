import com.diffplug.gradle.spotless.SpotlessTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21

val ktorVersion="3.4.0"
val coroutinesVersion="1.10.2"
val prometheusVersion="0.16.0"
val junitJupiterVersion="6.0.2"
val logbackVersion = "1.5.28"
val logstashEncoderVersion="9.0"
val jacksonVersion="2.21.0"
val jaxwsApiVersion="2.3.1"
val javaxAnnotationApiVersion="1.3.2"
val jaxbRuntimeVersion="2.4.0-b180830.0438"
val jaxbApiVersion="2.4.0-b180830.0359"
val javaxActivationVersion="1.1.1"
val commonsTextVersion="1.15.0"
val javaTimeAdapterVersion="1.1.3"
val syfoxmlcodegen="2.0.1"
val jfairyVersion="0.6.5"
val kafkaVersion="4.1.1"
val mockkVersion="1.14.9"
val kotlinVersion="2.3.10"
val googleCloudStorageVersion = "2.62.1"
val flywayVersion="12.0.0"
val hikariVersion="7.0.2"
val postgresVersion="42.7.9"
val testcontainerVersion = "2.0.3"
val ktfmtVersion="0.44"
val ibmMqVersion = "9.4.5.0"


plugins {
    id("application")
    kotlin("jvm") version "2.3.10"
    id("com.diffplug.spotless") version "8.2.1"
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

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

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
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainerVersion")
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

tasks {

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

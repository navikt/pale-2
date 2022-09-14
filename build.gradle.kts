import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val ktorVersion = "2.1.1"
val coroutinesVersion = "1.6.4"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "5.9.0"
val kluentVersion = "1.68"
val logbackVersion = "1.4.0"
val logstashEncoderVersion = "7.2"
val jacksonVersion = "2.13.4"
val jedisVersion = "4.2.3"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxbApiVersion = "2.4.0-b180830.0359"
val javaxActivationVersion = "1.1.1"
val legeerklaering = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val kithApprecVersion = "2019.07.30-04-23-2a0d1388209441ec05d2e92a821eed4f796a3ae2"
val commonsTextVersion = "1.9"
val javaTimeAdapterVersion = "1.1.3"
val arenaInfoVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val jfairyVersion = "0.6.5"
val pale2CommonVersion = "1.5051993"
val kafkaVersion = "3.2.1"
val mockkVersion = "1.12.7"
val kotlinVersion = "1.7.10"
val googleCloudStorageVersion = "2.11.3"
val jaxbImplVersion = "2.3.3"
val wsApiVersion = "2.3.3"
val annotationApiVersion = "1.3.5"

plugins {
    id("io.mateo.cxf-codegen") version "1.0.0-rc.3"
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.diffplug.spotless") version "6.5.0"
    id("org.jmailen.kotlinter") version "3.10.0"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url= "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/pale-2-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
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
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")

    implementation("no.nav.syfo:pale-2-common-mq:$pale2CommonVersion")
    implementation("no.nav.syfo:pale-2-common-models:$pale2CommonVersion")
    implementation("no.nav.syfo:pale-2-common-kafka:$pale2CommonVersion")
    implementation("no.nav.syfo:pale-2-common-metrics:$pale2CommonVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.helse.xml:kith-apprec:$kithApprecVersion")
    implementation("no.nav.helse.xml:legeerklaering:$legeerklaering")
    implementation("no.nav.helse.xml:arenainfo-2:$arenaInfoVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")
    implementation("redis.clients:jedis:$jedisVersion")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("com.devskiller:jfairy:$jfairyVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {

        kotlinOptions.jvmTarget = "17"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    "check" {
        dependsOn("formatKotlin")
    }
}

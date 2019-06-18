import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0-SNAPSHOT"

val ktorVersion = "1.1.5"
val prometheusVersion = "0.6.0"
val spekVersion = "2.0.2"
val kluentVersion = "1.39"
val smCommonVersion = "1.0.18"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.1"
val jacksonVersion = "2.9.7"
val jedisVersion = "2.9.0"
val sykmeldingVersion = "1.1-SNAPSHOT"
val kithHodemeldingVersion = "1.1"
val fellesformatVersion = "1.0"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxbApiVersion = "2.4.0-b180830.0359"
val javaxActivationVersion = "1.1.1"
val jaxwsToolsVersion = "2.3.1"
val legeerklaering = "1.0-SNAPSHOT"
val kithApprecVersion = "1.1"

plugins {
    kotlin("jvm") version "1.3.31"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    id("org.jmailen.kotlinter") version "1.25.1"
}

buildscript {
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
        classpath("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
        classpath("com.sun.activation:javax.activation:1.2.0")
        classpath("com.sun.xml.ws:jaxws-tools:2.3.1") {
            exclude(group = "com.sun.xml.ws", module = "policy")
        }
    }
}


repositories {
    mavenCentral()
    jcenter()
    maven(url= "https://dl.bintray.com/kotlin/ktor")
    maven(url= "https://dl.bintray.com/spekframework/spek-dev")
    maven(url= "https://repo.adeo.no/repository/maven-snapshots/")
    maven(url= "https://repo.adeo.no/repository/maven-releases/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

    implementation("no.nav.syfo.sm:syfosm-common-mq:$smCommonVersion")
    implementation("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation("no.nav.syfo.tjenester:fellesformat:$fellesformatVersion")
    implementation("no.nav.syfo.tjenester:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.syfo.tjenester:kith-apprec:$kithApprecVersion")
    implementation("no.nav.helse.xml:legeerklaering:$legeerklaering")

    implementation("redis.clients:jedis:$jedisVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
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
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
    }

}
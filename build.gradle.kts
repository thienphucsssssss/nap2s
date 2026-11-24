import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    kotlin("jvm") version "2.3.0"
}

allprojects {
    group = "vn.twosmc"
    version = "2.0.0"

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnly(kotlin("stdlib"))
    }

    java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
    }
    kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_1_8
}

// Root project khong tao "jar rong" nua.
// Thay vao do, build/assemble se copy shadowJar that cua module plugin
// ra build/libs/nap2s-<version>.jar de tranh copy nham artifact.
tasks.named<Jar>("jar") {
    enabled = false
}

val pluginProject = project(":nap2s-plugin")

val releaseJar by tasks.registering(Copy::class) {
    dependsOn(pluginProject.tasks.named("shadowJar"))
    from(pluginProject.layout.buildDirectory.file("libs/Nap2S.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "nap2s-${project.version}.jar" }
}

tasks.named("assemble") {
    dependsOn(releaseJar)
}

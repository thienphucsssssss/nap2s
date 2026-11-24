plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases")
    maven("https://repo.rosewooddev.io/repository/public/") {
        content { includeModule("org.black_ixx", "playerpoints") }
    }
    maven("https://repo.codemc.org/repository/maven-snapshots/") // AnvilGUI snapshots
}

dependencies {
    // Kotlin stdlib — root project khai compileOnly, plugin cần shade vào jar
    implementation(kotlin("stdlib"))

    // spigot
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // libs
    compileOnly("org.black_ixx:playerpoints:3.2.6")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // Embedded HTTP server cho webhook SePay (nhẹ ~50KB)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("com.zaxxer:HikariCP:5.1.0")

    implementation("com.h2database:h2:2.2.224")         // H2 embedded database
    implementation("com.mysql:mysql-connector-j:8.3.0")  // MySQL/MariaDB connector

    // AnvilGUI — UI input cho Java player, shade + relocate vào jar
    implementation("net.wesjd:anvilgui:1.10.10-SNAPSHOT")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.4.0")
    testImplementation("io.mockk:mockk:1.13.7")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

tasks {
    test {
        useJUnitPlatform()
    }

    val jarName = "Nap2S"

    register("customCopy") {
        dependsOn(shadowJar)

        val path = project.properties["shadowPath"]
        if (path != null) {
            doLast {
                println(path)
                copy {
                    from("build/libs/$jarName.jar")
                    into(path)
                }
                println("Copied")
            }
        }
    }

    processResources {
        from(rootProject.file("NOTICE.md")) {
            into("META-INF")
            rename { "NOTICE-NAP2S.md" }
        }
        outputs.upToDateWhen { false }
        filesMatching(listOf("**/plugin.yml")) {
            expand(mapOf("version" to project.version.toString()))
            println("$name: set version to ${project.version}")
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        filteringCharset = Charsets.UTF_8.name()
    }

    shadowJar {
        relocate("org.bstats", "vn.twosmc.nap2s.libs.bstats")
        relocate("fi.iki.elonen", "vn.twosmc.nap2s.libs.nanohttpd")
        relocate("com.zaxxer.hikari", "vn.twosmc.nap2s.libs.hikari")
        // AnvilGUI — relocate để tránh conflict với plugin khác dùng AnvilGUI version khác
        relocate("net.wesjd.anvilgui", "vn.twosmc.nap2s.libs.anvilgui")
        archiveFileName.set("$jarName.jar")
    }

    assemble {
        dependsOn(shadowJar, get("customCopy"))
    }
}

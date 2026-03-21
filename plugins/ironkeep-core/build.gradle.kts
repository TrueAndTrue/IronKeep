plugins {
    java
}

group = "com.ironkeep"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks {
    jar {
        archiveBaseName.set("ironkeep-core")
    }

    register<Copy>("deployPlugin") {
        dependsOn(jar)
        from(jar.get().archiveFile)
        into("../../server/plugins")
    }

    build {
        dependsOn("deployPlugin")
    }
}

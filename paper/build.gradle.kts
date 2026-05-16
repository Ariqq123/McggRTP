plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.0.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("McggRTP-paper-thin.jar")
}

tasks.shadowJar {
    archiveFileName.set("McggRTP-paper.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

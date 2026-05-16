import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    testCompileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    testRuntimeOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.yaml:snakeyaml:2.4")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
}

tasks.jar {
    archiveFileName.set("McggRTP-velocity-thin.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    archiveFileName.set("McggRTP-velocity.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

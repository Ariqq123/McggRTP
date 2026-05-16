plugins {
    java
}

allprojects {
    group = "me.mcgg.azreyzaako"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

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
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

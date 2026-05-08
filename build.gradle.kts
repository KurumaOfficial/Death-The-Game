// =============================================================================
// Death — root build.gradle.kts
//
// Конфигурация, общая для всех Java модулей проекта.
// Платформенные модули (:android, :ios) переопределяют необходимое
// в собственных build.gradle.kts — их Java sourceSet применяется
// здесь же, потому что :android применяет AGP, а :ios имеет свой scope.
// =============================================================================

plugins {
    base
}

val deathVersion: String by project
val deathGroup:   String by project

allprojects {
    group   = deathGroup
    version = deathVersion
}

// -----------------------------------------------------------------------------
// Чистая Java-конфигурация для модулей, которые НЕ являются Android-модулями.
// Android-модуль настраивается отдельно в :android (AGP).
// -----------------------------------------------------------------------------
val nonAndroidModules = setOf("core", "desktop", "rust-bridge", "ios")

subprojects {
    if (name in nonAndroidModules) {
        apply(plugin = "java-library")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
            options.compilerArgs.addAll(
                listOf(
                    "-Xlint:all,-serial,-processing",
                    "-Werror",
                )
            )
        }

        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = false
            }
        }
    }
}

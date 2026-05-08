// =============================================================================
// :rust-bridge — Java-сторона JNI bridge'а к Rust ядру.
//
// Что здесь:
//   - WeTTeA.native_bridge.rust.RustNativeLibrary — реализация
//     WeTTeA.api.nativebridge.NativeLibraryLoader: extract artifact
//     `native/<os>/<arch>/<libname>` из classpath во временный файл и
//     System.load.
//   - WeTTeA.native_bridge.rust.RustCore — Java-фасад над JNI-экспортами
//     Rust crate'а death-native.
//
// Cargo build хука:
//   - Task `cargoBuild` запускает `cargo build --release` в rust-core/.
//   - Task `copyNativeArtifact` копирует libdeath_native.{so,dll,dylib} в
//     build/resources/main/native/<os>/<arch>/.
//   - `processResources` зависит от `copyNativeArtifact`, поэтому run-jar
//     `:rust-bridge` (и любой transitive — :desktop) автоматически имеет
//     нативку на classpath.
//
// Свойство -PskipCargo=true пропускает оба таска (для CI без Rust toolchain
// или повторной сборки только Java-стороны).
// =============================================================================

import org.gradle.internal.os.OperatingSystem

dependencies {
    api(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// -----------------------------------------------------------------------------
// Cargo build hook
// -----------------------------------------------------------------------------

val rustCoreDir       = rootProject.layout.projectDirectory.dir("rust-core")
val rustTargetDir     = rustCoreDir.dir("target/release")
val nativeResourceDir = layout.buildDirectory.dir("resources/main/native")
val skipCargo         = (project.findProperty("skipCargo") as String?)?.toBoolean() ?: false

/**
 * Возвращает имя файла нативного артефакта для текущей host OS.
 * Linux/Android: libdeath_native.so, Windows: death_native.dll, macOS: libdeath_native.dylib.
 */
fun hostNativeFileName(): String {
    val os = OperatingSystem.current()
    return when {
        os.isWindows -> "death_native.dll"
        os.isMacOsX  -> "libdeath_native.dylib"
        else         -> "libdeath_native.so"
    }
}

/**
 * Возвращает пару (osDir, archDir) под путь `native/<os>/<arch>/`.
 * Соответствует формату, который читает `RustNativeLibrary` в runtime.
 */
fun hostNativeRelativeDir(): String {
    val os = OperatingSystem.current()
    val osTag = when {
        os.isWindows -> "windows"
        os.isMacOsX  -> "macos"
        else         -> "linux"
    }
    val arch = System.getProperty("os.arch").lowercase()
    val archTag = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
        arch.contains("64")                                -> "x86_64"
        else                                               -> arch
    }
    return "$osTag/$archTag"
}

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group       = "native"
    description = "Собирает rust-core (cdylib release) для host-тоlchain'а."
    workingDir = rustCoreDir.asFile
    commandLine("cargo", "build", "--release")
    onlyIf { !skipCargo }
    inputs.dir(rustCoreDir.dir("src"))
    inputs.file(rustCoreDir.file("Cargo.toml"))
    outputs.file(rustTargetDir.file(hostNativeFileName()))
}

val copyNativeArtifact = tasks.register<Copy>("copyNativeArtifact") {
    group       = "native"
    description = "Копирует libdeath_native в resources/main/native/<os>/<arch>/."
    dependsOn(cargoBuild)
    onlyIf { !skipCargo }
    from(rustTargetDir) {
        include(hostNativeFileName())
    }
    into(nativeResourceDir.map { it.dir(hostNativeRelativeDir()) })
}

tasks.named("processResources") {
    dependsOn(copyNativeArtifact)
}

// jar/run должны видеть нативку как обычный classpath ресурс.
sourceSets.named("main") {
    output.dir(mapOf("builtBy" to copyNativeArtifact), nativeResourceDir)
}

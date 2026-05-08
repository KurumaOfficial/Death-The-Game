# :ios (stage 1 placeholder)

iOS платформенный модуль Death.

**Текущий статус — INTEGRATION_MISSING.** Модуль присутствует физически, но
исключён из основной сборки до тех пор, пока:
- Не подключён RoboVM/MobiVM Gradle plugin;
- Не настроен macOS + Xcode toolchain;
- Не реализован `DeathIosLauncher` (см. `IosIntegrationPlaceholder`);
- Не собран `libdeath_native.a` (staticlib) для arm64;
- Не настроен `robovm.xml` + `Info.plist.xml`.

Все шаги — в `PROGRESS.md` (строка `:ios`).

## Включение

```bash
./gradlew -PenableIOS=true :ios:robovmIPad   # требует macOS + Xcode + RoboVM
```

При выключенном модуле основная сборка (`:core`, `:desktop`, `:rust-bridge`)
собирается без него.

# :android (stage 1 placeholder)

Android платформенный модуль Death.

**Текущий статус — INTEGRATION_MISSING.** Модуль присутствует физически, но
исключён из основной сборки до тех пор, пока:
- Не подключён `com.android.application` Gradle plugin;
- Не настроен Android SDK (`ANDROID_HOME`);
- Не реализован `DeathActivity` (см. `AndroidIntegrationPlaceholder`);
- Не настроен `cargo-ndk` для сборки `libdeath_native.so`.

Все шаги — в `PROGRESS.md` (строка `:android`).

## Включение

```bash
ANDROID_HOME=/path/to/sdk ./gradlew :android:assembleDebug
# или
./gradlew -PenableAndroid=true :android:assembleDebug
```

При выключенном модуле `:core:compileJava` и `:desktop:compileJava` собираются
без него.

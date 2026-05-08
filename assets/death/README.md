# assets/death

Корень runtime ассетов Death.

## Загрузка

Десктоп: classpath ресурсы. Файлы из `assets/death/` копируются в jar и
доступны через `WeTTeA.api.platform.PlatformFileSystem.openAsset(relativePath)`,
который ищет по префиксу `/assets/death/<relativePath>`.

Чтобы стандартный Gradle `processResources` положил файлы в classpath,
дерево `assets/death/` подключается как resource directory модуля `:core`
(см. `core/build.gradle.kts` секция `sourceSets["main"].resources.srcDir(...)`).

## Структура

| Папка        | Что хранится                                 | Формат               |
|--------------|----------------------------------------------|----------------------|
| `textures/`  | Цветные текстуры, normal maps, atlases       | `.png`, `.ktx2`      |
| `models/`    | 3D модели (mesh + skeleton + animations)     | `.glb`, `.gltf`      |
| `audio/sfx/` | Короткие звуковые эффекты                    | `.ogg`, `.wav`       |
| `audio/music/` | Музыкальные треки, амбиент                 | `.ogg`               |
| `fonts/`     | Шрифты UI / диалогов                          | `.ttf`, `.otf`       |
| `shaders/`   | Скомпилированные SPIR-V шейдеры              | `.spv`               |
| `ui/`        | Atlases UI, layout JSON                      | `.png`, `.json`      |
| `world/`     | Карты, сцены, сетки боя                      | `.json`              |
| `dialogues/` | Диалоговые ветки (Yarn / Ink / собственный)  | `.json`, `.yarn`     |
| `locales/`   | Локализация (i18n)                            | `.json`              |

## Stage 1 (текущее состояние)

Все папки созданы, но **пусты** (только README). На стадии 1 ни один ассет
не подгружается — INTEGRATION_MISSING. См. `PROGRESS.md`.

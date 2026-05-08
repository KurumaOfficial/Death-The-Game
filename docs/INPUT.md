# Input Death

> Stage 1.

## Контракт

Слой ввода описывается интерфейсами в `WeTTeA.api.input`:
- `InputDevice` — клавиатура / геймпад / тач (определено как enum + state holder);
- `InputState` — снимок состояния на текущий тик (нажатые кнопки, оси,
  координаты курсора);
- `InputEvent` — единичное событие (`KeyDown`, `KeyUp`, `PointerMove`, `PointerPress`).

## Адаптеры

| Платформа | Источник                            | Адаптер (stage 2)                 |
|-----------|-------------------------------------|-----------------------------------|
| desktop   | GLFW callbacks                      | `WeTTeA.platform.desktop.input.GlfwInputAdapter` |
| android   | Android `MotionEvent` / `KeyEvent`  | `WeTTeA.platform.android.input.AndroidInputAdapter` |
| iOS       | UIKit `touchesBegan/Moved/Ended`    | `WeTTeA.platform.ios.input.IosInputAdapter` |

Адаптер в каждом тике пушит события в `EventBus`
(`KeyDown`/`PointerPress`/...) и обновляет `InputState`, который доступен
через `ServiceContainer.require(InputState.class)`.

## Stage 1

INTEGRATION_MISSING. Ни один адаптер не реализован. GLFW окно открывается, но
callbacks не подвязаны. См. `PROGRESS.md` строка "input adapters".

## Маппинг действий

Stage 2 план:
- `WeTTeA.gameplay.input.ActionMap` — связывает физические клавиши/кнопки
  с логическими действиями (`MOVE_FORWARD`, `ATTACK`, `INTERACT`).
- Хранится как JSON в `assets/death/ui/keybindings.json`.
- Перебинды через `ActionMapEditor`.

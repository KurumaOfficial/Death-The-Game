/**
 * Контракты, интерфейсы, DTO и события — независимая от реализации
 * api-поверхность всего проекта Death.
 *
 * <p>Под-пакеты:
 * <ul>
 *   <li>{@code lifecycle} — жизненный цикл игровых объектов (tick/draw/dispose);</li>
 *   <li>{@code input} — абстракция input action'ов и платформенных источников;</li>
 *   <li>{@code render} — render backend, render passes, viewport, качество;</li>
 *   <li>{@code audio} — audio backend и параметры воспроизведения;</li>
 *   <li>{@code content} — загрузка и каталог игрового контента;</li>
 *   <li>{@code save} — сохранения и persistent state;</li>
 *   <li>{@code platform} — платформенный adapter и app lifecycle;</li>
 *   <li>{@code nativebridge} — контракт к нативным библиотекам (Rust ядро);</li>
 *   <li>{@code events} — глобальный event bus.</li>
 * </ul>
 *
 * <p>Реализации находятся в:
 * <ul>
 *   <li>{@code WeTTeA.core.*} — кросс-платформенные;</li>
 *   <li>{@code WeTTeA.render.*} — render impl;</li>
 *   <li>{@code WeTTeA.platform.*} и платформенных модулях ({@code :desktop}, {@code :android}, {@code :ios}) — платформенные;</li>
 *   <li>{@code WeTTeA.native_bridge.*} (модуль {@code :rust-bridge}) — JNI.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api;

/**
 * Контракты системы сохранений.
 *
 * <p>Слотовая модель: {@link WeTTeA.api.save.SaveSlot} → {@link WeTTeA.api.save.PersistentState}.
 * Хранилище абстрагировано через {@link WeTTeA.api.save.SaveStorage} — на desktop
 * это файл в user data dir, на Android — internal storage, на iOS — Documents/.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.save;

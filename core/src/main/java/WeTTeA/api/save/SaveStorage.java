package WeTTeA.api.save;

import WeTTeA.api.lifecycle.Disposable;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище сохранений — абстракция над файловой системой платформы.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface SaveStorage extends Disposable {

    /** Загрузить состояние из слота, если оно есть. */
    Optional<PersistentState> load(SaveSlot slot);

    /** Записать состояние в слот, перезаписывая. */
    void save(SaveSlot slot, PersistentState state);

    /** Удалить слот. No-op, если слот пустой. */
    void delete(SaveSlot slot);

    /** Перечислить все непустые слоты. */
    List<SaveSlot> listSlots();
}

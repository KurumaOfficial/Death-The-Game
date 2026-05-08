package WeTTeA.api.save;

/**
 * Идентификатор слота сохранения.
 *
 * <p>Слот стабилен в пределах профиля игрока. Авто-сохранения и quick-save'ы
 * имеют выделенные значения {@link #AUTO} и {@link #QUICK}, остальные — обычные.
 *
 * @param index числовой индекс слота. {@code -1} для AUTO, {@code -2} для QUICK,
 *              {@code 0..N} для обычных.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record SaveSlot(int index) {

    public static final SaveSlot AUTO = new SaveSlot(-1);
    public static final SaveSlot QUICK = new SaveSlot(-2);

    public SaveSlot {
        if (index < -2) {
            throw new IllegalArgumentException("invalid save slot index: " + index);
        }
    }

    public boolean isAuto() {
        return index == -1;
    }

    public boolean isQuick() {
        return index == -2;
    }
}

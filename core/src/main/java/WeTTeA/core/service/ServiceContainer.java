package WeTTeA.core.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service Locator core — хранит сервисы по типу.
 *
 * <p>Регистрация — на этапе boot; lookup — из gameplay/UI слоёв.
 * Не потокобезопасен — все операции из main thread на этапе boot;
 * lookup из game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class ServiceContainer {

    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> void register(Class<T> type, T impl) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(impl, "impl");
        if (services.containsKey(type)) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
        services.put(type, impl);
    }

    @SuppressWarnings("unchecked")
    public <T> T require(Class<T> type) {
        Object svc = services.get(type);
        if (svc == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return (T) svc;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }
}

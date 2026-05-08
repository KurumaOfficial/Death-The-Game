/**
 * Service Locator core — единая точка получения сервисов
 * (RenderBackend, AudioBackend, SaveStorage, EventBus, RustCore).
 *
 * <p>Регистрация — на этапе boot ({@code WeTTeA.core.CoreBootstrap});
 * lookup — из gameplay/UI слоёв через {@link WeTTeA.core.service.ServiceContainer}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.core.service;

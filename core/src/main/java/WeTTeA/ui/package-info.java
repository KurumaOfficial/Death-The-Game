/**
 * UI слой — меню, HUD, диалоги, инвентарь, инспектор.
 *
 * <p>Слушает {@link WeTTeA.api.events.EventBus} и читает state через
 * {@code WeTTeA.core.service.ServiceContainer}. Render идёт через
 * {@link WeTTeA.api.render.RenderBackend}.
 *
 * <p>На этапе 1 — пакет помечен в PROGRESS.md как INTEGRATION_MISSING.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.ui;

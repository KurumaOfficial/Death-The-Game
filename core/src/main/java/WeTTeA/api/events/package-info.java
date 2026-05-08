/**
 * Событийная шина core (in-process pub/sub).
 *
 * <p>{@link WeTTeA.api.events.EventBus} — глобальная шина, используется для:
 * <ul>
 *   <li>уведомлений между сценами;</li>
 *   <li>интеграции UI и gameplay (UI слушает events боя, диалога);</li>
 *   <li>narrative триггеров.</li>
 * </ul>
 *
 * <p>Запрещено:
 * <ul>
 *   <li>использовать events как замену прямым вызовам внутри одного слоя;</li>
 *   <li>гонять покадровые render-тики через шину;</li>
 *   <li>ловить событие в render thread без явной синхронизации.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.events;

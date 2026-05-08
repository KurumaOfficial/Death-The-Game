/**
 * Контракты системы загрузки контента (assets).
 *
 * <p>Назначение:
 * <ul>
 *   <li>{@link WeTTeA.api.content.AssetCategory} — типы asset'ов;</li>
 *   <li>{@link WeTTeA.api.content.AssetHandle} — дескриптор asset'а;</li>
 *   <li>{@link WeTTeA.api.content.AssetCatalog} — каталог (id → файл);</li>
 *   <li>{@link WeTTeA.api.content.ContentLoader} — загрузчик асинхронный.</li>
 * </ul>
 *
 * <p>Все assets живут в {@code assets/death/}, доступ к ним через
 * {@link WeTTeA.api.platform.PlatformFileSystem}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.content;

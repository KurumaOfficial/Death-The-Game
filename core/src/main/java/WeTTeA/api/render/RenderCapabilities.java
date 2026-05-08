package WeTTeA.api.render;

/**
 * Возможности конкретного устройства/драйвера рендера.
 *
 * <p>Заполняется backend'ом сразу после {@code init()}. Core читает эту
 * структуру при выборе пресета качества по умолчанию и при настройке
 * gameplay-сценариев (например, разрешение масс-инстансинга снарядов
 * до {@link #maxInstancesPerDraw()} в одном draw call'е).
 *
 * @param api                       какое API инициализировано
 * @param deviceName                читаемое имя GPU
 * @param driverVersion             строка версии драйвера, для логов
 * @param supportsCompute           true, если есть compute pipeline
 * @param supportsMultiDrawIndirect true, если поддерживается multi-draw indirect
 * @param maxInstancesPerDraw       лимит инстансов в одном draw call
 * @param maxTextureSize            максимальный размер текстуры (по стороне)
 * @param maxMsaaSamples            максимум MSAA сэмплов
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record RenderCapabilities(
        RenderApi api,
        String deviceName,
        String driverVersion,
        boolean supportsCompute,
        boolean supportsMultiDrawIndirect,
        int maxInstancesPerDraw,
        int maxTextureSize,
        int maxMsaaSamples
) {
    public RenderCapabilities {
        if (maxInstancesPerDraw <= 0) {
            throw new IllegalArgumentException("maxInstancesPerDraw must be positive: " + maxInstancesPerDraw);
        }
        if (maxTextureSize <= 0) {
            throw new IllegalArgumentException("maxTextureSize must be positive: " + maxTextureSize);
        }
        if (maxMsaaSamples <= 0) {
            throw new IllegalArgumentException("maxMsaaSamples must be positive: " + maxMsaaSamples);
        }
    }
}

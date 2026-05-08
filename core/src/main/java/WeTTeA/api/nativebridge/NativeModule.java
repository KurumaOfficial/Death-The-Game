package WeTTeA.api.nativebridge;

/**
 * Категории модулей нативного Rust ядра.
 *
 * <p>Каждый модуль соответствует подпакету в {@code rust-core/src/modules/}
 * и имеет свой набор JNI экспортов в {@code :rust-bridge} модуле.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum NativeModule {

    /** AI поведение крупных боссов и mob'ов. */
    AI,

    /** Физика — collision detection, raycasts, broadphase. */
    PHYSICS,

    /** Bullet hell — обновление десятков тысяч снарядов / частиц. */
    BULLET,

    /** Large-scale combat — отряды, формации, AI масс. */
    LARGE_SCALE,

    /** Spatial — BVH, octree, грид-индексы для запросов. */
    SPATIAL
}

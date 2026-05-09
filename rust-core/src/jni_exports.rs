//! JNI-экспорты `death-native`.
//!
//! Каждый `extern "system" fn` имеет имя по правилу JNI symbol mangling:
//!     `Java_<package>_<class>_<method>`
//! где в пакете каждое `_` заменяется на `_1`.
//!
//! Java side: `WeTTeA.native_bridge.rust.RustCore`
//! Package path → `WeTTeA/native_bridge/rust` (slash) → mangled `WeTTeA_native_1bridge_rust`.
//!
//! Stage 2.4: `nativeInit` / `nativeShutdown` логируют свой вызов
//! (через `eprintln!` в stderr с префиксом `[Death:rust]`) — это нужно как
//! smoke-доказательство того, что Java реально дошла до native кода после
//! `System.load`. Полезной нагрузки пока нет — фактическая инициализация
//! подсистем (physics, ecs, log routing → Java, panic hook → throwable)
//! приедет на стадии 2.5+.

use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray, JIntArray};
use jni::sys::{jboolean, jdouble, jint, jintArray, jlong, JNI_FALSE, JNI_TRUE};

use crate::pathfinding::Grid;
use crate::physics::{pack_body_handle, unpack_body_handle, PhysicsWorld};

/// Java: `private static native void nativeInit();`
///
/// Stage 2.4: пишет `[Death:rust] native init` в stderr и возвращается.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustCore_nativeInit(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    eprintln!("[Death:rust] native init (no-op stage 2.4)");
}

/// Java: `private static native void nativeShutdown();`
///
/// Stage 2.4: пишет `[Death:rust] native shutdown` в stderr и возвращается.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustCore_nativeShutdown(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    eprintln!("[Death:rust] native shutdown (no-op stage 2.4)");
}

// =============================================================================
// Stage 2.5: PhysicsWorld JNI экспорты
//
// Java side: WeTTeA.native_bridge.rust.RustPhysicsWorld
// Package    → WeTTeA/native_bridge/rust → mangled WeTTeA_native_1bridge_rust
//
// Хэндлы:
//   - PhysicsWorld* упаковывается в jlong (raw Box pointer).
//     Управление временем жизни — на Java стороне (close() → nativeDestroy).
//   - RigidBodyHandle (idx,gen) упаковывается в jlong через
//     physics::pack_body_handle / unpack_body_handle.
// =============================================================================

/// Java: `private static native long nativeCreate();`
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeCreate(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jlong {
    let world = Box::new(PhysicsWorld::new());
    Box::into_raw(world) as jlong
}

/// Java: `private static native void nativeDestroy(long handle);`
///
/// Безопасно вызывается с {@code 0} — это no-op. Иначе — `Box::from_raw` и drop.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeDestroy(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        // SAFETY: handle получен из nativeCreate выше; Java вызывает destroy
        // ровно один раз (контракт RustPhysicsWorld.close()). После drop'а
        // Java выставляет handle=0 чтобы предотвратить double-free.
        drop(Box::from_raw(handle as *mut PhysicsWorld));
    }
}

/// Java: `private static native long nativeAddDynamicBody(long handle, double x, double y, double z);`
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeAddDynamicBody(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jdouble,
    y: jdouble,
    z: jdouble,
) -> jlong {
    let world = unsafe { &mut *(handle as *mut PhysicsWorld) };
    let body = world.add_dynamic_body(x as f32, y as f32, z as f32);
    pack_body_handle(body)
}

/// Java: `private static native void nativeStep(long handle, double dt);`
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeStep(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    dt: jdouble,
) {
    let world = unsafe { &mut *(handle as *mut PhysicsWorld) };
    world.step(dt as f32);
}

/// Java: `private static native void nativeBodyPosition(long handle, long body, double[] out);`
///
/// Записывает в `out[0..3]` мировую позицию тела или `[NaN,NaN,NaN]` если
/// handle не валиден. Длина массива на Java стороне должна быть >= 3.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeBodyPosition<'a>(
    env: JNIEnv<'a>,
    _class: JClass<'a>,
    handle: jlong,
    body: jlong,
    out: JDoubleArray<'a>,
) {
    let world = unsafe { &*(handle as *const PhysicsWorld) };
    let pos = world.body_position(unpack_body_handle(body));
    let buf = [pos[0] as f64, pos[1] as f64, pos[2] as f64];
    if let Err(e) = env.set_double_array_region(&out, 0, &buf) {
        eprintln!("[Death:rust] nativeBodyPosition: failed to write out array: {e:?}");
    }
}

// =============================================================================
// Stage 3.3a: PathGrid (тактическая сетка + A*) JNI экспорты
//
// Java side: WeTTeA.native_bridge.rust.RustPathGrid
// Package    → WeTTeA/native_bridge/rust → mangled WeTTeA_native_1bridge_rust
//
// Хэндлы:
//   - Grid* упаковывается в jlong (raw Box pointer).
//     Управление временем жизни — на Java стороне (close() → nativeDestroy).
//
// Координаты (sx, sy, gx, gy, x, y, width, height) приходят как jint, но
// Java-сторона гарантирует ширину/высоту > 0 (через ensureGrid в конструкторе)
// и ограничивает координаты unsigned диапазоном через ensureBounds. Этот
// слой кастит i32 → u32 без проверки знака; отрицательные значения от
// Java означают баг выше по стеку и должны вылетать через ensureBounds.
// =============================================================================

/// Java: `private static native long nativeCreate(int width, int height);`
///
/// Возвращает raw pointer на `Grid`, упакованный в `jlong`. Java-сторона
/// валидирует width/height > 0 ДО вызова nativeCreate (см. RustPathGrid
/// конструктор), поэтому здесь мы делаем `assert!` ради защиты от багов.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeCreate(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    width: jint,
    height: jint,
) -> jlong {
    assert!(
        width > 0 && height > 0,
        "RustPathGrid.nativeCreate: width/height must be > 0, got {}x{}",
        width,
        height
    );
    let grid = Box::new(Grid::new(width as u32, height as u32));
    Box::into_raw(grid) as jlong
}

/// Java: `private static native void nativeDestroy(long handle);`
///
/// Безопасно вызывается с `0` — это no-op. Иначе — `Box::from_raw` и drop.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeDestroy(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        // SAFETY: handle получен из nativeCreate выше; Java вызывает destroy
        // ровно один раз (контракт RustPathGrid.close()). После drop'а Java
        // выставляет handle=0 чтобы предотвратить double-free.
        drop(Box::from_raw(handle as *mut Grid));
    }
}

/// Java: `private static native void nativeSetBlocked(long handle, int x, int y, boolean blocked);`
///
/// Java валидирует bounds через `ensureBounds(x, y)` ДО вызова. Этот слой —
/// silent no-op на out-of-bounds (дополнительная защита от багов).
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeSetBlocked(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jint,
    y: jint,
    blocked: jboolean,
) {
    let grid = unsafe { &mut *(handle as *mut Grid) };
    if x < 0 || y < 0 {
        return;
    }
    grid.set_blocked(x as u32, y as u32, blocked != JNI_FALSE);
}

/// Java: `private static native boolean nativeIsBlocked(long handle, int x, int y);`
///
/// Out-of-bounds трактуется как `true` (заблокировано) — защищает caller'ов
/// от случайного "проваливания" на отрицательные/огромные координаты.
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeIsBlocked(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jint,
    y: jint,
) -> jboolean {
    let grid = unsafe { &*(handle as *const Grid) };
    if x < 0 || y < 0 {
        return JNI_TRUE;
    }
    if grid.is_blocked(x as u32, y as u32) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

/// Java: `private static native int[] nativeFindPath(long handle, int sx, int sy, int gx, int gy);`
///
/// Возвращает flat `int[]` длины `2 * pathLength` (x0, y0, x1, y1, ...) —
/// клетки пути от start к goal включительно. Пустой массив = пути нет
/// (или start/goal вне границ / заблокированы).
///
/// Конверсия `Vec<(u32, u32)>` → `jintArray`:
/// - аллоцируем `jintArray` через `env.new_int_array(2 * len)`;
/// - заполняем `Vec<jint>` плоской раскладкой (`x0, y0, x1, y1, ...`);
/// - копируем через `env.set_int_array_region`;
/// - возвращаем `JIntArray::into_raw()` (raw jintArray, GC-владение Java).
#[no_mangle]
pub extern "system" fn Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeFindPath<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    handle: jlong,
    sx: jint,
    sy: jint,
    gx: jint,
    gy: jint,
) -> jintArray {
    // Out-of-bounds от Java стороны: сразу пустой массив.
    if sx < 0 || sy < 0 || gx < 0 || gy < 0 {
        return empty_int_array(&mut env);
    }
    let grid = unsafe { &*(handle as *const Grid) };
    let path = grid.find_path(sx as u32, sy as u32, gx as u32, gy as u32);
    if path.is_empty() {
        return empty_int_array(&mut env);
    }

    let len = path.len();
    let total = (len * 2) as jint;
    let arr = match env.new_int_array(total) {
        Ok(a) => a,
        Err(e) => {
            eprintln!("[Death:rust] nativeFindPath: new_int_array failed: {e:?}");
            return std::ptr::null_mut();
        }
    };

    let mut buf: Vec<jint> = Vec::with_capacity(len * 2);
    for &(x, y) in &path {
        buf.push(x as jint);
        buf.push(y as jint);
    }
    if let Err(e) = env.set_int_array_region(&arr, 0, &buf) {
        eprintln!("[Death:rust] nativeFindPath: set_int_array_region failed: {e:?}");
        return std::ptr::null_mut();
    }
    // SAFETY: JIntArray<'a> — это просто newtype над jintArray; into_raw
    // возвращает голый GC-handle, владение которым переходит обратно к JVM.
    arr.into_raw()
}

/// Хелпер: создаёт пустой `int[]` для возврата из nativeFindPath на
/// "пути нет" / out-of-bounds кейсах.
fn empty_int_array(env: &mut JNIEnv<'_>) -> jintArray {
    match env.new_int_array(0) {
        Ok(a) => {
            let raw: JIntArray<'_> = a;
            raw.into_raw()
        }
        Err(e) => {
            eprintln!("[Death:rust] empty_int_array: new_int_array(0) failed: {e:?}");
            std::ptr::null_mut()
        }
    }
}

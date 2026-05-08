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
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jdouble, jlong};

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

//! Минимальная физическая симуляция Death (stage 2.5).
//!
//! Backend: [Rapier3D](https://crates.io/crates/rapier3d) — стандарт де-факто
//! для Rust физики. Тут поднята минимальная сцена:
//! - `RigidBodySet` + `ColliderSet` (без коллайдеров пока — только гравитация);
//! - `IntegrationParameters::dt` управляется снаружи через [`PhysicsWorld::step`];
//! - `gravity = (0, -9.81, 0)`.
//!
//! Stage 2.5 умышленно не подключает коллайдеры/joint'ы/CCD: smoke-цель —
//! доказать что Java↔Rust handle round-trip работает и интегратор реально
//! сдвигает тело. Полные системы (collision, contacts, characters,
//! pathfinding hooks) приедут в stage 2.6+.
//!
//! ## Хэндлы
//!
//! - `*mut PhysicsWorld` упаковывается в `jlong` (raw pointer). Это
//!   ответственность Java стороны вернуть тот же long в `nativeDestroy`,
//!   иначе — утечка.
//! - `RigidBodyHandle` имеет `(idx: u32, generation: u32)` —
//!   запаковывается в `jlong` функциями [`pack_body_handle`] /
//!   [`unpack_body_handle`].

use rapier3d::prelude::*;

/// Контейнер физического мира stage 2.5.
///
/// Не `Send` намеренно: вся работа идёт из Java потока, через JNI. Если
/// захочется отдать симуляцию в worker — нужно или обернуть в `Mutex` на
/// стороне Java, или сделать message-pump в Rust.
pub struct PhysicsWorld {
    pub bodies: RigidBodySet,
    pub colliders: ColliderSet,
    pub gravity: Vector<Real>,
    pub params: IntegrationParameters,
    pub islands: IslandManager,
    pub broad_phase: DefaultBroadPhase,
    pub narrow_phase: NarrowPhase,
    pub impulses: ImpulseJointSet,
    pub multibodies: MultibodyJointSet,
    pub ccd: CCDSolver,
    pub query: QueryPipeline,
    pub pipeline: PhysicsPipeline,
}

impl PhysicsWorld {
    /// Создаёт мир с гравитацией Земли (-9.81 по Y).
    pub fn new() -> Self {
        Self {
            bodies: RigidBodySet::new(),
            colliders: ColliderSet::new(),
            gravity: vector![0.0, -9.81, 0.0],
            params: IntegrationParameters::default(),
            islands: IslandManager::new(),
            broad_phase: DefaultBroadPhase::new(),
            narrow_phase: NarrowPhase::new(),
            impulses: ImpulseJointSet::new(),
            multibodies: MultibodyJointSet::new(),
            ccd: CCDSolver::new(),
            query: QueryPipeline::new(),
            pipeline: PhysicsPipeline::new(),
        }
    }

    /// Добавляет dynamic rigid body в позицию `(x,y,z)` с минимальным
    /// ball-коллайдером радиуса {@code 0.5} и плотностью {@code 1.0}.
    ///
    /// <p>Коллайдер обязателен, потому что в Rapier масса dynamic тела
    /// вычисляется из коллайдеров: тело без коллайдеров имеет
    /// {@code effective_inv_mass = 0} и интегратор сил не применяет к нему
    /// гравитацию (поведение совпадает с static). Минимальный sphere(0.5)
    /// даёт ненулевую массу и сохраняет смысл "точечной массы" для stage 2.5.
    /// В stage 2.6+ API расширится явным выбором формы коллайдера.
    pub fn add_dynamic_body(&mut self, x: Real, y: Real, z: Real) -> RigidBodyHandle {
        let body = RigidBodyBuilder::dynamic()
            .translation(vector![x, y, z])
            .build();
        let handle = self.bodies.insert(body);
        let collider = ColliderBuilder::ball(0.5).density(1.0).build();
        self.colliders
            .insert_with_parent(collider, handle, &mut self.bodies);
        handle
    }

    /// Один физический тик. `dt` — шаг интеграции в секундах.
    pub fn step(&mut self, dt: Real) {
        self.params.dt = dt;
        let physics_hooks = ();
        let event_handler = ();
        self.pipeline.step(
            &self.gravity,
            &self.params,
            &mut self.islands,
            &mut self.broad_phase,
            &mut self.narrow_phase,
            &mut self.bodies,
            &mut self.colliders,
            &mut self.impulses,
            &mut self.multibodies,
            &mut self.ccd,
            Some(&mut self.query),
            &physics_hooks,
            &event_handler,
        );
    }

    /// Возвращает позицию тела как `[x,y,z]` или `[NaN,NaN,NaN]` если
    /// handle уже не валиден (тело удалено или generation mismatch).
    pub fn body_position(&self, handle: RigidBodyHandle) -> [Real; 3] {
        match self.bodies.get(handle) {
            Some(body) => {
                let t = body.translation();
                [t.x, t.y, t.z]
            }
            None => [Real::NAN, Real::NAN, Real::NAN],
        }
    }
}

impl Default for PhysicsWorld {
    fn default() -> Self {
        Self::new()
    }
}

/// Упаковывает `RigidBodyHandle` в `jlong`: low 32 = index, high 32 = generation.
pub fn pack_body_handle(h: RigidBodyHandle) -> i64 {
    let (idx, gen) = h.into_raw_parts();
    ((idx as u64) | ((gen as u64) << 32)) as i64
}

/// Распаковывает `jlong` обратно в `RigidBodyHandle`.
pub fn unpack_body_handle(packed: i64) -> RigidBodyHandle {
    let p = packed as u64;
    let idx = (p & 0xFFFF_FFFF) as u32;
    let gen = (p >> 32) as u32;
    RigidBodyHandle::from_raw_parts(idx, gen)
}

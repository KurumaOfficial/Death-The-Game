//! Тактическая сетка + A* pathfinding (stage 3.3a).
//!
//! 2D сетка размера `width × height` с булевыми клетками "проходимо /
//! заблокировано". Поиск кратчайшего пути 4-направленного движения через
//! A* с Manhattan-heuristic'ой и [`BinaryHeap`] для open-set'а.
//!
//! Этот модуль — pure Rust, никаких внешних crate'ов кроме `std`. Это
//! осознанный выбор: A* настолько прост, что подключать `pathfinding`
//! crate ради ~100 строк кода — лишняя зависимость с непредсказуемыми
//! breaking change'ами в будущем. Полные библиотечные алгоритмы (HPA*,
//! flow fields, JPS+) приедут в stage 3.3b+ когда станет ясно какой
//! движок нам реально нужен.
//!
//! ## Координатная система
//!
//! Origin `(0, 0)` — левый нижний угол. Клетка хранится в плоском массиве
//! по индексу `idx = y * width + x`. Допустимые значения:
//! `0 <= x < width`, `0 <= y < height`.
//!
//! ## Хэндлы
//!
//! `*mut Grid` упаковывается в `jlong` (raw Box pointer). Управление
//! временем жизни — на Java стороне (`close()` → `nativeDestroy`).
//!
//! ## Stage 3.3b+ план
//!
//! - Веса перехода (terrain cost) — `cost: Vec<u32>` параллельно `blocked`.
//! - Diagonal movement (8 направлений) — флаг в конструкторе + Octile heuristic.
//! - Multi-agent reservation system — резервация клеток по тикам.
//! - HPA* для крупных карт > 256×256 — кластеризация + abstract graph.

use std::cmp::Ordering;
use std::collections::BinaryHeap;

/// Тактическая сетка фиксированного размера.
///
/// Хранит флаги "blocked" в плоском `Vec<bool>` (одна `bool` = 1 байт).
/// Для типичных карт < 64×64 это ~4KB heap'а — пренебрежимо. Для
/// крупных карт `Vec<u64>` с битовой упаковкой даст 8× выигрыш по памяти,
/// но это уже на stage 3.3b+ когда понадобится.
pub struct Grid {
    width: u32,
    height: u32,
    blocked: Vec<bool>,
}

impl Grid {
    /// Создаёт сетку `width × height` со всеми клетками проходимыми.
    /// Паника при `width == 0` или `height == 0` — это логическая ошибка
    /// caller'а (нет смысла в пустой сетке).
    pub fn new(width: u32, height: u32) -> Self {
        assert!(width > 0 && height > 0, "Grid dimensions must be > 0");
        let size = (width as usize) * (height as usize);
        Self {
            width,
            height,
            blocked: vec![false; size],
        }
    }

    pub fn width(&self) -> u32 {
        self.width
    }

    pub fn height(&self) -> u32 {
        self.height
    }

    /// Линейный индекс клетки `(x, y)` в плоском массиве. Caller отвечает
    /// за in-bounds (метод не проверяет — для горячего пути).
    #[inline]
    fn idx(&self, x: u32, y: u32) -> usize {
        (y as usize) * (self.width as usize) + (x as usize)
    }

    /// Помечает клетку проходимой/непроходимой. Out-of-bounds — silent
    /// no-op (Java-сторона валидирует bounds через ensureBounds; этот
    /// слой надёжен на случай гоночных JNI-вызовов).
    pub fn set_blocked(&mut self, x: u32, y: u32, blocked: bool) {
        if x < self.width && y < self.height {
            let i = self.idx(x, y);
            self.blocked[i] = blocked;
        }
    }

    /// Возвращает `true` если клетка непроходима. Out-of-bounds клетка
    /// считается заблокированной (защищает A* от выхода за границы).
    pub fn is_blocked(&self, x: u32, y: u32) -> bool {
        if x >= self.width || y >= self.height {
            return true;
        }
        self.blocked[self.idx(x, y)]
    }

    /// A* с Manhattan-heuristic'ой по 4-направленному соседству.
    ///
    /// Возвращает путь как `Vec<(x, y)>` от start до goal включительно.
    /// Особые случаи:
    /// - start или goal вне границ → пустой `Vec`;
    /// - start или goal заблокированы → пустой `Vec`;
    /// - start == goal → `vec![(sx, sy)]` (1 клетка);
    /// - пути нет (полностью отрезано) → пустой `Vec`.
    ///
    /// Сложность: O((W·H) log(W·H)) worst case, обычно гораздо меньше
    /// за счёт heuristic'ы. На сетке 8×8 — <0.1ms.
    pub fn find_path(&self, sx: u32, sy: u32, gx: u32, gy: u32) -> Vec<(u32, u32)> {
        if sx >= self.width || sy >= self.height || gx >= self.width || gy >= self.height {
            return Vec::new();
        }
        if self.is_blocked(sx, sy) || self.is_blocked(gx, gy) {
            return Vec::new();
        }
        if sx == gx && sy == gy {
            return vec![(sx, sy)];
        }

        let size = (self.width as usize) * (self.height as usize);
        let start = self.idx(sx, sy);
        let goal = self.idx(gx, gy);

        // g_score[i] = текущая лучшая известная стоимость пути start→i.
        // u32::MAX используется как sentinel "ещё не посещали".
        let mut g_score = vec![u32::MAX; size];
        // came_from[i] = предыдущая клетка на пути start→i. usize::MAX = "нет".
        let mut came_from = vec![usize::MAX; size];

        g_score[start] = 0;

        let mut open = BinaryHeap::new();
        open.push(AStarNode {
            idx: start,
            f: manhattan(sx, sy, gx, gy),
        });

        let w_i32 = self.width as i32;
        let h_i32 = self.height as i32;

        while let Some(AStarNode { idx: current, f: _ }) = open.pop() {
            if current == goal {
                // Reconstruct: goal → ... → start через came_from, потом reverse.
                let mut path = Vec::new();
                let mut cur = goal;
                loop {
                    let x = (cur % self.width as usize) as u32;
                    let y = (cur / self.width as usize) as u32;
                    path.push((x, y));
                    if cur == start {
                        break;
                    }
                    cur = came_from[cur];
                    if cur == usize::MAX {
                        // Не должно случиться: если goal достигнут, came_from
                        // от goal до start полностью заполнен. Защита от багов.
                        return Vec::new();
                    }
                }
                path.reverse();
                return path;
            }

            let cx = (current % self.width as usize) as i32;
            let cy = (current / self.width as usize) as i32;

            for &(dx, dy) in &[(-1i32, 0i32), (1, 0), (0, -1), (0, 1)] {
                let nx = cx + dx;
                let ny = cy + dy;
                if nx < 0 || ny < 0 || nx >= w_i32 || ny >= h_i32 {
                    continue;
                }
                let nx_u = nx as u32;
                let ny_u = ny as u32;
                if self.is_blocked(nx_u, ny_u) {
                    continue;
                }
                let n_idx = self.idx(nx_u, ny_u);
                let tentative = g_score[current].saturating_add(1);
                if tentative < g_score[n_idx] {
                    came_from[n_idx] = current;
                    g_score[n_idx] = tentative;
                    let h = manhattan(nx_u, ny_u, gx, gy);
                    open.push(AStarNode {
                        idx: n_idx,
                        f: tentative.saturating_add(h),
                    });
                }
            }
        }

        Vec::new()
    }
}

/// Manhattan distance — допустимая (admissible) и консистентная
/// heuristic'а для 4-directional grid'а с равными весами.
#[inline]
fn manhattan(x1: u32, y1: u32, x2: u32, y2: u32) -> u32 {
    let dx = (x1 as i64 - x2 as i64).unsigned_abs() as u32;
    let dy = (y1 as i64 - y2 as i64).unsigned_abs() as u32;
    dx + dy
}

/// Узел в open-set'е A*. Сравниваем по `f` (g + h), где меньшее значение
/// должно быть приоритетнее. Поскольку `BinaryHeap` — max-heap, реализуем
/// `Ord` "наоборот" (меньшее f → больший Ord → top of heap).
#[derive(Eq, PartialEq)]
struct AStarNode {
    idx: usize,
    f: u32,
}

impl Ord for AStarNode {
    fn cmp(&self, other: &Self) -> Ordering {
        // reversed: меньшее f → выше priority.
        other.f.cmp(&self.f).then_with(|| other.idx.cmp(&self.idx))
    }
}

impl PartialOrd for AStarNode {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_grid_finds_direct_path() {
        let g = Grid::new(8, 8);
        let path = g.find_path(0, 0, 3, 0);
        assert_eq!(path, vec![(0, 0), (1, 0), (2, 0), (3, 0)]);
    }

    #[test]
    fn same_start_and_goal_returns_one_cell() {
        let g = Grid::new(8, 8);
        let path = g.find_path(2, 3, 2, 3);
        assert_eq!(path, vec![(2, 3)]);
    }

    #[test]
    fn blocked_goal_returns_empty() {
        let mut g = Grid::new(8, 8);
        g.set_blocked(7, 7, true);
        let path = g.find_path(0, 0, 7, 7);
        assert!(path.is_empty());
    }

    #[test]
    fn blocked_start_returns_empty() {
        let mut g = Grid::new(8, 8);
        g.set_blocked(0, 0, true);
        let path = g.find_path(0, 0, 7, 7);
        assert!(path.is_empty());
    }

    #[test]
    fn out_of_bounds_returns_empty() {
        let g = Grid::new(8, 8);
        assert!(g.find_path(0, 0, 99, 0).is_empty());
        assert!(g.find_path(0, 0, 0, 99).is_empty());
    }

    #[test]
    fn wall_with_gap_routes_through_gap() {
        let mut g = Grid::new(8, 8);
        // Стена по строке y=4, кроме клетки (3, 4) — единственный проход.
        for x in 0..8 {
            if x != 3 {
                g.set_blocked(x, 4, true);
            }
        }
        let path = g.find_path(0, 0, 7, 7);
        assert!(!path.is_empty(), "must find a path through the gap");
        assert_eq!(path[0], (0, 0));
        assert_eq!(path[path.len() - 1], (7, 7));
        // Путь должен проходить через (3, 4) — это единственный gap.
        assert!(path.contains(&(3, 4)), "path must use the only gap at (3, 4)");
        // Никакой другой клетки в строке y=4 быть не должно.
        for &(x, y) in &path {
            if y == 4 {
                assert_eq!(x, 3, "wall row crossed at non-gap cell ({}, {})", x, y);
            }
        }
        // 4-directional: каждый шаг — ровно один соседний.
        for w in path.windows(2) {
            let (x1, y1) = w[0];
            let (x2, y2) = w[1];
            let dx = (x1 as i32 - x2 as i32).unsigned_abs();
            let dy = (y1 as i32 - y2 as i32).unsigned_abs();
            assert_eq!(dx + dy, 1, "non-adjacent cells in path: ({},{}) → ({},{})", x1, y1, x2, y2);
        }
    }

    #[test]
    fn fully_walled_off_returns_empty() {
        let mut g = Grid::new(8, 8);
        // Полная стена y=4 без gap'а — отрезаем верх от низа.
        for x in 0..8 {
            g.set_blocked(x, 4, true);
        }
        let path = g.find_path(0, 0, 7, 7);
        assert!(path.is_empty(), "fully walled off must return no path");
    }

    #[test]
    fn manhattan_heuristic_is_optimal_on_open_grid() {
        let g = Grid::new(8, 8);
        // На пустой сетке кратчайший путь = Manhattan distance + 1 (включая start).
        let path = g.find_path(0, 0, 7, 7);
        // Manhattan(0,0 → 7,7) = 14. Path length = 15 (включает обе точки).
        assert_eq!(path.len(), 15);
    }
}

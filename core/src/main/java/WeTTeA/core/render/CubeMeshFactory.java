package WeTTeA.core.render;

/**
 * Stage E.1 — генератор геометрии единичного куба.
 *
 * <p>Куб {@code [-0.5..+0.5]} по всем трём осям, 6 граней, 24 вершины
 * (по 4 на грань — отдельные нормали и UV у соседних граней без
 * сглаживания), 36 индексов (2 треугольника × 6 граней × 3).
 *
 * <p><b>Layout вершины (32 байта).</b>
 * <pre>
 *   offset 0   : float3 position   (XYZ в local space)
 *   offset 12  : float3 normal     (XYZ в local space, единичный)
 *   offset 24  : float2 uv         (UV в [0..1])
 * </pre>
 *
 * <p>Этот же layout зашит в {@code cube.vert.glsl}
 * (location=0/1/2) и в {@link WeTTeA.api.render.Camera}-совместимом
 * Vulkan pipeline'е (см. desktop {@code VulkanPipeline}).
 *
 * <p><b>Winding.</b> Front face = CCW (смотрим на грань снаружи —
 * вершины обходятся против часовой стрелки), что совпадает с
 * Vulkan-default'ом и позволяет {@code cullMode=BACK} на pipeline'е.
 *
 * <p>Метод {@link #generate()} возвращает CPU-side данные, ничего не
 * аллоцируя на GPU; backend'ы (Vulkan / OpenGL ES) сами заливают их в
 * vertex/index buffer'ы (на 0.1.0 — через staging + DEVICE_LOCAL).
 *
 * <p>Не потокобезопасен; вызывать только при init'е сцены.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class CubeMeshFactory {

    /** Количество float'ов на вершину: pos(3) + normal(3) + uv(2) = 8. */
    public static final int FLOATS_PER_VERTEX = 8;

    /** Размер одной вершины в байтах (FLOATS_PER_VERTEX × 4). */
    public static final int VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

    /** Количество вершин в кубе. */
    public static final int VERTEX_COUNT = 24;

    /** Количество индексов в кубе (6 граней × 2 tri × 3). */
    public static final int INDEX_COUNT = 36;

    private CubeMeshFactory() {
    }

    /**
     * Генерирует CPU-side данные единичного куба.
     *
     * @return новый {@link CubeMesh} с массивами {@code float[192]}
     *         (24 vertex × 8 float) и {@code short[36]}
     */
    public static CubeMesh generate() {
        float[] vertices = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
        short[] indices  = new short[INDEX_COUNT];

        int v = 0;
        int i = 0;

        // +X (правая) — нормаль (1,0,0)
        v = pushQuad(vertices, v,
                +0.5f, -0.5f, +0.5f,   1, 0, 0,   0, 1,
                +0.5f, -0.5f, -0.5f,   1, 0, 0,   1, 1,
                +0.5f, +0.5f, -0.5f,   1, 0, 0,   1, 0,
                +0.5f, +0.5f, +0.5f,   1, 0, 0,   0, 0);
        i = pushQuadIndices(indices, i, 0);

        // -X (левая) — нормаль (-1,0,0)
        v = pushQuad(vertices, v,
                -0.5f, -0.5f, -0.5f,  -1, 0, 0,   0, 1,
                -0.5f, -0.5f, +0.5f,  -1, 0, 0,   1, 1,
                -0.5f, +0.5f, +0.5f,  -1, 0, 0,   1, 0,
                -0.5f, +0.5f, -0.5f,  -1, 0, 0,   0, 0);
        i = pushQuadIndices(indices, i, 4);

        // +Y (верхняя) — нормаль (0,1,0)
        v = pushQuad(vertices, v,
                -0.5f, +0.5f, +0.5f,   0, 1, 0,   0, 1,
                +0.5f, +0.5f, +0.5f,   0, 1, 0,   1, 1,
                +0.5f, +0.5f, -0.5f,   0, 1, 0,   1, 0,
                -0.5f, +0.5f, -0.5f,   0, 1, 0,   0, 0);
        i = pushQuadIndices(indices, i, 8);

        // -Y (нижняя) — нормаль (0,-1,0)
        v = pushQuad(vertices, v,
                -0.5f, -0.5f, -0.5f,   0, -1, 0,  0, 1,
                +0.5f, -0.5f, -0.5f,   0, -1, 0,  1, 1,
                +0.5f, -0.5f, +0.5f,   0, -1, 0,  1, 0,
                -0.5f, -0.5f, +0.5f,   0, -1, 0,  0, 0);
        i = pushQuadIndices(indices, i, 12);

        // +Z (передняя) — нормаль (0,0,1)
        v = pushQuad(vertices, v,
                -0.5f, -0.5f, +0.5f,   0, 0, 1,   0, 1,
                +0.5f, -0.5f, +0.5f,   0, 0, 1,   1, 1,
                +0.5f, +0.5f, +0.5f,   0, 0, 1,   1, 0,
                -0.5f, +0.5f, +0.5f,   0, 0, 1,   0, 0);
        i = pushQuadIndices(indices, i, 16);

        // -Z (задняя) — нормаль (0,0,-1)
        v = pushQuad(vertices, v,
                +0.5f, -0.5f, -0.5f,   0, 0, -1,  0, 1,
                -0.5f, -0.5f, -0.5f,   0, 0, -1,  1, 1,
                -0.5f, +0.5f, -0.5f,   0, 0, -1,  1, 0,
                +0.5f, +0.5f, -0.5f,   0, 0, -1,  0, 0);
        i = pushQuadIndices(indices, i, 20);

        if (v != vertices.length) {
            throw new IllegalStateException("BUG: ожидалось " + vertices.length + " float'ов, записано " + v);
        }
        if (i != indices.length) {
            throw new IllegalStateException("BUG: ожидалось " + indices.length + " индексов, записано " + i);
        }
        return new CubeMesh(vertices, indices);
    }

    private static int pushQuad(float[] dst, int offset,
                                float p0x, float p0y, float p0z, float n0x, float n0y, float n0z, float u0, float v0,
                                float p1x, float p1y, float p1z, float n1x, float n1y, float n1z, float u1, float v1,
                                float p2x, float p2y, float p2z, float n2x, float n2y, float n2z, float u2, float v2,
                                float p3x, float p3y, float p3z, float n3x, float n3y, float n3z, float u3, float v3) {
        offset = pushVertex(dst, offset, p0x, p0y, p0z, n0x, n0y, n0z, u0, v0);
        offset = pushVertex(dst, offset, p1x, p1y, p1z, n1x, n1y, n1z, u1, v1);
        offset = pushVertex(dst, offset, p2x, p2y, p2z, n2x, n2y, n2z, u2, v2);
        offset = pushVertex(dst, offset, p3x, p3y, p3z, n3x, n3y, n3z, u3, v3);
        return offset;
    }

    private static int pushVertex(float[] dst, int offset,
                                  float px, float py, float pz,
                                  float nx, float ny, float nz,
                                  float u, float v) {
        dst[offset++] = px;
        dst[offset++] = py;
        dst[offset++] = pz;
        dst[offset++] = nx;
        dst[offset++] = ny;
        dst[offset++] = nz;
        dst[offset++] = u;
        dst[offset++] = v;
        return offset;
    }

    private static int pushQuadIndices(short[] dst, int offset, int baseVertex) {
        // Quad: v0,v1,v2,v3 → tri (v0,v1,v2) + tri (v2,v3,v0); CCW при взгляде на front face.
        dst[offset++] = (short) (baseVertex + 0);
        dst[offset++] = (short) (baseVertex + 1);
        dst[offset++] = (short) (baseVertex + 2);
        dst[offset++] = (short) (baseVertex + 2);
        dst[offset++] = (short) (baseVertex + 3);
        dst[offset++] = (short) (baseVertex + 0);
        return offset;
    }

    /**
     * Stage E.1 — CPU-side данные куба.
     *
     * <p>{@link #vertices()} и {@link #indices()} возвращают <i>внутренние</i>
     * массивы (без копий) — backend копирует данные сразу в staging buffer
     * и больше к ним не возвращается. Изменять содержимое снаружи запрещено.
     *
     * @param vertices float[] длиной {@code VERTEX_COUNT * FLOATS_PER_VERTEX}
     * @param indices  short[] длиной {@code INDEX_COUNT}
     */
    public record CubeMesh(float[] vertices, short[] indices) {

        public CubeMesh {
            if (vertices.length != VERTEX_COUNT * FLOATS_PER_VERTEX) {
                throw new IllegalArgumentException(
                        "vertices.length=" + vertices.length + ", ожидалось " + (VERTEX_COUNT * FLOATS_PER_VERTEX));
            }
            if (indices.length != INDEX_COUNT) {
                throw new IllegalArgumentException(
                        "indices.length=" + indices.length + ", ожидалось " + INDEX_COUNT);
            }
        }

        public int vertexCount() { return VERTEX_COUNT; }
        public int indexCount()  { return INDEX_COUNT; }
    }
}

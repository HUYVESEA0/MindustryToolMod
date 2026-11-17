package mindustrytool.features.rangedisplay;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;

public final class RangeDrawUtils {

    private static final float[] CIRCLE_OFFSET_24 = prepareCircleOffset(24);
    private static final float[] CIRCLE_OFFSET_36 = prepareCircleOffset(36);
    private static final float[] CIRCLE_OFFSET_60 = prepareCircleOffset(60);

    private static final float[] CIRCLE_VERTICES_24 = new float[24 * 24];
    private static final float[] CIRCLE_VERTICES_36 = new float[36 * 24];
    private static final float[] CIRCLE_VERTICES_60 = new float[60 * 24];

    private static final Vec2 VEC_1 = new Vec2();
    private static final Vec2 VEC_2 = new Vec2();

    private static float initializedU = Float.NaN;
    private static float initializedV = Float.NaN;

    private RangeDrawUtils() {
    }

    private static float[] prepareCircleOffset(int sides) {
        float[] offsets = new float[sides * 4];
        float step = 360f / sides;
        for (int i = 0; i < sides; i++) {
            float angle1 = i * step;
            float angle2 = (i + 1) * step;
            int idx = i * 4;
            offsets[idx] = Mathf.cosDeg(angle1);
            offsets[idx + 1] = Mathf.sinDeg(angle1);
            offsets[idx + 2] = Mathf.cosDeg(angle2);
            offsets[idx + 3] = Mathf.sinDeg(angle2);
        }
        return offsets;
    }

    private static float[] getOffsets(int level) {
        if (level == 1) {
            return CIRCLE_OFFSET_24;
        }
        if (level == 3) {
            return CIRCLE_OFFSET_60;
        }
        return CIRCLE_OFFSET_36;
    }

    private static float[] getVertices(int level) {
        if (level == 1) {
            return CIRCLE_VERTICES_24;
        }
        if (level == 3) {
            return CIRCLE_VERTICES_60;
        }
        return CIRCLE_VERTICES_36;
    }

    private static boolean ensureUvInitialized(TextureRegion whiteRegion) {
        if (whiteRegion == null || whiteRegion.texture == null) {
            return false;
        }
        float u = whiteRegion.u;
        float v = whiteRegion.v;
        if (u == initializedU && v == initializedV) {
            return true;
        }
        initVertexUv(CIRCLE_VERTICES_24, 24, u, v);
        initVertexUv(CIRCLE_VERTICES_36, 36, u, v);
        initVertexUv(CIRCLE_VERTICES_60, 60, u, v);
        initializedU = u;
        initializedV = v;
        return true;
    }

    private static void initVertexUv(float[] vertices, int sides, float u, float v) {
        float mixColor = Color.clearFloatBits;
        for (int i = 0; i < sides; i++) {
            int off = i * 24;
            vertices[off + 3] = u;
            vertices[off + 4] = v;
            vertices[off + 5] = mixColor;
            vertices[off + 9] = u;
            vertices[off + 10] = v;
            vertices[off + 11] = mixColor;
            vertices[off + 15] = u;
            vertices[off + 16] = v;
            vertices[off + 17] = mixColor;
            vertices[off + 21] = u;
            vertices[off + 22] = v;
            vertices[off + 23] = mixColor;
        }
    }

    public static void fillCircle(float x, float y, float radius, int level) {
        if (radius <= 0f || Core.atlas == null) {
            return;
        }
        TextureRegion white = Core.atlas.white();
        if (!ensureUvInitialized(white)) {
            return;
        }
        float color = Draw.getColorPacked();
        float[] offset = getOffsets(level);
        float[] vertices = getVertices(level);
        int sides = offset.length / 4;

        for (int i = 0; i < sides; i++) {
            int idx = i * 24;
            int offIdx = i * 4;
            float offX1 = offset[offIdx];
            float offY1 = offset[offIdx + 1];
            float offX2 = offset[offIdx + 2];
            float offY2 = offset[offIdx + 3];

            vertices[idx] = x + offX1 * radius;
            vertices[idx + 1] = y + offY1 * radius;
            vertices[idx + 2] = color;

            vertices[idx + 6] = x;
            vertices[idx + 7] = y;
            vertices[idx + 8] = color;

            vertices[idx + 12] = x;
            vertices[idx + 13] = y;
            vertices[idx + 14] = color;

            vertices[idx + 18] = x + offX2 * radius;
            vertices[idx + 19] = y + offY2 * radius;
            vertices[idx + 20] = color;
        }

        Draw.vert(white.texture, vertices, 0, vertices.length);
    }

    public static void innerCircle(
            float x,
            float y,
            float innerRadius,
            float radius,
            Color innerColor,
            Color outerColor,
            int level) {
        if (radius <= 0f || Core.atlas == null) {
            return;
        }
        TextureRegion white = Core.atlas.white();
        if (!ensureUvInitialized(white)) {
            return;
        }
        float c1 = innerColor.toFloatBits();
        float c2 = outerColor.toFloatBits();
        float[] offset = getOffsets(level);
        float[] vertices = getVertices(level);
        int sides = offset.length / 4;

        for (int i = 0; i < sides; i++) {
            int idx = i * 24;
            int offIdx = i * 4;
            float offX1 = offset[offIdx];
            float offY1 = offset[offIdx + 1];
            float offX2 = offset[offIdx + 2];
            float offY2 = offset[offIdx + 3];

            vertices[idx] = x + offX1 * innerRadius;
            vertices[idx + 1] = y + offY1 * innerRadius;
            vertices[idx + 2] = c1;

            vertices[idx + 6] = x + offX1 * radius;
            vertices[idx + 7] = y + offY1 * radius;
            vertices[idx + 8] = c2;

            vertices[idx + 12] = x + offX2 * radius;
            vertices[idx + 13] = y + offY2 * radius;
            vertices[idx + 14] = c2;

            vertices[idx + 18] = x + offX2 * innerRadius;
            vertices[idx + 19] = y + offY2 * innerRadius;
            vertices[idx + 20] = c1;
        }

        Draw.vert(white.texture, vertices, 0, vertices.length);
    }

    public static void innerPoly(
            float x,
            float y,
            int sides,
            float innerRadius,
            float outerRadius,
            float rotation,
            Color innerColor,
            Color outerColor) {
        if (sides < 3 || outerRadius <= 0f) {
            return;
        }
        float c1 = innerColor.toFloatBits();
        float c2 = outerColor.toFloatBits();
        float step = 360f / sides;

        for (int i = 0; i < sides; i++) {
            float angle1 = i * step + rotation;
            float angle2 = (i + 1) * step + rotation;
            float cos1 = Mathf.cosDeg(angle1);
            float sin1 = Mathf.sinDeg(angle1);
            float cos2 = Mathf.cosDeg(angle2);
            float sin2 = Mathf.sinDeg(angle2);

            Fill.quad(
                    x + cos1 * innerRadius, y + sin1 * innerRadius, c1,
                    x + cos1 * outerRadius, y + sin1 * outerRadius, c2,
                    x + cos2 * outerRadius, y + sin2 * outerRadius, c2,
                    x + cos2 * innerRadius, y + sin2 * innerRadius, c1);
        }
    }

    public static int resolveLevel(float radius, float scale) {
        float screenRad = radius * Math.max(0.25f, scale);
        if (screenRad < 85f) {
            return 1;
        }
        if (screenRad > 220f) {
            return 3;
        }
        return 2;
    }

    public static void lineCircle(float x, float y, float radius, int level) {
        if (radius <= 0f || Core.atlas == null) {
            return;
        }
        TextureRegion white = Core.atlas.white();
        if (!ensureUvInitialized(white)) {
            return;
        }
        float halfStroke = Lines.getStroke() * 0.5f;
        float color = Draw.getColorPacked();
        float[] offset = getOffsets(level);
        float[] vertices = getVertices(level);
        int sides = offset.length / 4;
        float outerR = radius + halfStroke;
        float innerR = Math.max(0f, radius - halfStroke);

        for (int i = 0; i < sides; i++) {
            int idx = i * 24;
            int offIdx = i * 4;
            float offX1 = offset[offIdx];
            float offY1 = offset[offIdx + 1];
            float offX2 = offset[offIdx + 2];
            float offY2 = offset[offIdx + 3];

            vertices[idx] = x + offX1 * outerR;
            vertices[idx + 1] = y + offY1 * outerR;
            vertices[idx + 2] = color;

            vertices[idx + 6] = x + offX1 * innerR;
            vertices[idx + 7] = y + offY1 * innerR;
            vertices[idx + 8] = color;

            vertices[idx + 12] = x + offX2 * innerR;
            vertices[idx + 13] = y + offY2 * innerR;
            vertices[idx + 14] = color;

            vertices[idx + 18] = x + offX2 * outerR;
            vertices[idx + 19] = y + offY2 * outerR;
            vertices[idx + 20] = color;
        }

        Draw.vert(white.texture, vertices, 0, vertices.length);
    }

    public static void dashCircle(float x, float y, float radius, int level) {
        if (radius <= 0f || Core.atlas == null) {
            return;
        }
        TextureRegion white = Core.atlas.white();
        if (!ensureUvInitialized(white)) {
            return;
        }
        float halfStroke = Lines.getStroke() * 0.5f;
        float color = Draw.getColorPacked();
        float[] offset = getOffsets(level);
        float[] vertices = getVertices(level);
        int sides = offset.length / 4;
        float outerR = radius + halfStroke;
        float innerR = Math.max(0f, radius - halfStroke);

        int quadCount = 0;
        for (int i = 0; i < sides; i += 2) {
            int idx = quadCount * 24;
            int offIdx = i * 4;
            float offX1 = offset[offIdx];
            float offY1 = offset[offIdx + 1];
            float offX2 = offset[offIdx + 2];
            float offY2 = offset[offIdx + 3];

            vertices[idx] = x + offX1 * outerR;
            vertices[idx + 1] = y + offY1 * outerR;
            vertices[idx + 2] = color;

            vertices[idx + 6] = x + offX1 * innerR;
            vertices[idx + 7] = y + offY1 * innerR;
            vertices[idx + 8] = color;

            vertices[idx + 12] = x + offX2 * innerR;
            vertices[idx + 13] = y + offY2 * innerR;
            vertices[idx + 14] = color;

            vertices[idx + 18] = x + offX2 * outerR;
            vertices[idx + 19] = y + offY2 * outerR;
            vertices[idx + 20] = color;
            quadCount++;
        }

        Draw.vert(white.texture, vertices, 0, quadCount * 24);
    }

    public static void circleFan(float x, float y, float radius, float angle, float rotate, int sides) {
        if (radius <= 0f || angle <= 0f || sides <= 0) {
            return;
        }
        float clampedAngle = Math.min(angle, 360f);
        float step = 360f / sides;
        int fullSteps = (int) (clampedAngle / 360f * sides);
        float rem = clampedAngle - fullSteps * step;

        for (int i = 0; i < fullSteps; i++) {
            float a1 = rotate + i * step;
            float a2 = rotate + (i + 1) * step;
            VEC_1.set(Mathf.cosDeg(a1), Mathf.sinDeg(a1)).scl(radius).add(x, y);
            VEC_2.set(Mathf.cosDeg(a2), Mathf.sinDeg(a2)).scl(radius).add(x, y);
            Fill.quad(x, y, VEC_1.x, VEC_1.y, VEC_2.x, VEC_2.y, x, y);
        }

        if (rem > 0.001f) {
            float a1 = rotate + fullSteps * step;
            float a2 = rotate + clampedAngle;
            VEC_1.set(Mathf.cosDeg(a1), Mathf.sinDeg(a1)).scl(radius).add(x, y);
            VEC_2.set(Mathf.cosDeg(a2), Mathf.sinDeg(a2)).scl(radius).add(x, y);
            Fill.quad(x, y, VEC_1.x, VEC_1.y, VEC_2.x, VEC_2.y, x, y);
        }
    }
}

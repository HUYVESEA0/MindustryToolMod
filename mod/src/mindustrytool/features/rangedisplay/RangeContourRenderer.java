package mindustrytool.features.rangedisplay;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Interp;
import arc.util.Nullable;
import arc.util.Time;
import mindustry.graphics.Layer;

public class RangeContourRenderer {

    public static final int LAYER_OVERDRIVE = 256;
    public static final int LAYER_MENDER = 257;
    public static final int LAYER_POINT_DEFENSE = 258;
    public static final int LAYER_SPAWNER = 259;
    public static final int LAYER_NO_AMMO = 260;
    public static final int LAYER_UNIT_BUILD = 261;
    public static final int LAYER_UNIT_MINE = 262;
    public static final int MAX_LAYERS = 264;

    private static final Color CLEAR_WHITE = new Color(1f, 1f, 1f, 0f);

    private static final String SCREENSPACE_VERT = ""
            + "attribute vec4 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main(){\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = a_position;\n"
            + "}\n";

    private static final String ENTITY_RANGE_HIGH_FRAG = ""
            + "#define HIGHP\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_campos;\n"
            + "uniform vec2 u_resolution;\n"
            + "uniform float u_time;\n"
            + "uniform float u_stroke;\n"
            + "uniform float u_alpha;\n"
            + "uniform float u_borderAlpha;\n"
            + "varying vec2 v_texCoords;\n"
            + "const float threshold = 0.01;\n"
            + "void main() {\n"
            + "    vec2 v = u_stroke * (1.0 / u_resolution);\n"
            + "    vec4 base = texture2D(u_texture, v_texCoords);\n"
            + "    if (base.a <= threshold) {\n"
            + "        gl_FragColor = vec4(0.0);\n"
            + "        return;\n"
            + "    }\n"
            + "    vec2 worldCoord = vec2(v_texCoords.x * u_resolution.x + u_campos.x, v_texCoords.y * u_resolution.y + u_campos.y);\n"
            + "    float m = min(min(min(\n"
            + "        texture2D(u_texture, v_texCoords + vec2(1.0, 0.0) * v).a,\n"
            + "        texture2D(u_texture, v_texCoords + vec2(0.0, 1.0) * v).a),\n"
            + "        texture2D(u_texture, v_texCoords + vec2(-1.0, 0.0) * v).a),\n"
            + "        texture2D(u_texture, v_texCoords + vec2(0.0, -1.0) * v).a);\n"
            + "    float time = u_time * 0.1;\n"
            + "    float a =\n"
            + "        sin((worldCoord.x + worldCoord.y) * 0.0831 + time * 0.0095) +\n"
            + "        sin((-worldCoord.x + worldCoord.y) * 0.075 + time * 0.056) +\n"
            + "        sin((worldCoord.x - worldCoord.y) * 0.0546 + time * 0.21) +\n"
            + "        sin((-worldCoord.x - worldCoord.y) * 0.03432 + time * 0.712);\n"
            + "    a = (a / 4.0 + 1.0) / 2.0;\n"
            + "    float s = length(base.rgb);\n"
            + "    float stepM = step(m, threshold);\n"
            + "    float stepS = step(s, threshold);\n"
            + "    float con1 = stepM;\n"
            + "    float con2 = 1.0 - con1;\n"
            + "    vec4 c1 = vec4(base.rgb, u_alpha);\n"
            + "    vec4 c2 = vec4(vec3(1.0), u_alpha);\n"
            + "    gl_FragColor = vec4(base.rgb, a * u_borderAlpha) * con1\n"
            + "        + (mix(c1, c2, a) * stepS + c1 * (1.0 - stepS)) * con2;\n"
            + "}\n";

    private static final String ENTITY_RANGE_LOW_FRAG = ""
            + "#define HIGHP\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_campos;\n"
            + "uniform vec2 u_resolution;\n"
            + "uniform float u_time;\n"
            + "uniform float u_stroke;\n"
            + "uniform float u_alpha;\n"
            + "uniform float u_borderAlpha;\n"
            + "varying vec2 v_texCoords;\n"
            + "const float threshold = 0.01;\n"
            + "void main() {\n"
            + "    vec2 v = u_stroke * (1.0 / u_resolution);\n"
            + "    vec4 base = texture2D(u_texture, v_texCoords);\n"
            + "    if (base.a <= threshold) {\n"
            + "        gl_FragColor = vec4(0.0);\n"
            + "        return;\n"
            + "    }\n"
            + "    float m = min(min(min(\n"
            + "        texture2D(u_texture, v_texCoords + vec2(1.0, 0.0) * v).a,\n"
            + "        texture2D(u_texture, v_texCoords + vec2(0.0, 1.0) * v).a),\n"
            + "        texture2D(u_texture, v_texCoords + vec2(-1.0, 0.0) * v).a),\n"
            + "        texture2D(u_texture, v_texCoords + vec2(0.0, -1.0) * v).a);\n"
            + "    float s = length(base.rgb);\n"
            + "    float stepM = step(m, threshold);\n"
            + "    float stepS = step(s, threshold);\n"
            + "    float con1 = stepM;\n"
            + "    float con2 = 1.0 - con1;\n"
            + "    vec4 c1 = vec4(base.rgb, u_alpha);\n"
            + "    vec4 c2 = vec4(0.0);\n"
            + "    gl_FragColor = vec4(base.rgb, u_borderAlpha) * con1\n"
            + "        + (c2 * stepS + c1 * (1.0 - stepS)) * con2;\n"
            + "}\n";

    private static final float BASE_LAYER = Layer.light - 2.5f;
    private final Color tempColor = new Color();
    private final Runnable captureRunnable = this::beginCapture;
    private final Runnable renderRunnable = this::endAndBlit;

    private boolean frameActive = false;
    private float frameFillAlpha = 0.08f;
    private float frameBorderAlpha = 0.6f;
    private float frameStroke = 2f;
    private boolean frameHighQuality = true;

    private @Nullable FrameBuffer buffer;
    private @Nullable Shader highShader;
    private @Nullable Shader lowShader;
    private boolean initialized = false;
    private boolean initializationFailed = false;
    private boolean capturing = false;

    public RangeContourRenderer() {
    }

    public void resetFrame() {
        frameActive = false;
    }

    public boolean ensureInitialized() {
        if (initialized) {
            return buffer != null && highShader != null && lowShader != null;
        }
        if (initializationFailed || Core.gl == null || Core.graphics == null || Core.atlas == null) {
            return false;
        }
        FrameBuffer fbo = null;
        Shader high = null;
        Shader low = null;
        try {
            int w = Math.max(2, Core.graphics.getWidth() / 2);
            int h = Math.max(2, Core.graphics.getHeight() / 2);
            fbo = new FrameBuffer(w, h);
            fbo.getTexture().setFilter(TextureFilter.linear);
            high = new Shader(SCREENSPACE_VERT, ENTITY_RANGE_HIGH_FRAG);
            low = new Shader(SCREENSPACE_VERT, ENTITY_RANGE_LOW_FRAG);
            this.buffer = fbo;
            this.highShader = high;
            this.lowShader = low;
            this.initialized = true;
            return true;
        } catch (Throwable ignored) {
            if (fbo != null) {
                fbo.dispose();
            }
            if (high != null) {
                high.dispose();
            }
            if (low != null) {
                low.dispose();
            }
            this.initializationFailed = true;
            return false;
        }
    }

    public boolean drawMergedShape(
            int layerId,
            int entityId,
            float x,
            float y,
            float radius,
            int edges,
            float polyRot,
            Color color,
            float fillAlpha,
            float stroke,
            float opacity,
            boolean highQuality,
            float rendererScale) {
        if (radius <= 1f || layerId < 0 || layerId >= MAX_LAYERS || !ensureInitialized()) {
            return false;
        }

        if (!frameActive) {
            frameActive = true;
            frameFillAlpha = fillAlpha * opacity;
            frameBorderAlpha = 0.65f * opacity;
            frameStroke = stroke;
            frameHighQuality = highQuality;
            Draw.drawRange(BASE_LAYER, 0.02f, captureRunnable, renderRunnable);
        }

        float prevZ = Draw.z();
        float layerOffset = (layerId % 32) * 0.0001f;
        int lodLevel = RangeDrawUtils.resolveLevel(radius, rendererScale);

        tempColor.set(color).a(0.65f * opacity);
        Draw.z(BASE_LAYER + 0.001f + layerOffset);
        Draw.color(tempColor);
        if (edges <= 0) {
            RangeDrawUtils.fillCircle(x, y, radius - 1f, lodLevel);
        } else {
            Fill.poly(x, y, edges, radius - 1f, polyRot);
        }

        if (highQuality) {
            Draw.z(BASE_LAYER + 0.006f + layerOffset);
            int hash = entityId * 1103515245 + 12345;
            float timeOffset = (hash & 0x7FFFFFFF) % 240;
            float phaseScl = 0.9f + (((hash >>> 16) & 0xFF) / 255f) * 0.2f;
            float r = ((Time.time * phaseScl + timeOffset) % 240f) / 240f;
            if (r < 0f) {
                r += 1f;
            }
            float inner = Interp.pow3.apply(r);
            float outer = Interp.pow3Out.apply(r);

            if (edges <= 0) {
                RangeDrawUtils.innerCircle(x, y, inner * radius, outer * radius, CLEAR_WHITE, Color.white, 1);
            } else {
                Draw.color();
                RangeDrawUtils.innerPoly(x, y, edges, inner * radius, outer * radius, polyRot, CLEAR_WHITE, Color.white);
            }
        }

        Draw.z(BASE_LAYER + 0.012f + layerOffset);
        Lines.stroke(1f, Color.black);
        if (edges <= 0) {
            RangeDrawUtils.lineCircle(x, y, radius, lodLevel);
        } else {
            Lines.poly(x, y, edges, radius, polyRot);
        }

        Draw.color();
        Draw.z(prevZ);
        return true;
    }

    private void beginCapture() {
        FrameBuffer fbo = this.buffer;
        if (fbo == null || Core.graphics == null) {
            return;
        }
        if (capturing) {
            fbo.end();
            capturing = false;
        }
        int w = Math.max(2, Core.graphics.getWidth() / 2);
        int h = Math.max(2, Core.graphics.getHeight() / 2);
        fbo.resize(w, h);
        fbo.begin(Color.clear);
        capturing = true;
    }

    private void endAndBlit() {
        FrameBuffer fbo = this.buffer;
        if (!capturing || fbo == null) {
            return;
        }
        capturing = false;
        fbo.end();
        if (Core.camera == null) {
            return;
        }

        Shader shader = frameHighQuality ? highShader : lowShader;
        if (shader == null) {
            return;
        }
        shader.bind();
        shader.apply();
        shader.setUniformf("u_time", Time.time);
        shader.setUniformf("u_resolution", Core.camera.width, Core.camera.height);
        shader.setUniformf(
                "u_campos",
                Core.camera.position.x - Core.camera.width / 2f,
                Core.camera.position.y - Core.camera.height / 2f);
        shader.setUniformf("u_stroke", frameStroke);
        shader.setUniformf("u_alpha", frameFillAlpha);
        shader.setUniformf("u_borderAlpha", frameBorderAlpha);
        shader.setUniformi("u_texture", 0);
        fbo.blit(shader);
    }

    public void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
        if (highShader != null) {
            highShader.dispose();
            highShader = null;
        }
        if (lowShader != null) {
            lowShader.dispose();
            lowShader = null;
        }
        initialized = false;
        initializationFailed = false;
    }
}

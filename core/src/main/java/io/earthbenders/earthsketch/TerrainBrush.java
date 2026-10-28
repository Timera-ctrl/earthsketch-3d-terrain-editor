package io.earthbenders.earthsketch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sculpts terrain elevation and soil thickness.
 *
 * Every mode produces a per-point height delta, which applyHeightDelta
 * routes to both heights[] and soilDepth[] so the soil/bedrock model
 * stays consistent (see class-level history in the original commit).
 *
 * Undo support:
 *   beginStroke() is called when the user presses the mouse down in
 *   sculpt mode. Every sculptAt() during the drag records the "before"
 *   state of each grid point the first time it is touched, and remembers
 *   that it was touched.
 *
 *   endStroke() is called on mouse up. It builds a single TerrainEdit
 *   covering every point the stroke touched, so the whole drag becomes
 *   one Ctrl+Z.
 */
public class TerrainBrush {

    public enum Mode {
        DIG, FILL, FLATTEN, LEVEL, SMOOTH
    }

    private final ElvLoader loader;

    private Mode mode = Mode.DIG;
    private float radius = 8f;
    private float strength = 0.5f;
    private float falloff = 1f;
    private float targetHeight = 0f;

    // Stroke accumulators. Cleared on beginStroke(), consumed by endStroke().
    private final Set<Integer> strokeTouched = new HashSet<>();
    private final Map<Integer, float[]> strokeSnapShot = new HashMap<>();

    public TerrainBrush(ElvLoader loader) {
        this.loader = loader;
    }

    /** Start a new stroke. Called on mouse-down in sculpt mode. */
    public void beginStroke() {
        strokeTouched.clear();
        strokeSnapShot.clear();
    }

    /** End the current stroke and return the edit for the undo manager. */
    public TerrainEdit endStroke() {
        TerrainEdit edit = new TerrainEdit();
        for (int idx : strokeTouched) {
            float[] before = strokeSnapShot.get(idx);
            float[] after = new float[]{
                loader.heights[idx],
                loader.soilDepth[idx],
                loader.sand[idx],
                loader.silt[idx],
                loader.clay[idx]
            };
            edit.recordPoint(idx, before, after);
        }
        strokeTouched.clear();
        strokeSnapShot.clear();
        return edit;
    }

    /** Capture the current state of a point the first time it is touched. */
    private void touchPoint(int idx) {
        if (!strokeSnapShot.containsKey(idx)) {
            strokeSnapShot.put(idx, new float[]{
                loader.heights[idx],
                loader.soilDepth[idx],
                loader.sand[idx],
                loader.silt[idx],
                loader.clay[idx]
            });
        }
        strokeTouched.add(idx);
    }

    public void sculptAt(int centerX, int centerZ) {
        int rb = (int) Math.ceil(radius);
        float r2 = radius * radius;

        int startX = Math.max(0, centerX - rb);
        int endX   = Math.min(loader.dimX - 1, centerX + rb);
        int startZ = Math.max(0, centerZ - rb);
        int endZ   = Math.min(loader.dimY - 1, centerZ + rb);

        if (startX > endX || startZ > endZ) return;

        // FLATTEN needs a target computed before any point is modified.
        float flatTarget = 0f;
        if (mode == Mode.FLATTEN) {
            float sum = 0f, wsum = 0f;
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {
                    int dx = x - centerX, dz = z - centerZ;
                    if (dx * dx + dz * dz > r2) continue;
                    float w = falloffWeight(dx, dz);
                    sum  += loader.heights[z * loader.dimX + x] * w;
                    wsum += w;
                }
            }
            if (wsum > 0f) flatTarget = sum / wsum;
        }

        for (int z = startZ; z <= endZ; z++) {
            for (int x = startX; x <= endX; x++) {
                int dx = x - centerX, dz = z - centerZ;
                if (dx * dx + dz * dz > r2) continue;

                int idx = z * loader.dimX + x;
                touchPoint(idx);
                float w = falloffWeight(dx, dz);

                switch (mode) {
                    case DIG:
                        applyHeightDelta(idx, -strength * w);
                        break;
                    case FILL:
                        applyHeightDelta(idx, +strength * w);
                        break;
                    case FLATTEN:
                        applyHeightDelta(idx, (flatTarget - loader.heights[idx]) * w);
                        break;
                    case LEVEL:
                        applyHeightDelta(idx, (targetHeight - loader.heights[idx]) * w);
                        break;
                    case SMOOTH:
                        applyHeightDelta(idx, (smoothTarget(x, z) - loader.heights[idx]) * w);
                        break;
                }
            }
        }
    }

    private void applyHeightDelta(int idx, float dh) {
        if (dh == 0f) return;
        loader.heights[idx]   += dh;
        loader.soilDepth[idx] = Math.max(0f, loader.soilDepth[idx] + dh);
    }

    private float falloffWeight(int dx, int dz) {
        if (falloff <= 0f) return 1f;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float t = 1f - dist / radius;
        if (t < 0f) t = 0f;
        float smooth = t * t * (3f - 2f * t);
        return 1f - falloff + falloff * smooth;
    }

    private float smoothTarget(int x, int z) {
        int xL = Math.max(x - 1, 0);
        int xR = Math.min(x + 1, loader.dimX - 1);
        int zU = Math.max(z - 1, 0);
        int zD = Math.min(z + 1, loader.dimY - 1);

        return (
            loader.heights[z * loader.dimX + xL] +
            loader.heights[z * loader.dimX + xR] +
            loader.heights[zU * loader.dimX + x] +
            loader.heights[zD * loader.dimX + x]
        ) * 0.25f;
    }

    public void stroke(int startX, int startZ, int endX, int endZ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps == 0) { sculptAt(startX, startZ); return; }

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            sculptAt(Math.round(startX + dx * t), Math.round(startZ + dz * t));
        }
    }

    public Mode  getMode()   { return mode; }
    public void  setMode(Mode mode) { this.mode = mode; }

    public float getRadius() { return radius; }
    public void  setRadius(float r) { if (r > 0f) this.radius = r; }

    public float getStrength() { return strength; }
    public void  setStrength(float s) { this.strength = s; }

    public float getFalloff() { return falloff; }
    public void  setFalloff(float f) { this.falloff = Math.max(0f, Math.min(1f, f)); }

    public float getTargetHeight() { return targetHeight; }
    public void  setTargetHeight(float h) { this.targetHeight = h; }
}

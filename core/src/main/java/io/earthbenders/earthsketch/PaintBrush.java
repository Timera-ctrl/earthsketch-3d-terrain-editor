package io.earthbenders.earthsketch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Paints soil composition and depth onto the terrain.
 *
 * Falloff
 * -------
 * Every point inside the brush disc gets a weight w in [0, 1] from a
 * smoothstep curve: 1 at the centre, 0 at the rim, and a smooth S-shape
 * in between. The falloff field in [0, 1] controls the steepness:
 *
 *   falloff = 0  ->  w = 1 everywhere inside the disc (hard edge)
 *   falloff = 1  ->  w follows the full smoothstep (soft edge)
 *
 * Composition and depth in Set mode blend from the pre-stroke state
 * toward the target by w, so a stroke leaves a smooth rim rather than
 * a hard circle.
 *
 * Max-weight blending
 * -------------------
 * Within one stroke, each point is blended at most once, using the
 * strongest weight any stamp in the stroke contributed at that point.
 * This means a fast drag and a slow drag over the same point leave the
 * same result, and holding the mouse in place does not keep pushing
 * composition further toward the target.
 *
 * Airbrush mode
 * -------------
 * Depth airbrush is the exception: it is additive and NOT max-weight
 * gated. Repeated passes are supposed to accumulate.
 */
public class PaintBrush {

    private final ElvLoader loader;

    private float radius;
    private float depth;
    private float sand;
    private float silt;
    private float clay;

    private boolean randomVariationEnabled;
    private boolean slopeAwareDepthEnabled;
    private boolean slopeAwareCompositionEnabled;
    private boolean blendEnabled;
    private boolean depthAccumulateEnabled;

    private float variation;
    private float strength;

    /** 0 = hard disc, 1 = full smoothstep. The base brush defaults to 0 (constant effect). */
    private float falloff = 0f;

    public static final float MIN_RADIUS = 1f;
    public static final float MAX_RADIUS = 100f;

    // Per-stroke accumulators.
    private final Set<Integer> allPointsTouchedThisStroke = new HashSet<>();
    private final Map<Integer, float[]> snapShot = new HashMap<>();
    private final Map<Integer, Float> maxWeightThisStroke = new HashMap<>();

    public PaintBrush(ElvLoader loader) {
        this.loader = loader;

        this.radius = 5f;
        this.depth = 0.5f;
        this.sand = 0.6f;
        this.silt = 0.3f;
        this.clay = 0.1f;
        this.variation = 0.10f;
        this.strength = 1.0f;
        this.falloff = 0.0f;

        this.randomVariationEnabled = false;
        this.slopeAwareCompositionEnabled = false;
        this.slopeAwareDepthEnabled = false;
        this.blendEnabled = false;
        this.depthAccumulateEnabled = false;
    }

    public void beginStroke() {
        allPointsTouchedThisStroke.clear();
        snapShot.clear();
        maxWeightThisStroke.clear();
    }

    public TerrainEdit endStroke() {
        TerrainEdit edit = new TerrainEdit();
        for (int index : allPointsTouchedThisStroke) {
            float[] before = snapShot.get(index);
            float[] after = new float[]{
                loader.heights[index],
                loader.soilDepth[index],
                loader.sand[index],
                loader.silt[index],
                loader.clay[index]
            };
            edit.recordPoint(index, before, after);
        }
        allPointsTouchedThisStroke.clear();
        snapShot.clear();
        maxWeightThisStroke.clear();
        return edit;
    }

    /** Smoothstep falloff weight for a point at the given distance. */
    private float falloffWeight(float distance) {
        if (falloff <= 0f) return 1f;
        float t = 1f - distance / radius;
        if (t < 0f) t = 0f;
        float smooth = t * t * (3f - 2f * t);
        return 1f - falloff + falloff * smooth;
    }

    public void paintAt(int centerX, int centerZ) {
        int radiusBounds = (int) Math.ceil(radius);
        float squaredRadius = radius * radius;

        int startX = Math.max(0, centerX - radiusBounds);
        int endX   = Math.min(loader.dimX - 1, centerX + radiusBounds);
        int startZ = Math.max(0, centerZ - radiusBounds);
        int endZ   = Math.min(loader.dimY - 1, centerZ + radiusBounds);

        for (int z = startZ; z <= endZ; z++) {
            for (int x = startX; x <= endX; x++) {
                int deltaX = x - centerX;
                int deltaZ = z - centerZ;
                float squaredDistance = (deltaX * deltaX) + (deltaZ * deltaZ);

                if (squaredDistance > squaredRadius) continue;

                int index = z * loader.dimX + x;

                // Snapshot the pre-stroke state of this point, once.
                if (!snapShot.containsKey(index)) {
                    snapShot.put(index, new float[]{
                        loader.heights[index],
                        loader.soilDepth[index],
                        loader.sand[index],
                        loader.silt[index],
                        loader.clay[index]
                    });
                }
                allPointsTouchedThisStroke.add(index);

                float distance = (float) Math.sqrt(squaredDistance);
                float w = falloffWeight(distance);
                float prevMax = maxWeightThisStroke.getOrDefault(index, 0f);
                boolean newPeak = w > prevMax;

                // ---- Target values, with optional model adjustments ----
                float targetDepth = depth;
                float targetSand = sand;
                float targetSilt = silt;
                float targetClay = clay;

                if (slopeAwareCompositionEnabled) {
                    float[] adj = SoilCompositionModel.adjustCompositionForTerrain(
                        loader.smoothHeights, x, z,
                        loader.dimX, loader.dimY, loader.gridSpacing,
                        targetSand, targetSilt, targetClay);
                    targetSand = adj[0];
                    targetSilt = adj[1];
                    targetClay = adj[2];
                }

                if (randomVariationEnabled) {
                    targetDepth = Math.max(0f, randomVariation(targetDepth));
                    float[] rand = getRandomComposition(targetSand, targetSilt, targetClay);
                    targetSand = rand[0];
                    targetSilt = rand[1];
                    targetClay = rand[2];
                }

                if (slopeAwareDepthEnabled) {
                    float depthFactor = SoilDepthModel.calculateDepthFactor(
                        loader.heights, x, z,
                        loader.dimX, loader.dimY, loader.gridSpacing);
                    targetDepth *= depthFactor;
                }

                // Keep the target within the legal soil-depth range for this point.
                targetDepth = Math.max(0f, Math.min(targetDepth, loader.maxDepth[index]));

                // ---- Depth ----
                if (depthAccumulateEnabled) {
                    // Airbrush: additive, uses w directly, no max gate.
                    float deposit = targetDepth * strength * w;
                    float cap = loader.maxDepth[index];

                    loader.soilDepth[index] = Math.max(0f, Math.min(loader.soilDepth[index] + deposit, cap));
                } else if (newPeak) {
                    // Set mode with falloff. Blend pre-stroke depth
                    // toward target by w. Max-weight gate ensures the
                    // strongest stamp in the stroke wins.
                    float baseDepth = snapShot.get(index)[1];
                    float newDepth = baseDepth + (targetDepth - baseDepth) * w;
                    loader.soilDepth[index] = Math.max(0.0f, Math.min(newDepth, loader.maxDepth[index]));
                }

                // ---- Composition ----
                float currentTotal =
                    loader.sand[index] + loader.silt[index] + loader.clay[index];

                if (currentTotal <= 0f) {
                    // Unpainted. Nothing to blend from, so write target.
                    loader.sand[index] = targetSand;
                    loader.silt[index] = targetSilt;
                    loader.clay[index] = targetClay;
                    if (newPeak) maxWeightThisStroke.put(index, w);
                } else if (newPeak) {
                    float[] snap = snapShot.get(index);
                    float baseSand = snap[2];
                    float baseSilt = snap[3];
                    float baseClay = snap[4];

                    // In set mode the falloff weight is the full blend
                    // factor. In blend mode, scale it by strength so
                    // both knobs are meaningful at the same time.
                    float factor = blendEnabled ? strength * w : w;

                    float newSand = baseSand + (targetSand - baseSand) * factor;
                    float newSilt = baseSilt + (targetSilt - baseSilt) * factor;
                    float newClay = baseClay + (targetClay - baseClay) * factor;

                    float newTotal = newSand + newSilt + newClay;
                    if (newTotal > 0f) {
                        loader.sand[index] = newSand / newTotal;
                        loader.silt[index] = newSilt / newTotal;
                        loader.clay[index] = newClay / newTotal;
                    }
                    maxWeightThisStroke.put(index, w);
                }
                // If !newPeak, this point was already updated in this
                // stroke at a stronger weight. Skip.
            }
        }
    }

    public void setRadius(float radius) {
        if (!Float.isFinite(radius))
            throw new IllegalArgumentException("Brush radius must be a finite number.");
        if (radius < MIN_RADIUS || radius > MAX_RADIUS)
            throw new IllegalArgumentException("Brush radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS + ".");
        this.radius = radius;
    }

    public void setDepth(float depth) {
        if (depth >= 0) this.depth = depth;
    }

    public void setComposition(float sand, float silt, float clay) {
        float sum = sand + silt + clay;
        boolean inRange = sand >= 0f && sand <= 1f
                       && silt >= 0f && silt <= 1f
                       && clay >= 0f && clay <= 1f;
        if (inRange && Math.abs(sum - 1.0f) < 0.0001f) {
            this.sand = sand;
            this.silt = silt;
            this.clay = clay;
        }
    }

    public float getRadius() { return radius; }
    public float getDepth()  { return depth; }
    public float getSand()   { return sand; }
    public float getSilt()   { return silt; }
    public float getClay()   { return clay; }

    public void setRandomVariationEnabled(boolean enabled) { this.randomVariationEnabled = enabled; }
    public void setSlopeAwareDepthEnabled(boolean enabled) { this.slopeAwareDepthEnabled = enabled; }
    public boolean isSlopeAwareDepthEnabled() { return slopeAwareDepthEnabled; }
    public void setSlopeAwareCompositionEnabled(boolean enabled) { this.slopeAwareCompositionEnabled = enabled; }
    public boolean isSlopeAwareCompositionEnabled() { return slopeAwareCompositionEnabled; }
    public boolean isRandomVariationEnabled() { return randomVariationEnabled; }

    public void setDepthAccumulateEnabled(boolean enabled) { this.depthAccumulateEnabled = enabled; }
    public boolean isDepthAccumulateEnabled() { return depthAccumulateEnabled; }

    public void setVariation(float variation) {
        if (variation >= 0f && variation <= 1f) this.variation = variation;
    }
    public float getVariation() { return variation; }

    public void setBlendEnabled(boolean enabled) { this.blendEnabled = enabled; }
    public boolean isBlendEnabled() { return blendEnabled; }

    public void setStrength(float strength) {
        if (strength >= 0f && strength <= 1f) this.strength = strength;
    }
    public float getStrength() { return strength; }

    public void setFalloff(float falloff) {
        if (falloff >= 0f && falloff <= 1f) this.falloff = falloff;
    }
    public float getFalloff() { return falloff; }

    private float randomVariation(float baseValue) {
        float maxChange = baseValue * variation;
        float randomOffset = (float) ((Math.random() * 2.0) - 1.0) * maxChange;
        return baseValue + randomOffset;
    }

    private float[] getRandomComposition(float baseSand, float baseSilt, float baseClay) {
        float randomSand = Math.max(0f, randomVariation(baseSand));
        float randomSilt = Math.max(0f, randomVariation(baseSilt));
        float randomClay = Math.max(0f, randomVariation(baseClay));
        float total = randomSand + randomSilt + randomClay;
        if (total <= 0f) return new float[]{baseSand, baseSilt, baseClay};
        return new float[]{randomSand / total, randomSilt / total, randomClay / total};
    }

    public void paintStroke(int startX, int startZ, int endX, int endZ) {
        int deltaX = endX - startX;
        int deltaZ = endZ - startZ;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float stampSpacing = Math.max(1f, radius * 0.25f);
        int steps = Math.max(1, (int) Math.ceil(distance / stampSpacing));

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int x = Math.round(startX + deltaX * t);
            int z = Math.round(startZ + deltaZ * t);
            paintAt(x, z);
        }
    }
}

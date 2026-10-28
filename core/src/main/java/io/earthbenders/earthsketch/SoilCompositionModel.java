package io.earthbenders.earthsketch;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class SoilCompositionModel {
    private static final float baseSand = 0.33f;
    private static final float baseSilt = 0.33f; //never used
    private static final float baseClay = 0.34f;
    private static final float scalingFactor = 50f;

    // returns {sand, silt, clay} proportions per point dependent on the curvature 
    public static float [] calculateSoilComposition(float [] heights, int X, int Z, int dimX, int dimY, float gridSpacing){
        //going to reuse slopeNorm and duplicate curvature calculation
        //refactor needed for curvature and slopeNorm calculation to consolidate into one math class
        Vector3 normal = TerrainMeshBuilder.slopeNorm(heights, X, Z, dimX, dimY, gridSpacing);
        int xLeft = Math.max(X-1, 0);
        int xRight = Math.min(X+1, dimX-1);
        float hL = heights[Z * dimX + xLeft];
        float hR = heights[Z * dimX + xRight];
        float hC = heights[Z * dimX + X];

        float slopeLeft = (hC - hL)/gridSpacing;
        float slopeRight = (hR - hC)/gridSpacing;
        float curvature = (slopeRight-slopeLeft)/gridSpacing;

        float slopeNorm = normal.y;

        float slopeSteepness = 1f - slopeNorm; //0 is perfectly flat, 1 is extereme steepness
        float sandShift = slopeSteepness * 0.3f; //in studies it is consistently shown that sand is a higher percentage on slopes by 30%

        float curvatureFactor = MathUtils.clamp(curvature*scalingFactor, -1f, 1f);
        float clayShift = Math.max(curvatureFactor, 0f); //concave and flat favours clay

        float sand = baseSand  + sandShift - clayShift * 0.5f;
        float clay = baseClay  + clayShift - sandShift * 0.5f;
        float silt = 1f - sand - clay; //whatever is left

        sand = MathUtils.clamp(sand, 0f, 1f);
        clay = MathUtils.clamp(clay, 0f, 1f);
        silt = MathUtils.clamp(silt, 0f, 1f);

        float total = sand + clay + silt;
        sand /= total;
        clay /= total;
        silt /= total;

        return new float []{sand, silt, clay};


    }

    public static float[] adjustCompositionForTerrain(
        float[] heights,
        int X,
        int Z,
        int dimX,
        int dimY,
        float gridSpacing,
        float userSand,
        float userSilt,
        float userClay
    ) {
        // Get Tana's terrain-dependent composition.
        float[] terrainComposition =
            calculateSoilComposition(
                heights,
                X,
                Z,
                dimX,
                dimY,
                gridSpacing
            );

        float terrainSand = terrainComposition[0];
        float terrainSilt = terrainComposition[1];
        float terrainClay = terrainComposition[2];

        // Determine how the terrain changed each component
        // relative to Tana's original baseline.
        float sandWeight = terrainSand / baseSand;
        float siltWeight = terrainSilt / baseSilt;
        float clayWeight = terrainClay / baseClay;

        // Apply the same relative terrain effect to the
        // composition selected by the user.
        float adjustedSand = userSand * sandWeight;
        float adjustedSilt = userSilt * siltWeight;
        float adjustedClay = userClay * clayWeight;

        // Re-normalise so the composition still sums to 1.
        float total =
            adjustedSand
            + adjustedSilt
            + adjustedClay;

        if (total <= 0f) {
            return new float[] {
                userSand,
                userSilt,
                userClay
            };
        }

        adjustedSand /= total;
        adjustedSilt /= total;
        adjustedClay /= total;

        return new float[] {
            adjustedSand,
            adjustedSilt,
            adjustedClay
        };
    }
}

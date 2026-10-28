package io.earthbenders.earthsketch;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;

public class SoilDepthModel {
    private static final float scalingFactor = 50f;

    public static float calculateMaxDepth(float [] heights, int X, int Z, int dimX, int dimY, float gridSpacing){
        //slope component reused from prior method.
        //curvature component, a new calculation; a tell of how the max depth of soil will vary along terrain
        Vector3 normal = TerrainMeshBuilder.slopeNorm(heights, X, Z, dimX, dimY, gridSpacing);
        int xLeft = Math.max(X-1, 0);
        int xRight = Math.min(X+1, dimX-1);
        float hL = heights[Z * dimX + xLeft];
        float hR = heights[Z * dimX + xRight];
        float hC = heights[Z * dimX + X];

        float slopeLeft = (hC - hL)/gridSpacing;
        float slopeRight = (hR - hC)/gridSpacing;
        float curvature = (slopeRight-slopeLeft)/gridSpacing;

        float slopeNorm = MathUtils.clamp(normal.y, 0f, 1f);
        float baseMaxDepth = 2.0f; // max depth for a flat piece of ground, this is based on scientific findings for what a deep soil would be on flat ground https://pmc.ncbi.nlm.nih.gov/articles/PMC12539739/
        float concaveBonus = 1.5f; // the additional soil allowable in a concave landscape

        float curvatureFactor = MathUtils.clamp(curvature * scalingFactor, -1f, 1f);
        float maxDepth = baseMaxDepth * slopeNorm + concaveBonus * Math.max(curvatureFactor, 0);

        return Math.max(0f, maxDepth);

    }

    public static float calculateDepthFactor(
        float[] heights,
        int X,
        int Z,
        int dimX,
        int dimY,
        float gridSpacing
    ) {
        // Reuse the existing slope calculation.
        Vector3 normal =
            TerrainMeshBuilder.slopeNorm(
                heights,
                X,
                Z,
                dimX,
                dimY,
                gridSpacing
            );

        // Reuse the existing curvature calculation.
        int xLeft = Math.max(X - 1, 0);
        int xRight = Math.min(X + 1, dimX - 1);

        float hL = heights[Z * dimX + xLeft];
        float hR = heights[Z * dimX + xRight];
        float hC = heights[Z * dimX + X];

        float slopeLeft =
            (hC - hL) / gridSpacing;

        float slopeRight =
            (hR - hC) / gridSpacing;

        float curvature =
            (slopeRight - slopeLeft) / gridSpacing;

        /*
        * normal.y is approximately:
        * 1 -> flat
        * 0 -> very steep
        */
        float slopeFactor =
            MathUtils.clamp(normal.y, 0f, 1f);

        /*
        * Preserve Tana's curvature idea:
        * positive curvature indicates concavity in the
        * current model and allows more soil retention.
        */
        float curvatureFactor =
            MathUtils.clamp(
                curvature * scalingFactor,
                -1f,
                1f
            );

        /*
        * Keep some soil depth even on very steep terrain.
        */
        final float MIN_DEPTH_FACTOR = 0.20f;

        /*
        * Slope provides the main reduction in depth.
        */
        float depthFactor =
            MathUtils.lerp(
                MIN_DEPTH_FACTOR,
                1.0f,
                slopeFactor
            );

        /*
        * Concave terrain receives a modest relative increase.
        *
        * This is deliberately a multiplier adjustment rather than
        * adding an absolute number of metres.
        */
        final float MAX_CONCAVE_BONUS = 0.25f;

        float concaveBonus =
            Math.max(curvatureFactor, 0f)
                * MAX_CONCAVE_BONUS;

        depthFactor += concaveBonus;

        return MathUtils.clamp(
            depthFactor,
            MIN_DEPTH_FACTOR,
            1.0f
        );
    }
    
}

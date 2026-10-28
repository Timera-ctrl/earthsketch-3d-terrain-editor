package io.earthbenders.earthsketch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SoilDepthModelTest {
    @Test
    public void testCalculateMaxDepth_FlatTerrain() {
        float[] heights = {1, 1, 1, 1, 1, 1, 1, 1, 1}; // completely flat
        float maxDepth = SoilDepthModel.calculateMaxDepth(heights, 1, 1, 3, 3, 1.0f);

        assertEquals(2.0f, maxDepth, 0.01f); // flat, non-concave ground should hit exactly baseMaxDepth
    }

    @Test
    public void testCalculateMaxDepth_SteepTerrain() {
        float[] heights = {0, 0, 0, 1, 5, 1, 2, 2, 2}; // steep
        float maxDepth = SoilDepthModel.calculateMaxDepth(heights, 1, 1, 3, 3, 1.0f);

        assertTrue(maxDepth < 2.0f); // steep terrain should allow LESS than the flat-ground maximum
    }

    @Test
    public void testSteepTerrainGetsLowerDepthFactorThanFlatTerrain() {

        int dimX = 5;
        int dimY = 5;
        float spacing = 1f;

        float[] flat =
            new float[dimX * dimY];

        float[] steep =
            new float[dimX * dimY];

        for (int z = 0; z < dimY; z++) {
            for (int x = 0; x < dimX; x++) {
                steep[z * dimX + x] = x * 5f;
            }
        }

        float flatFactor =
            SoilDepthModel.calculateDepthFactor(
                flat,
                2,
                2,
                dimX,
                dimY,
                spacing
            );

        float steepFactor =
            SoilDepthModel.calculateDepthFactor(
                steep,
                2,
                2,
                dimX,
                dimY,
                spacing
            );

        assertTrue(
            steepFactor < flatFactor
        );
    }

    @Test
    public void testConcaveTerrainCanReceiveDepthBonus() {

        int dimX = 5;
        int dimY = 5;
        float spacing = 1f;

        float[] flat =
            new float[dimX * dimY];

        float[] concave =
            new float[dimX * dimY];

        for (int z = 0; z < dimY; z++) {
            concave[z * dimX + 1] = 2f;
            concave[z * dimX + 2] = 0f;
            concave[z * dimX + 3] = 2f;
        }

        float flatFactor =
            SoilDepthModel.calculateDepthFactor(
                flat,
                2,
                2,
                dimX,
                dimY,
                spacing
            );

        float concaveFactor =
            SoilDepthModel.calculateDepthFactor(
                concave,
                2,
                2,
                dimX,
                dimY,
                spacing
            );

        assertTrue(
            concaveFactor >= flatFactor
        );
    }
}

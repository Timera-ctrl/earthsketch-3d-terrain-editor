package io.earthbenders.earthsketch;

import org.junit.Test;
import static org.junit.Assert.*;

public class SoilCompositionModelTest {

    private static final float SUM_TOLERANCE = 0.001f;

    @Test
    public void testCalculateComposition_SumsToOne_FlatTerrain() {
        float[] heights = {1, 1, 1, 1, 1, 1, 1, 1, 1}; // flat
        float[] composition = SoilCompositionModel.calculateSoilComposition(heights, 1, 1, 3, 3, 1.0f);

        assertEquals(3, composition.length);
        float sum = composition[0] + composition[1] + composition[2];
        assertEquals(1.0f, sum, SUM_TOLERANCE);
    }

    @Test
    public void testCalculateComposition_SteepPeak_FavorsSand() {
        float[] heights = {0, 0, 0, 1, 5, 1, 2, 2, 2}; // same steep peak dataset as slopeNorm/depth tests
        float[] flatHeights = {1, 1, 1, 1, 1, 1, 1, 1, 1};

        float[] steepComposition = SoilCompositionModel.calculateSoilComposition(heights, 1, 1, 3, 3, 1.0f);
        float[] flatComposition = SoilCompositionModel.calculateSoilComposition(flatHeights, 1, 1, 3, 3, 1.0f);

        // steep terrain should have MORE sand than flat terrain
        assertTrue(steepComposition[0] > flatComposition[0]);
    }

    @Test
    public void testCalculateComposition_Valley_FavorsClay() {
        // a concave valley: center lower than all neighbors
        float[] heights = {5, 5, 5, 5, 0, 5, 5, 5, 5};
        float[] flatHeights = {1, 1, 1, 1, 1, 1, 1, 1, 1};

        float[] valleyComposition = SoilCompositionModel.calculateSoilComposition(heights, 1, 1, 3, 3, 1.0f);
        float[] flatComposition = SoilCompositionModel.calculateSoilComposition(flatHeights, 1, 1, 3, 3, 1.0f);

        // valley terrain should have MORE clay than flat terrain
        assertTrue(valleyComposition[2] > flatComposition[2]);
    }

    @Test
    public void testCalculateComposition_AllValuesInValidRange() {
        float[] heights = {0, 0, 0, 1, 5, 1, 2, 2, 2};
        float[] composition = SoilCompositionModel.calculateSoilComposition(heights, 1, 1, 3, 3, 1.0f);

        for (float value : composition) {
            assertTrue(value >= 0f && value <= 1f);
        }
    }

    @Test
    public void testCalculateComposition_SumsToOne_SteepPeak() {
        float[] heights = {0, 0, 0, 1, 5, 1, 2, 2, 2};
        float[] composition = SoilCompositionModel.calculateSoilComposition(heights, 1, 1, 3, 3, 1.0f);

        float sum = composition[0] + composition[1] + composition[2];
        assertEquals(1.0f, sum, SUM_TOLERANCE);
    }

    @Test
    public void testSlopeAwareCompositionDiffersBetweenFlatAndSteepTerrain() {

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

        float[] flatComposition =
            SoilCompositionModel.adjustCompositionForTerrain(
                flat,
                2,
                2,
                dimX,
                dimY,
                spacing,
                0.6f,
                0.3f,
                0.1f
            );

        float[] steepComposition =
            SoilCompositionModel.adjustCompositionForTerrain(
                steep,
                2,
                2,
                dimX,
                dimY,
                spacing,
                0.6f,
                0.3f,
                0.1f
            );

        boolean different =
            Math.abs(
                flatComposition[0]
                    - steepComposition[0]
            ) > 0.0001f
            ||
            Math.abs(
                flatComposition[1]
                    - steepComposition[1]
            ) > 0.0001f
            ||
            Math.abs(
                flatComposition[2]
                    - steepComposition[2]
            ) > 0.0001f;

        assertTrue(different);

        assertEquals(
            1.0f,
            flatComposition[0]
                + flatComposition[1]
                + flatComposition[2],
            0.001f
        );

        assertEquals(
            1.0f,
            steepComposition[0]
                + steepComposition[1]
                + steepComposition[2],
            0.001f
        );
    }

    @Test
    public void testSlopeAwareCompositionIsSafeAtTerrainCorner() {

        int dimX = 5;
        int dimY = 5;
        float spacing = 1f;

        float[] heights =
            new float[dimX * dimY];

        for (int i = 0; i < heights.length; i++) {
            heights[i] = i;
        }

        float[] result =
            SoilCompositionModel.adjustCompositionForTerrain(
                heights,
                0,
                0,
                dimX,
                dimY,
                spacing,
                0.6f,
                0.3f,
                0.1f
            );

        assertTrue(
            Float.isFinite(result[0])
        );

        assertTrue(
            Float.isFinite(result[1])
        );

        assertTrue(
            Float.isFinite(result[2])
        );

        assertTrue(
            result[0] >= 0f &&
            result[0] <= 1f
        );

        assertTrue(
            result[1] >= 0f &&
            result[1] <= 1f
        );

        assertTrue(
            result[2] >= 0f &&
            result[2] <= 1f
        );

        assertEquals(
            1.0f,
            result[0]
                + result[1]
                + result[2],
            0.001f
        );
    }
}
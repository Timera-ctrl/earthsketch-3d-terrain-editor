package io.earthbenders.earthsketch;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerrainBrushTest {

    private ElvLoader loader;
    private TerrainBrush brush;

    @Before
    public void setUp() {
        loader = new ElvLoader();
        loader.dimX = 5;
        loader.dimY = 5;
        loader.gridSpacing = 1.0f;
        loader.heights = new float[25];
        loader.soilDepth = new float[25];
        loader.sand = new float[25];
        loader.silt = new float[25];
        loader.clay = new float[25];

        for (int i = 0; i < 25; i++) {
            loader.heights[i] = 5.0f;   // flat starting terrain
            loader.soilDepth[i] = 1.0f; // enough soil depth to dig into
        }

        brush = new TerrainBrush(loader);
    }

    // ===== DIG mode =====

    @Test
    public void testDig_LowersHeightAtCenter() {
        brush.setMode(TerrainBrush.Mode.DIG);
        brush.setRadius(1f);
        brush.setStrength(0.5f);

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        assertTrue(loader.heights[center] < 5.0f);
    }

    @Test
    public void testDig_AlsoReducesSoilDepth() {
        brush.setMode(TerrainBrush.Mode.DIG);
        brush.setRadius(1f);
        brush.setStrength(0.5f);

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        assertTrue(loader.soilDepth[center] < 1.0f);
    }

    @Test
    public void testDig_SoilDepthNeverGoesNegative() {
        brush.setMode(TerrainBrush.Mode.DIG);
        brush.setRadius(1f);
        brush.setStrength(10f); // deliberately huge, to try to force negative soilDepth

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        assertTrue(loader.soilDepth[center] >= 0f);
    }

    // ===== FILL mode =====

    @Test
    public void testFill_RaisesHeightAtCenter() {
        brush.setMode(TerrainBrush.Mode.FILL);
        brush.setRadius(1f);
        brush.setStrength(0.5f);

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        assertTrue(loader.heights[center] > 5.0f);
    }

    // ===== FLATTEN mode =====

    @Test
    public void testFlatten_MovesTowardLocalAverage() {
        // create an uneven bump so flatten has something to do
        loader.heights[2 * loader.dimX + 2] = 20f; // center much higher than surroundings 

        brush.setMode(TerrainBrush.Mode.FLATTEN);
        brush.setRadius(2f);
        brush.setStrength(1f);
        brush.setFalloff(0f); // uniform weighting, easier to reason about

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        // center should move down toward the average of its neighborhood, not stay at 20
        assertTrue(loader.heights[center] < 20f);
    }

    // ===== LEVEL mode =====

    @Test
    public void testLevel_MovesTowardTargetHeight() {
        brush.setMode(TerrainBrush.Mode.LEVEL);
        brush.setTargetHeight(10f);
        brush.setRadius(1f);
        brush.setStrength(1f);
        brush.setFalloff(0f);

        brush.sculptAt(2, 2);

        int center = 2 * loader.dimX + 2;
        assertTrue(loader.heights[center] > 5.0f); // moved toward 10, up from starting 5
    }

    // ===== SMOOTH mode =====

    @Test
    public void testSmooth_ReducesSpike() {
        int center = 2 * loader.dimX + 2;
        loader.heights[center] = 50f; // a sharp spike among flat neighbors

        brush.setMode(TerrainBrush.Mode.SMOOTH);
        brush.setRadius(1f);
        brush.setStrength(1f);
        brush.setFalloff(0f);

        brush.sculptAt(2, 2);

        assertTrue(loader.heights[center] < 50f); // spike should be reduced toward neighbor average
    }

    // ===== Undo/redo integration (via TerrainEdit) =====

    @Test
    public void testBeginEndStroke_ProducesEditThatUndoesCorrectly() {
        int center = 2 * loader.dimX + 2;
        float originalHeight = loader.heights[center];

        brush.setMode(TerrainBrush.Mode.FILL);
        brush.setRadius(1f);
        brush.setStrength(2f);

        brush.beginStroke();
        brush.sculptAt(2, 2);
        TerrainEdit edit = brush.endStroke();

        assertFalse(edit.isEmpty());
        assertTrue(loader.heights[center] > originalHeight); // sculpt applied

        edit.applyBefore(loader);
        assertEquals(originalHeight, loader.heights[center], 0.001f); // undo restores it
    }

    @Test
    public void testEndStroke_WithNoSculpting_ReturnsEmptyEdit() {
        brush.beginStroke();
        // no sculptAt() calls at all
        TerrainEdit edit = brush.endStroke();

        assertTrue(edit.isEmpty());
    }

    // ===== Radius/strength validation =====

    @Test
    public void testSetRadius_RejectsNonPositiveValues() {
        brush.setRadius(5f);
        brush.setRadius(-1f); // should be rejected
        assertEquals(5f, brush.getRadius(), 0.001f);

        brush.setRadius(0f); // should also be rejected
        assertEquals(5f, brush.getRadius(), 0.001f);
    }

    @Test
    public void testSetFalloff_ClampsToZeroOneRange() {
        brush.setFalloff(2f);
        assertEquals(1f, brush.getFalloff(), 0.001f);

        brush.setFalloff(-1f);
        assertEquals(0f, brush.getFalloff(), 0.001f);
    }
}

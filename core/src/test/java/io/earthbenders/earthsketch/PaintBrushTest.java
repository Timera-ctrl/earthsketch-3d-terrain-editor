package io.earthbenders.earthsketch;

import org.junit.Before;
import org.junit.Test;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class PaintBrushTest {

    private ElvLoader loader;
    private PaintBrush brush;

    @Rule
    public TemporaryFolder tempFolder =
        new TemporaryFolder();

    @Before
    public void setUp() {
        loader = TestTerrainData.flatMedium();

        // Start soil empty so depth tests are unambiguous.
        for (int i = 0; i < loader.soilDepth.length; i++) {
            loader.soilDepth[i] = 0f;
        }
        // maxDepth from the fixture is 2.0f - enough headroom for the
        // airbrush tests below.

        brush = new PaintBrush(loader);
    }

    // ==================================================================
    // Initial state
    // ==================================================================

    @Test
    public void testBrushInitialization() {

        assertEquals(
            5.0f,
            brush.getRadius(),
            0.001f
        );

        assertEquals(
            0.5f,
            brush.getDepth(),
            0.001f
        );

        assertEquals(
            0.6f,
            brush.getSand(),
            0.001f
        );

        assertEquals(
            0.3f,
            brush.getSilt(),
            0.001f
        );

        assertEquals(
            0.1f,
            brush.getClay(),
            0.001f
        );

        assertFalse(
            brush.isDepthAccumulateEnabled()
        );

        assertEquals(
            0.0f,
            brush.getFalloff(),
            0.001f
        );
    }

    // ==================================================================
    // Set mode (default), without falloff effect
    // ==================================================================

    @Test
    public void testPaintAtCentreSetsDepth() {
        brush.setFalloff(0f);
        brush.paintAt(5, 5);
        assertEquals(0.5f, loader.soilDepth[5 * 10 + 5], 0.001f);
    }

    @Test
    public void testSetModeRepeatedPassesDoNotAccumulate() {
        brush.setFalloff(0f);
        brush.paintAt(5, 5);
        brush.paintAt(5, 5);
        brush.paintAt(5, 5);
        assertEquals(0.5f, loader.soilDepth[5 * 10 + 5], 0.001f);
    }

    @Test
    public void testPaintAtCorner() {
        brush.setFalloff(0f);
        brush.paintAt(0, 0);
        assertEquals(0.5f, loader.soilDepth[0], 0.001f);
    }

    @Test
    public void testPaintWithLargeRadius() {
        brush.setFalloff(0f);
        brush.setRadius(10.0f);
        brush.paintAt(5, 5);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.5f, loader.soilDepth[i], 0.001f);
        }
    }

    // ==================================================================
    // Falloff - setters and validation
    // ==================================================================

    @Test
    public void testSetFalloff_Valid() {
        brush.setFalloff(0.5f);
        assertEquals(0.5f, brush.getFalloff(), 0.001f);
    }

    @Test
    public void testSetFalloff_ZeroIsValid() {
        brush.setFalloff(0f);
        assertEquals(0f, brush.getFalloff(), 0.001f);
    }

    @Test
    public void testSetFalloff_OneIsValid() {
        brush.setFalloff(1f);
        assertEquals(1f, brush.getFalloff(), 0.001f);
    }

    @Test
    public void testSetFalloff_BelowRangeIgnored() {
        brush.setFalloff(0.5f);
        brush.setFalloff(-0.2f);
        assertEquals(0.5f, brush.getFalloff(), 0.001f);
    }

    @Test
    public void testSetFalloff_AboveRangeIgnored() {
        brush.setFalloff(0.5f);
        brush.setFalloff(1.7f);
        assertEquals(0.5f, brush.getFalloff(), 0.001f);
    }

    // ==================================================================
    // Falloff - effect on composition and depth
    // ==================================================================

    @Test
    public void testFalloffZeroGivesHardEdge() {
        // With falloff = 0, every point inside the disc gets the full
        // target composition.
        brush.setFalloff(0f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        // Centre
        assertEquals(0.9f, loader.sand[5 * 10 + 5], 0.001f);
        // 3 cells from centre, still inside radius 5
        assertEquals(0.9f, loader.sand[5 * 10 + 2], 0.001f);
        assertEquals(0.9f, loader.sand[2 * 10 + 5], 0.001f);
    }

    @Test
    public void testFalloffOneCentreReachesFullTarget() {
        // With falloff = 1, the point at the brush centre has weight 1,
        // so it still receives the full target.
        brush.setFalloff(1f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        assertEquals(0.9f, loader.sand[5 * 10 + 5], 0.001f);
    }

    @Test
    public void testFalloffOneEdgeIsBetweenBaseAndTarget() {
        // Base composition at every point is 0.33/0.33/0.34.
        // Target is 0.9/0.05/0.05.
        // At the edge of the disc the weight is small but nonzero, so
        // the resulting sand should be strictly between base and target.
        brush.setFalloff(1f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        // Point 4 cells from centre (brush radius is 5).
        float edgeSand = loader.sand[5 * 10 + 1];

        assertTrue("Edge sand should be above base", edgeSand > 0.33f);
        assertTrue("Edge sand should be below target", edgeSand < 0.9f);
    }

    @Test
    public void testFalloffCentreCloserToTargetThanEdge() {
        brush.setFalloff(1f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        float centreSand = loader.sand[5 * 10 + 5];
        float edgeSand   = loader.sand[5 * 10 + 1];

        assertTrue("Centre should be closer to target than edge",
            centreSand > edgeSand);
    }

    @Test
    public void testFalloffAffectsDepthInSetMode() {
        // Depth is also blended by falloff weight in Set mode.
        brush.setFalloff(1f);
        brush.setDepth(1.0f);

        brush.paintAt(5, 5);

        float centreDepth = loader.soilDepth[5 * 10 + 5];
        float edgeDepth   = loader.soilDepth[5 * 10 + 1];

        assertEquals(1.0f, centreDepth, 0.001f);
        assertTrue("Edge depth should be less than centre depth",
            edgeDepth < centreDepth);
        assertTrue("Edge depth should be greater than zero",
            edgeDepth > 0f);
    }

    @Test
    public void testFalloffOutsideDiscUnaffected() {
        brush.setFalloff(1f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        // Point at distance 6 from centre: outside radius 5.
        int idx = 5 * 10 + 0; // actually 5 cells to the left, at (0,5)
        // Clamped grid is 0..9, so a point that's clearly outside the disc
        // is (0, 5), 5 cells away, which is ON the boundary. Use (0, 0)
        // instead - distance sqrt(50) ~ 7, well outside.
        int outsideIdx = 0;
        assertEquals(0.33f, loader.sand[outsideIdx], 0.001f);
    }

    @Test
    public void testFalloffHalfGivesIntermediateBlend() {
        // With falloff = 0.5 the smoothstep is half-applied. At the
        // centre this gives weight 1 (since smoothstep(1) = 1), so
        // the centre still hits target. A point halfway through the
        // disc gives weight strictly between 0 and 1.
        brush.setFalloff(0.5f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.paintAt(5, 5);

        float midSand = loader.sand[5 * 10 + 3]; // 2 cells from centre
        assertTrue(midSand > 0.33f);
        assertTrue(midSand < 0.9f);
    }

    // ==================================================================
    // Max-weight gate
    // ==================================================================

    @Test
    public void testFalloffMaxWeightGateNoOverBlend() {
        // Painting the same spot twice within one stroke should not
        // push composition further toward target the second time.
        brush.setFalloff(1f);
        brush.setComposition(0.9f, 0.05f, 0.05f);

        brush.beginStroke();

        brush.paintAt(5, 5);
        float afterFirst = loader.sand[5 * 10 + 5];

        brush.paintAt(5, 5);
        float afterSecond = loader.sand[5 * 10 + 5];

        brush.endStroke();

        assertEquals("Repeated stamp should not over-blend",
            afterFirst, afterSecond, 0.0001f);
    }

    // ==================================================================
    // Airbrush mode
    // ==================================================================

    @Test
    public void testAirbrushSinglePassDepositsFraction() {
        brush.setFalloff(0f);
        brush.setDepthAccumulateEnabled(true);
        brush.setDepth(1.0f);
        brush.setStrength(0.1f);

        brush.paintAt(5, 5);

        assertEquals(0.1f, loader.soilDepth[5 * 10 + 5], 0.01f);
    }

    @Test
    public void testAirbrushAccumulates() {
        brush.setFalloff(0f);
        brush.setDepthAccumulateEnabled(true);
        brush.setDepth(1.0f);
        brush.setStrength(0.1f);

        int idx = 5 * 10 + 5;
        for (int i = 1; i <= 5; i++) {
            brush.paintAt(5, 5);
            assertEquals(i * 0.1f, loader.soilDepth[idx], 0.01f);
        }
    }

    @Test
    public void testAirbrushClampsAtMaxDepth() {
        brush.setFalloff(0f);
        brush.setDepthAccumulateEnabled(true);
        brush.setDepth(10.0f);
        brush.setStrength(1.0f);

        for (int i = 0; i < 20; i++) {
            brush.paintAt(5, 5);
        }

        assertTrue(loader.soilDepth[5 * 10 + 5] <= 2.0f + 0.001f);
    }

    @Test
    public void testAirbrushFalloffMakesCentreDeeperThanEdge() {
        brush.setRadius(5.0f);
        brush.setFalloff(1f);
        brush.setDepthAccumulateEnabled(true);
        brush.setDepth(1.0f);
        brush.setStrength(0.1f);

        brush.paintAt(5, 5);

        float centre = loader.soilDepth[5 * 10 + 5];
        float edge   = loader.soilDepth[5 * 10 + 8]; // 3 cells from centre

        assertTrue("Centre deposit should exceed edge deposit",
            centre > edge);
    }

    @Test
    public void testAirbrushDisabledIgnoresStrength() {
        brush.setFalloff(0f);
        brush.setDepthAccumulateEnabled(false);
        brush.setDepth(0.8f);
        brush.setStrength(0.2f);

        brush.paintAt(5, 5);
        brush.paintAt(5, 5);
        brush.paintAt(5, 5);

        assertEquals(0.8f, loader.soilDepth[5 * 10 + 5], 0.001f);
    }

    @Test
    public void testAirbrushFlagRoundTrip() {
        assertFalse(brush.isDepthAccumulateEnabled());
        brush.setDepthAccumulateEnabled(true);
        assertTrue(brush.isDepthAccumulateEnabled());
        brush.setDepthAccumulateEnabled(false);
        assertFalse(brush.isDepthAccumulateEnabled());
    }

    // ==================================================================
    // Setters - validation
    // ==================================================================

    @Test
    public void testSetRadius_Valid() {
        brush.setRadius(15.0f);
        assertEquals(15.0f, brush.getRadius(), 0.001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRadius_TooSmall() {
        brush.setRadius(0.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRadius_TooLarge() {
        brush.setRadius(200f);
    }

    @Test
    public void testSetDepth_Valid() {
        brush.setDepth(2.5f);
        assertEquals(2.5f, brush.getDepth(), 0.001f);
    }

    @Test
    public void testSetDepth_NegativeIgnored() {
        brush.setDepth(-1.0f);
        assertEquals(0.5f, brush.getDepth(), 0.001f);
    }

    @Test
    public void testSetComposition_Valid() {
        brush.setComposition(0.5f, 0.3f, 0.2f);
        assertEquals(0.5f, brush.getSand(), 0.001f);
        assertEquals(0.3f, brush.getSilt(), 0.001f);
        assertEquals(0.2f, brush.getClay(), 0.001f);
    }

    @Test
    public void testSetComposition_SumNotOneRejected() {
        brush.setComposition(0.5f, 0.5f, 0.5f);
        assertEquals(0.6f, brush.getSand(), 0.001f);
    }

    @Test
    public void testSetComposition_OutOfRangeRejected() {
        brush.setComposition(1.5f, -0.3f, -0.2f);
        assertEquals(0.6f, brush.getSand(), 0.001f);
    }

    // ==================================================================
    // Strokes
    // ==================================================================

    @Test
    public void testPaintStrokeCoversLine() {
        brush.setFalloff(0f);
        brush.paintStroke(2, 2, 6, 6);
        assertEquals(0.5f, loader.soilDepth[2 * 10 + 2], 0.001f);
        assertEquals(0.5f, loader.soilDepth[4 * 10 + 4], 0.001f);
        assertEquals(0.5f, loader.soilDepth[6 * 10 + 6], 0.001f);
    }

    @Test
    public void testPaintStrokeDegenerate() {
        brush.setFalloff(0f);
        brush.paintStroke(5, 5, 5, 5);
        assertEquals(0.5f, loader.soilDepth[5 * 10 + 5], 0.001f);
    }

    @Test
    public void testPaintStrokeClampsToBounds() {
        brush.setFalloff(0f);
        brush.paintStroke(5, 5, 20, 20);
        assertEquals(0.5f, loader.soilDepth[9 * 10 + 9], 0.001f);
    }

    @Test
    public void testMultiplePaintsAtDifferentPoints() {
        brush.setFalloff(0f);
        brush.paintAt(3, 3);
        brush.paintAt(7, 7);
        assertEquals(0.5f, loader.soilDepth[3 * 10 + 3], 0.001f);
        assertEquals(0.5f, loader.soilDepth[7 * 10 + 7], 0.001f);
    }

    // ==================================================================
    // Overwrite semantics
    // ==================================================================

    @Test
    public void testPaintOverwritesComposition() {

        brush.setFalloff(0f);

        // First stroke uses the default composition: 0.6 / 0.3 / 0.1.
        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        int index = 5 * loader.dimX + 5;

        assertEquals(
            0.6f,
            loader.sand[index],
            0.001f
        );

        assertEquals(
            0.3f,
            loader.silt[index],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.clay[index],
            0.001f
        );

        // Start a NEW stroke with a different composition.
        brush.setComposition(
            0.1f,
            0.8f,
            0.1f
        );

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        assertEquals(
            0.1f,
            loader.sand[index],
            0.001f
        );

        assertEquals(
            0.8f,
            loader.silt[index],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.clay[index],
            0.001f
        );
    }

    // ==================================================================
    // Stroke tracking for undo
    // ==================================================================

    @Test
    public void testEndStrokeReturnsNonEmptyEdit() {
        brush.beginStroke();
        brush.paintAt(5, 5);
        TerrainEdit edit = brush.endStroke();

        assertNotNull(edit);
        assertFalse(edit.isEmpty());
    }

    @Test
    public void testEndStrokeWithNoPaintIsEmpty() {
        brush.beginStroke();
        TerrainEdit edit = brush.endStroke();
        assertTrue(edit.isEmpty());
    }

    @Test
    public void testBeginStrokeClearsPreviousTracking() {
        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        brush.beginStroke();
        TerrainEdit second = brush.endStroke();
        assertTrue(second.isEmpty());
    }

    @Test
    public void testDifferentPointsStoreIndependentCompositions() {

        brush.setFalloff(0f);
        brush.setRadius(1f);

        // Point A.
        brush.setComposition(
            0.7f,
            0.2f,
            0.1f
        );

        brush.beginStroke();
        brush.paintAt(2, 2);
        brush.endStroke();

        int indexA =
            2 * loader.dimX + 2;

        // Point B.
        brush.setComposition(
            0.1f,
            0.3f,
            0.6f
        );

        brush.beginStroke();
        brush.paintAt(7, 7);
        brush.endStroke();

        int indexB =
            7 * loader.dimX + 7;

        assertEquals(
            0.7f,
            loader.sand[indexA],
            0.001f
        );

        assertEquals(
            0.2f,
            loader.silt[indexA],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.clay[indexA],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.sand[indexB],
            0.001f
        );

        assertEquals(
            0.3f,
            loader.silt[indexB],
            0.001f
        );

        assertEquals(
            0.6f,
            loader.clay[indexB],
            0.001f
        );
    }

    @Test
    public void testPaintedCompositionSurvivesSaveAndReload()
        throws IOException {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        PaintBrush brush =
            new PaintBrush(terrain);

        brush.setFalloff(0f);
        brush.setRadius(1f);

        brush.setComposition(
            0.2f,
            0.5f,
            0.3f
        );

        brush.beginStroke();
        brush.paintAt(4, 4);
        brush.endStroke();

        int index =
            4 * terrain.dimX + 4;

        File savedFile =
            tempFolder.newFile(
                "painted-composition.elv"
            );

        terrain.save(
            savedFile.getAbsolutePath()
        );

        ElvLoader reloaded =
            new ElvLoader();

        reloaded.load(
            savedFile.getAbsolutePath()
        );

        assertEquals(
            0.2f,
            reloaded.sand[index],
            0.001f
        );

        assertEquals(
            0.5f,
            reloaded.silt[index],
            0.001f
        );

        assertEquals(
            0.3f,
            reloaded.clay[index],
            0.001f
        );

        assertEquals(
            1.0f,
            reloaded.sand[index]
                + reloaded.silt[index]
                + reloaded.clay[index],
            0.001f
        );
    }

    @Test
    public void testRandomVariationKeepsCompositionNormalised() {

        brush.setRadius(3f);
        brush.setFalloff(0f);

        brush.setComposition(
            0.6f,
            0.3f,
            0.1f
        );

        brush.setRandomVariationEnabled(
            true
        );

        brush.setVariation(
            0.5f
        );

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        for (int z = 2; z <= 8; z++) {

            for (int x = 2; x <= 8; x++) {

                int dx = x - 5;
                int dz = z - 5;

                if (dx * dx + dz * dz > 9) {
                    continue;
                }

                int index =
                    z * loader.dimX + x;

                float sand =
                    loader.sand[index];

                float silt =
                    loader.silt[index];

                float clay =
                    loader.clay[index];

                assertTrue(
                    sand >= 0f &&
                    sand <= 1f
                );

                assertTrue(
                    silt >= 0f &&
                    silt <= 1f
                );

                assertTrue(
                    clay >= 0f &&
                    clay <= 1f
                );

                assertEquals(
                    1.0f,
                    sand + silt + clay,
                    0.001f
                );
            }
        }
    }

    @Test
    public void testCircularBrushAffectsOnlyPointsInsideRadius() {

        brush.setFalloff(0f);
        brush.setRadius(3f);
        brush.setDepth(1.0f);

        brush.paintAt(5, 5);

        for (int z = 0; z < loader.dimY; z++) {

            for (int x = 0; x < loader.dimX; x++) {

                int dx = x - 5;
                int dz = z - 5;

                float distanceSquared =
                    dx * dx + dz * dz;

                int index =
                    z * loader.dimX + x;

                if (distanceSquared <= 9f) {

                    assertEquals(
                        "Point inside radius should be painted",
                        1.0f,
                        loader.soilDepth[index],
                        0.001f
                    );

                } else {

                    assertEquals(
                        "Point outside radius should remain unchanged",
                        0.0f,
                        loader.soilDepth[index],
                        0.001f
                    );
                }
            }
        }
    }

    @Test
    public void testPaintStrokeInterpolatesHorizontalMovement() {

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setDepth(1.0f);

        brush.paintStroke(
            2,
            5,
            8,
            5
        );

        for (int x = 2; x <= 8; x++) {

            int index =
                5 * loader.dimX + x;

            assertTrue(
                "Intermediate horizontal point should be painted",
                loader.soilDepth[index] > 0f
            );
        }
    }

    @Test
    public void testConstantEffectIsUniformAcrossBrushFootprint() {

        brush.setFalloff(0f);
        brush.setRadius(3f);
        brush.setDepth(1.0f);
        brush.setComposition(
            0.6f,
            0.3f,
            0.1f
        );

        brush.setRandomVariationEnabled(false);

        brush.paintAt(5, 5);

        int centre =
            5 * loader.dimX + 5;

        int nearEdge =
            5 * loader.dimX + 7;

        assertEquals(
            loader.soilDepth[centre],
            loader.soilDepth[nearEdge],
            0.001f
        );

        assertEquals(
            loader.sand[centre],
            loader.sand[nearEdge],
            0.001f
        );

        assertEquals(
            loader.silt[centre],
            loader.silt[nearEdge],
            0.001f
        );

        assertEquals(
            loader.clay[centre],
            loader.clay[nearEdge],
            0.001f
        );
    }

    @Test
    public void testZeroRandomVariationMatchesConstantEffect() {

        brush.setFalloff(0f);
        brush.setRadius(3f);
        brush.setComposition(
            0.6f,
            0.3f,
            0.1f
        );

        brush.setRandomVariationEnabled(true);
        brush.setVariation(0f);

        brush.paintAt(5, 5);

        int centre =
            5 * loader.dimX + 5;

        int nearby =
            5 * loader.dimX + 6;

        assertEquals(
            loader.sand[centre],
            loader.sand[nearby],
            0.001f
        );

        assertEquals(
            loader.silt[centre],
            loader.silt[nearby],
            0.001f
        );

        assertEquals(
            loader.clay[centre],
            loader.clay[nearby],
            0.001f
        );
    }

    @Test
    public void testRandomVariationProducesDifferentValues() {

        brush.setFalloff(0f);
        brush.setRadius(3f);
        brush.setComposition(
            0.6f,
            0.3f,
            0.1f
        );

        brush.setRandomVariationEnabled(true);
        brush.setVariation(0.5f);

        brush.paintAt(5, 5);

        int centre =
            5 * loader.dimX + 5;

        boolean foundDifference =
            false;

        for (int z = 2; z <= 8; z++) {

            for (int x = 2; x <= 8; x++) {

                int dx = x - 5;
                int dz = z - 5;

                if (dx * dx + dz * dz > 9) {
                    continue;
                }

                int index =
                    z * loader.dimX + x;

                if (Math.abs(
                        loader.sand[index]
                            - loader.sand[centre]
                    ) > 0.0001f
                    ||
                    Math.abs(
                        loader.silt[index]
                            - loader.silt[centre]
                    ) > 0.0001f
                    ||
                    Math.abs(
                        loader.clay[index]
                            - loader.clay[centre]
                    ) > 0.0001f) {

                    foundDifference = true;
                    break;
                }
            }

            if (foundDifference) {
                break;
            }
        }

        assertTrue(
            "Random variation should produce at least some differing soil values",
            foundDifference
        );
    }

    @Test
    public void testLowStrengthMovesCompositionPartwayTowardTarget() {

        int index = 5 * loader.dimX + 5;

        loader.sand[index] = 0.2f;
        loader.silt[index] = 0.3f;
        loader.clay[index] = 0.5f;

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setBlendEnabled(true);
        brush.setStrength(0.25f);

        brush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        assertTrue(
            loader.sand[index] > 0.2f
        );

        assertTrue(
            loader.sand[index] < 0.8f
        );
    }

    @Test
    public void testHigherStrengthMovesCompositionCloserToTarget() {

        ElvLoader lowTerrain =
            TestTerrainData.flatMedium();

        ElvLoader highTerrain =
            TestTerrainData.flatMedium();

        int index =
            5 * lowTerrain.dimX + 5;

        lowTerrain.sand[index] = 0.2f;
        lowTerrain.silt[index] = 0.3f;
        lowTerrain.clay[index] = 0.5f;

        highTerrain.sand[index] = 0.2f;
        highTerrain.silt[index] = 0.3f;
        highTerrain.clay[index] = 0.5f;

        PaintBrush lowBrush =
            new PaintBrush(lowTerrain);

        PaintBrush highBrush =
            new PaintBrush(highTerrain);

        lowBrush.setFalloff(0f);
        highBrush.setFalloff(0f);

        lowBrush.setRadius(1f);
        highBrush.setRadius(1f);

        lowBrush.setBlendEnabled(true);
        highBrush.setBlendEnabled(true);

        lowBrush.setStrength(0.25f);
        highBrush.setStrength(0.75f);

        lowBrush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        highBrush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        lowBrush.beginStroke();
        lowBrush.paintAt(5, 5);
        lowBrush.endStroke();

        highBrush.beginStroke();
        highBrush.paintAt(5, 5);
        highBrush.endStroke();

        float lowDistance =
            Math.abs(
                0.8f - lowTerrain.sand[index]
            );

        float highDistance =
            Math.abs(
                0.8f - highTerrain.sand[index]
            );

        assertTrue(
            highDistance < lowDistance
        );
    }

    @Test
    public void testZeroStrengthLeavesCompositionUnchanged() {

        int index = 5 * loader.dimX + 5;

        loader.sand[index] = 0.2f;
        loader.silt[index] = 0.3f;
        loader.clay[index] = 0.5f;

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setBlendEnabled(true);
        brush.setStrength(0f);

        brush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        assertEquals(
            0.2f,
            loader.sand[index],
            0.001f
        );

        assertEquals(
            0.3f,
            loader.silt[index],
            0.001f
        );

        assertEquals(
            0.5f,
            loader.clay[index],
            0.001f
        );
    }

    @Test
    public void testFullStrengthMovesCompositionToTarget() {

        int index = 5 * loader.dimX + 5;

        loader.sand[index] = 0.2f;
        loader.silt[index] = 0.3f;
        loader.clay[index] = 0.5f;

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setBlendEnabled(true);
        brush.setStrength(1f);

        brush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        assertEquals(
            0.8f,
            loader.sand[index],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.silt[index],
            0.001f
        );

        assertEquals(
            0.1f,
            loader.clay[index],
            0.001f
        );
    }

    @Test
    public void testStrengthDoesNotChangeBrushFootprint() {

        ElvLoader lowTerrain =
            TestTerrainData.flatMedium();

        ElvLoader highTerrain =
            TestTerrainData.flatMedium();

        PaintBrush lowBrush =
            new PaintBrush(lowTerrain);

        PaintBrush highBrush =
            new PaintBrush(highTerrain);

        lowBrush.setFalloff(0f);
        highBrush.setFalloff(0f);

        lowBrush.setRadius(3f);
        highBrush.setRadius(3f);

        lowBrush.setBlendEnabled(true);
        highBrush.setBlendEnabled(true);

        lowBrush.setStrength(0.25f);
        highBrush.setStrength(1f);

        lowBrush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        highBrush.setComposition(
            0.8f,
            0.1f,
            0.1f
        );

        lowBrush.paintAt(5, 5);
        highBrush.paintAt(5, 5);

        for (int i = 0; i < lowTerrain.sand.length; i++) {

            boolean lowChanged =
                Math.abs(
                    lowTerrain.sand[i] - 0.33f
                ) > 0.001f;

            boolean highChanged =
                Math.abs(
                    highTerrain.sand[i] - 0.33f
                ) > 0.001f;

            assertEquals(
                lowChanged,
                highChanged
            );
        }
    }

    @Test
    public void testDifferentPointsStoreIndependentDepths() {

        brush.setFalloff(0f);
        brush.setRadius(1f);

        brush.setDepth(0.4f);

        brush.beginStroke();
        brush.paintAt(2, 2);
        brush.endStroke();

        int indexA =
            2 * loader.dimX + 2;

        brush.setDepth(1.2f);

        brush.beginStroke();
        brush.paintAt(7, 7);
        brush.endStroke();

        int indexB =
            7 * loader.dimX + 7;

        assertEquals(
            0.4f,
            loader.soilDepth[indexA],
            0.001f
        );

        assertEquals(
            1.2f,
            loader.soilDepth[indexB],
            0.001f
        );
    }

    @Test
    public void testPaintedDepthSurvivesSaveAndReload()
        throws IOException {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        PaintBrush brush =
            new PaintBrush(terrain);

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setDepth(1.1f);

        brush.beginStroke();
        brush.paintAt(4, 4);
        brush.endStroke();

        int index =
            4 * terrain.dimX + 4;

        File savedFile =
            tempFolder.newFile(
                "painted-depth.elv"
            );

        terrain.save(
            savedFile.getAbsolutePath()
        );

        ElvLoader reloaded =
            new ElvLoader();

        reloaded.load(
            savedFile.getAbsolutePath()
        );

        assertEquals(
            1.1f,
            reloaded.soilDepth[index],
            0.001f
        );
    }

    @Test
    public void testEditingSoilDepthDoesNotChangeElevation() {

        brush.setFalloff(0f);
        brush.setRadius(1f);
        brush.setDepth(1.0f);

        int index =
            5 * loader.dimX + 5;

        float originalHeight =
            loader.heights[index];

        float originalDepth =
            loader.soilDepth[index];

        brush.beginStroke();
        brush.paintAt(5, 5);
        brush.endStroke();

        assertNotEquals(
            originalDepth,
            loader.soilDepth[index],
            0.001f
        );

        assertEquals(
            originalHeight,
            loader.heights[index],
            0.001f
        );
    }
}

package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.Mesh;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for SoilNailBuilder.
 *
 * The builder returns a List<Mesh>, chunked so short indices never
 * overflow. Every test that actually calls build() must run on the
 * GDX thread because Mesh construction requires a GL context.
 *
 * Vertex layout (9 floats per vertex):
 *   [0..2] position (x, y, z)
 *   [3..5] normal   (x, y, z)
 *   [6]    packed colour
 *   [7..8] texCoord (baselineY, baselineValid) - always 0/0 here
 *
 * Nail vertex order (8 verts per nail):
 *   Quad 1 (spans Z at fixed X, normal +X): 0,1 top / 2,3 bottom
 *   Quad 2 (spans X at fixed Z, normal +Z): 4,5 top / 6,7 bottom
 */
public class SoilNailBuilderTest {

    private static final int FPV = 9; // floats per vertex
    private static final int V_PER_NAIL = 8;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpClass() {
        TestGdxSetup.init();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Flat terrain where every grid point has the given soil depth. */
    private static ElvLoader flatTerrain(int dimX, int dimY, float spacing,
                                          float height, float soilDepth) {
        ElvLoader l = new ElvLoader();
        l.dimX = dimX;
        l.dimY = dimY;
        l.gridSpacing = spacing;
        l.latitude = 0f;

        int n = dimX * dimY;
        l.heights        = new float[n];
        l.soilDepth      = new float[n];
        l.sand           = new float[n];
        l.silt           = new float[n];
        l.clay           = new float[n];
        l.maxDepth       = new float[n];
        l.smoothHeights  = new float[n];

        for (int i = 0; i < n; i++) {
            l.heights[i]       = height;
            l.soilDepth[i]     = soilDepth;
            l.sand[i]          = 0.33f;
            l.silt[i]          = 0.33f;
            l.clay[i]          = 0.34f;
            l.maxDepth[i]      = 2f;
            l.smoothHeights[i] = height;
        }
        return l;
    }

    /** Flat terrain with a single non-zero soil point at (gx, gz). */
    private static ElvLoader singlePointTerrain(int dimX, int dimY,
                                                 int gx, int gz,
                                                 float height, float soilDepth) {
        ElvLoader l = flatTerrain(dimX, dimY, 1f, height, 0f);
        l.soilDepth[gz * dimX + gx] = soilDepth;
        return l;
    }

    /** Total nails across every chunk. */
    private static int countNails(List<Mesh> meshes) {
        int total = 0;
        for (Mesh m : meshes) total += m.getNumVertices() / V_PER_NAIL;
        return total;
    }

    /** Reads every vertex of a mesh into a fresh float array. */
    private static float[] readVertices(Mesh mesh) {
        int fpv = mesh.getVertexSize() / 4;
        float[] v = new float[mesh.getNumVertices() * fpv];
        mesh.getVertices(v);
        return v;
    }

    // ==================================================================
    // Emptiness and sampling
    // ==================================================================

    @Test
    public void testAllZeroSoilProducesNoMeshes() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = flatTerrain(4, 4, 1f, 1f, 0f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            assertNotNull(meshes);
            assertTrue(meshes.isEmpty());
        });
    }

    @Test
    public void testStrideOneEmitsOneNailPerGridPoint() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = flatTerrain(3, 3, 1f, 1f, 0.5f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            assertEquals(9, countNails(meshes));
        });
    }

    @Test
    public void testStrideSkipsPoints() {
        TestGdxSetup.runOnGdxThread(() -> {
            // stride 2 on a 10x10 grid samples at 0,2,4,6,8 -> 5x5 = 25
            ElvLoader l = flatTerrain(10, 10, 1f, 1f, 0.5f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 2);
            assertEquals(25, countNails(meshes));
        });
    }

    @Test
    public void testStrideZeroClampedToOne() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = flatTerrain(4, 4, 1f, 1f, 0.5f);
            int withZero = countNails(SoilNailBuilder.build(l, 0));
            int withOne  = countNails(SoilNailBuilder.build(l, 1));
            assertEquals(withOne, withZero);
        });
    }

    @Test
    public void testPointsBelowDepthThresholdAreSkipped() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = flatTerrain(3, 3, 1f, 1f, 0.5f);
            l.soilDepth[4] = 0.0005f; // below 0.001 threshold
            assertEquals(8, countNails(SoilNailBuilder.build(l, 1)));
        });
    }

    // ==================================================================
    // Nail geometry - known points
    // ==================================================================

    @Test
    public void testNailStraddlesSurfaceAtKnownPoint() {
        TestGdxSetup.runOnGdxThread(() -> {
            // Single nail at grid (2, 2). Height 10, soil depth 3.
            // Expected: top = 13, bottom = 7.
            ElvLoader l = singlePointTerrain(5, 5, 2, 2, 10f, 3f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);

            assertEquals(1, meshes.size());
            Mesh mesh = meshes.get(0);
            assertEquals(V_PER_NAIL, mesh.getNumVertices());
            assertEquals(12, mesh.getNumIndices());

            float[] v = readVertices(mesh);

            float cx = 2f;
            float cz = 2f;
            float topY = 13f;
            float botY = 7f;

            // Vertex 0 - top of quad 1
            assertEquals(cx,   v[0],                0.0001f);
            assertEquals(topY, v[1],                0.0001f);
            // Vertex 2 - bottom of quad 1
            assertEquals(botY, v[2 * FPV + 1],      0.0001f);
            // Vertex 4 - top of quad 2 (sits at cx - halfWidth)
            assertEquals(cx - 0.35f, v[4 * FPV + 0], 0.0001f);
            assertEquals(topY,       v[4 * FPV + 1], 0.0001f);
            assertEquals(cz,         v[4 * FPV + 2], 0.0001f);
            // Vertex 6 - bottom of quad 2
            assertEquals(botY, v[6 * FPV + 1],      0.0001f);
        });
    }

    @Test
    public void testQuadNormalsAreSidewaysNotUp() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = singlePointTerrain(3, 3, 1, 1, 5f, 1f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            float[] v = readVertices(meshes.get(0));

            // Quad 1 (verts 0..3) faces +X
            for (int i = 0; i < 4; i++) {
                assertEquals(1f, v[i * FPV + 3], 0.0001f);
                assertEquals(0f, v[i * FPV + 4], 0.0001f);
                assertEquals(0f, v[i * FPV + 5], 0.0001f);
            }
            // Quad 2 (verts 4..7) faces +Z
            for (int i = 4; i < 8; i++) {
                assertEquals(0f, v[i * FPV + 3], 0.0001f);
                assertEquals(0f, v[i * FPV + 4], 0.0001f);
                assertEquals(1f, v[i * FPV + 5], 0.0001f);
            }
        });
    }

    @Test
    public void testHalfWidthScalesWithGridSpacing() {
        TestGdxSetup.runOnGdxThread(() -> {
            // gs = 2, stride = 1 -> spacing = 2 -> hw = 2 * 0.35 = 0.7
            ElvLoader l = singlePointTerrain(3, 3, 1, 1, 5f, 1f);
            l.gridSpacing = 2f;
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            float[] v = readVertices(meshes.get(0));

            // Vertex 0 is at z = cz - hw, vertex 1 is at z = cz + hw
            float hw = (v[1 * FPV + 2] - v[0 * FPV + 2]) / 2f;
            assertEquals(0.7f, hw, 0.0001f);
        });
    }

    @Test
    public void testHalfWidthCappedAtLargeStride() {
        TestGdxSetup.runOnGdxThread(() -> {
            // gs = 1, stride = 8 -> spacing = 8 -> 8*0.35 = 2.8
            // cap = gs * 2 = 2 -> hw should be 2, not 2.8
            ElvLoader l = singlePointTerrain(20, 20, 0, 0, 5f, 1f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 8);
            float[] v = readVertices(meshes.get(0));

            float hw = (v[1 * FPV + 2] - v[0 * FPV + 2]) / 2f;
            assertEquals(2f, hw, 0.0001f);
        });
    }

    // ==================================================================
    // Colour - depth ramp
    // ==================================================================

    @Test
    public void testColourMatchesDepthPalette() {
        TestGdxSetup.runOnGdxThread(() -> {
            // Nails emitted in scan order: (0,0) first.
            // Depth at (0,0) is 0.5, max across grid is 1.0.
            ElvLoader l = flatTerrain(3, 3, 1f, 1f, 0.5f);
            l.soilDepth[4] = 1.0f;

            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            float[] v = readVertices(meshes.get(0));

            float expected = SoilPalette.getDepthColor(0.5f, 1.0f);
            float actual   = v[6]; // colour of first vertex of first nail
            assertEquals(expected, actual, 0.0001f);
        });
    }

    @Test
    public void testDeeperSoilGetsDifferentColourThanShallow() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader l = flatTerrain(3, 1, 1f, 1f, 0f);
            l.soilDepth[0] = 0.5f;  // shallow
            l.soilDepth[2] = 4.0f;  // deep

            List<Mesh> meshes = SoilNailBuilder.build(l, 1);
            float[] v = readVertices(meshes.get(0));

            float shallow = v[0 * V_PER_NAIL * FPV + 6];
            float deep    = v[1 * V_PER_NAIL * FPV + 6];
            assertNotEquals(shallow, deep, 0.0001f);
        });
    }

    // ==================================================================
    // Chunking (the whole point of the rewrite)
    // ==================================================================

    @Test
    public void testChunkingBeyondFourThousandNails() {
        TestGdxSetup.runOnGdxThread(() -> {
            // 65 * 65 = 4225 > 4000, must split
            ElvLoader l = flatTerrain(65, 65, 1f, 1f, 0.5f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);

            assertTrue("Must chunk past 4000 nails", meshes.size() >= 2);

            int total = 0;
            for (Mesh m : meshes) {
                int nails = m.getNumVertices() / V_PER_NAIL;
                assertTrue("Chunk exceeds 4000 nails", nails <= 4000);
                total += nails;
            }
            assertEquals(4225, total);
        });
    }

    @Test
    public void testIndicesAreInRangeWithinEveryChunk() {
        TestGdxSetup.runOnGdxThread(() -> {
            // Small enough to stay in one chunk, big enough to be meaningful.
            ElvLoader l = flatTerrain(20, 20, 1f, 1f, 0.5f);
            List<Mesh> meshes = SoilNailBuilder.build(l, 1);

            for (Mesh m : meshes) {
                short[] idx = new short[m.getNumIndices()];
                m.getIndices(idx);
                int vertCount = m.getNumVertices();
                for (short i : idx) {
                    assertTrue(
                        "Index " + i + " out of range (chunk has "
                            + vertCount + " vertices)",
                        i >= 0 && i < vertCount
                    );
                }
            }
        });
    }

    // ==================================================================
    // Save / reload round trip
    // ==================================================================

    @Test
    public void testSaveReloadPreservesDepthAndGeometry() {
        TestGdxSetup.runOnGdxThread(() -> {
            try {
                ElvLoader original = flatTerrain(4, 4, 1f, 2f, 1.5f);
                File f = tempFolder.newFile("nails_roundtrip.elv");
                original.save(f.getAbsolutePath());

                ElvLoader reloaded = new ElvLoader();
                reloaded.load(f.getAbsolutePath());

                List<Mesh> before = SoilNailBuilder.build(original, 1);
                List<Mesh> after  = SoilNailBuilder.build(reloaded, 1);

                assertEquals(countNails(before), countNails(after));

                float[] a = readVertices(before.get(0));
                float[] b = readVertices(after.get(0));

                assertEquals(a.length, b.length);
                for (int i = 0; i < a.length; i++) {
                    // Position, normal, and packed colour must all survive.
                    assertEquals(
                        "Vertex component " + i + " differs after reload",
                        a[i], b[i], 0.0001f
                    );
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testSaveReloadPreservesDepthRampRange() {
        TestGdxSetup.runOnGdxThread(() -> {
            try {
                // Deliberately mixed depths so the ramp is exercised.
                ElvLoader original = flatTerrain(3, 3, 1f, 1f, 0.25f);
                original.soilDepth[4] = 1.75f;

                File f = tempFolder.newFile("ramp_roundtrip.elv");
                original.save(f.getAbsolutePath());

                ElvLoader reloaded = new ElvLoader();
                reloaded.load(f.getAbsolutePath());

                float[] a = readVertices(SoilNailBuilder.build(original, 1).get(0));
                float[] b = readVertices(SoilNailBuilder.build(reloaded, 1).get(0));

                // The colour at every vertex must match exactly.
                for (int nail = 0; nail < 9; nail++) {
                    int colourOffset = nail * V_PER_NAIL * FPV + 6;
                    assertEquals(
                        "Colour at nail " + nail + " differs after reload",
                        a[colourOffset], b[colourOffset], 0.0001f
                    );
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

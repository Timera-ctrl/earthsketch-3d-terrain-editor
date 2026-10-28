package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TerrainMeshBuilderTest {

    @BeforeClass
    public static void setUpClass() {
        TestGdxSetup.init();
    }

    private ElvLoader loader;

    @Before
    public void setUp() {
        loader = TestTerrainData.flatSmall();
    }

    // ==================================================================
    // slopeNorm
    // ==================================================================

    @Test
    public void testSlopeNorm_IsUnitLengthAtCentre() {
        float[] heights = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        Vector3 n = TerrainMeshBuilder.slopeNorm(heights, 1, 1, 3, 3, 1.0f);
        assertEquals(1.0f, n.len(), 0.001f);
    }

    @Test
    public void testSlopeNorm_IsUnitLengthAtEdge() {
        float[] heights = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        Vector3 n = TerrainMeshBuilder.slopeNorm(heights, 0, 1, 3, 3, 1.0f);
        assertEquals(1.0f, n.len(), 0.001f);
    }

    @Test
    public void testSlopeNorm_IsUnitLengthAtCorner() {
        float[] heights = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        Vector3 n = TerrainMeshBuilder.slopeNorm(heights, 0, 0, 3, 3, 1.0f);
        assertEquals(1.0f, n.len(), 0.001f);
    }

    @Test
    public void testSlopeNorm_FlatTerrainPointsStraightUp() {
        float[] heights = TestTerrainData.flatHeights3x3();
        Vector3 n = TerrainMeshBuilder.slopeNorm(heights, 1, 1, 3, 3, 1.0f);

        assertEquals(0.0f, n.x, 0.001f);
        assertEquals(1.0f, n.y, 0.001f);
        assertEquals(0.0f, n.z, 0.001f);
    }

    @Test
    public void testSlopeNorm_PeakTiltsNormal() {
        float[] heights = TestTerrainData.peakHeights3x3();
        Vector3 n = TerrainMeshBuilder.slopeNorm(heights, 1, 1, 3, 3, 1.0f);

        assertEquals(1.0f, n.len(), 0.001f);
        assertTrue("Peak should tilt the normal away from vertical",
            n.y < 1.0f);
    }

    @Test
    public void testSlopeNorm_FinerGridProducesSteeperTilt() {
        float[] heights = {0, 1, 2, 3, 4, 5, 6, 7, 8};

        Vector3 coarse = TerrainMeshBuilder.slopeNorm(heights, 1, 1, 3, 3, 1.0f);
        Vector3 fine   = TerrainMeshBuilder.slopeNorm(heights, 1, 1, 3, 3, 0.5f);

        assertTrue(fine.y < coarse.y);
    }

    // ==================================================================
    // buildTerrain
    // ==================================================================

    @Test
    public void testBuildTerrain_NonEmpty() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            assertNotNull(meshes);
            assertFalse(meshes.isEmpty());
            for (Mesh m : meshes) {
                assertTrue(m.getNumVertices() > 0);
                assertTrue(m.getNumIndices() > 0);
            }
        });
    }

    @Test
    public void testBuildTerrain_VertexCountMatchesGrid() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            int total = 0;
            for (Mesh m : meshes) total += m.getNumVertices();
            // 4x3 = 12 vertices
            assertEquals(12, total);
        });
    }

    @Test
    public void testBuildTerrain_IndexCountMatchesCells() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            int total = 0;
            for (Mesh m : meshes) total += m.getNumIndices();
            // (4-1) * (3-1) = 6 cells x 6 indices
            assertEquals(36, total);
        });
    }

    @Test
    public void testBuildTerrain_BaselineIsStoredInTexCoords() {
        TestGdxSetup.runOnGdxThread(() -> {
            float[] baseline = new float[loader.heights.length];
            for (int i = 0; i < baseline.length; i++) baseline[i] = 100f + i;

            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, baseline);
            Mesh mesh = meshes.get(0);
            int fpv = mesh.getVertexSize() / 4;
            float[] verts = new float[mesh.getNumVertices() * fpv];
            mesh.getVertices(verts);

            // texCoord.x at offset 7, texCoord.y at offset 8
            assertEquals(100f, verts[7], 0.001f);
            assertEquals(1.0f, verts[8], 0.001f);
        });
    }

    @Test
    public void testBuildTerrain_BoundingBoxMatchesHeights() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            BoundingBox box = new BoundingBox();
            meshes.get(0).calculateBoundingBox(box);

            // All heights are 1.0 in the fixture.
            assertEquals(1.0f, box.min.y, 0.001f);
            assertEquals(1.0f, box.max.y, 0.001f);
        });
    }

    @Test
    public void testBuildTerrain_SingleCell() {
        TestGdxSetup.runOnGdxThread(() -> {
            ElvLoader tiny = new ElvLoader();
            tiny.dimX = 2;
            tiny.dimY = 2;
            tiny.gridSpacing = 1.0f;
            tiny.heights = new float[]{1, 2, 3, 4};

            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(tiny, tiny.heights);
            assertEquals(1, meshes.size());
            assertEquals(4, meshes.get(0).getNumVertices());
            assertEquals(6, meshes.get(0).getNumIndices());
        });
    }

    @Test
    public void testBuildTerrain_MultiTileSplits() {
        TestGdxSetup.runOnGdxThread(() -> {
            // 300x300 exceeds the 128 tile size, so multiple meshes result.
            ElvLoader big = new ElvLoader();
            big.dimX = 300;
            big.dimY = 300;
            big.gridSpacing = 1.0f;
            int n = 300 * 300;
            big.heights = new float[n];
            for (int i = 0; i < n; i++) big.heights[i] = 1.0f;

            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(big, big.heights);
            assertTrue("Expected multiple tiles", meshes.size() > 1);
        });
    }

    // ==================================================================
    // buildTerrainSkirt
    // ==================================================================

    @Test
    public void testBuildTerrainSkirt_NonEmpty() {
        TestGdxSetup.runOnGdxThread(() -> {
            Mesh skirt = TerrainMeshBuilder.buildTerrainSkirt(loader);
            assertNotNull(skirt);
            assertTrue(skirt.getNumVertices() > 0);
            assertTrue(skirt.getNumIndices() > 0);
        });
    }

    @Test
    public void testBuildTerrainSkirt_ExtendsBelowTerrain() {
        TestGdxSetup.runOnGdxThread(() -> {
            Mesh skirt = TerrainMeshBuilder.buildTerrainSkirt(loader);
            BoundingBox box = new BoundingBox();
            skirt.calculateBoundingBox(box);
            assertTrue(box.min.y < 0f);
            assertTrue(box.max.y >= 1.0f);
        });
    }

    @Test
    public void testBuildTerrainSkirt_LargerTerrain() {
        TestGdxSetup.runOnGdxThread(() -> {
            Mesh skirt = TerrainMeshBuilder.buildTerrainSkirt(
                TestTerrainData.flatMedium());
            assertNotNull(skirt);
            assertTrue(skirt.getNumVertices() > 0);
        });
    }

    // ==================================================================
    // buildTerrainBottom
    // ==================================================================

    @Test
    public void testBuildTerrainBottom_HasFourVertices() {
        TestGdxSetup.runOnGdxThread(() -> {
            Mesh bottom = TerrainMeshBuilder.buildTerrainBottom(loader);
            assertEquals(4, bottom.getNumVertices());
            assertEquals(6, bottom.getNumIndices());
        });
    }

    @Test
    public void testBuildTerrainBottom_SitsBelowHeights() {
        TestGdxSetup.runOnGdxThread(() -> {
            Mesh bottom = TerrainMeshBuilder.buildTerrainBottom(loader);
            BoundingBox box = new BoundingBox();
            bottom.calculateBoundingBox(box);

            for (float h : loader.heights) {
                assertTrue(box.max.y < h);
            }
        });
    }

    // ==================================================================
    // updateTerrainColors
    // ==================================================================

    @Test
    public void testUpdateTerrainColors_CompositionMode() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            TerrainMeshBuilder.updateTerrainColors(
                meshes, loader, TerrainMeshBuilder.ViewMode.COMPOSITION);

            Mesh mesh = meshes.get(0);
            int fpv = mesh.getVertexSize() / 4;
            float[] verts = new float[mesh.getNumVertices() * fpv];
            mesh.getVertices(verts);

            float actual = verts[6];
            float expected = SoilPalette.getColor(0.33f, 0.33f, 0.34f);
            assertEquals(expected, actual, 0.0001f);
        });
    }

    @Test
    public void testUpdateTerrainColors_DepthModeDiffers() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            TerrainMeshBuilder.updateTerrainColors(
                meshes, loader, TerrainMeshBuilder.ViewMode.DEPTH);

            Mesh mesh = meshes.get(0);
            int fpv = mesh.getVertexSize() / 4;
            float[] verts = new float[mesh.getNumVertices() * fpv];
            mesh.getVertices(verts);

            float depthColor = verts[6];
            float compColor = SoilPalette.getColor(0.33f, 0.33f, 0.34f);
            assertNotEquals(compColor, depthColor, 0.0001f);
        });
    }

    @Test
    public void testUpdateTerrainColors_DepthModeWithZeroSoil() {
        TestGdxSetup.runOnGdxThread(() -> {
            for (int i = 0; i < loader.soilDepth.length; i++) {
                loader.soilDepth[i] = 0f;
            }

            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            TerrainMeshBuilder.updateTerrainColors(
                meshes, loader, TerrainMeshBuilder.ViewMode.DEPTH);

            // Must not crash on the maxDepth == 0 divide, and must still
            // write a non-zero colour.
            Mesh mesh = meshes.get(0);
            int fpv = mesh.getVertexSize() / 4;
            float[] verts = new float[mesh.getNumVertices() * fpv];
            mesh.getVertices(verts);
            assertNotEquals(0f, verts[6]);
        });
    }

    // ==================================================================
    // updateTerrainGeometry
    // ==================================================================

    @Test
    public void testUpdateTerrainGeometry_ReflectsHeightChange() {
        TestGdxSetup.runOnGdxThread(() -> {
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);
            loader.heights[0] = 42f;

            TerrainMeshBuilder.updateTerrainGeometry(meshes, loader);

            BoundingBox box = new BoundingBox();
            meshes.get(0).calculateBoundingBox(box);
            assertEquals(42f, box.max.y, 0.001f);
        });
    }

    @Test
    public void testUpdateTerrainGeometry_RecomputesNormals() {
        TestGdxSetup.runOnGdxThread(() -> {
            // Start with a slope
            for (int i = 0; i < loader.heights.length; i++) {
                loader.heights[i] = i * 0.5f;
            }
            List<Mesh> meshes = TerrainMeshBuilder.buildTerrain(loader, loader.heights);

            // Flatten the terrain, then refresh geometry.
            for (int i = 0; i < loader.heights.length; i++) {
                loader.heights[i] = 1.0f;
            }
            TerrainMeshBuilder.updateTerrainGeometry(meshes, loader);

            Mesh mesh = meshes.get(0);
            int fpv = mesh.getVertexSize() / 4;
            float[] verts = new float[mesh.getNumVertices() * fpv];
            mesh.getVertices(verts);

            // Flat terrain -> normal is straight up
            assertEquals(0f, verts[3], 0.001f);
            assertEquals(1f, verts[4], 0.001f);
            assertEquals(0f, verts[5], 0.001f);
        });
    }
}

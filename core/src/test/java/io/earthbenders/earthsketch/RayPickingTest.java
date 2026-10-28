package io.earthbenders.earthsketch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;

import java.lang.reflect.Method;
import java.util.List;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for RayPicking's state machine and non-GL behaviour.
 *
 * An empty mesh list is used on purpose. The constructor tolerates it,
 * the bounding-box loop is skipped, and every mouse event short-circuits
 * at pickTile() returning null. That lets us exercise the class's own
 * logic without a GL context or mocked Mesh objects.
 */
public class RayPickingTest {

    private ElvLoader loader;
    private PaintBrush paintBrush;
    private TerrainBrush terrainBrush;
    private HistoryManager manager;
    private PerspectiveCamera camera;
    private RayPicking picker;

    @BeforeClass
    public static void setUpClass() {
        TestGdxSetup.init();
    }

    @Before
    public void setUp() {
        loader = TestTerrainData.flatMedium();
        paintBrush = new PaintBrush(loader);
        terrainBrush = new TerrainBrush(loader);
        manager = new HistoryManager(50);

        camera = new PerspectiveCamera(67f, 1280, 720);
        camera.position.set(5, 10, 15);
        camera.lookAt(5, 1, 5);
        camera.update();

        picker = new RayPicking(
            Collections.emptyList(),
            camera,
            loader,
            paintBrush,
            terrainBrush,
            manager
        );
    }

    // ==================================================================
    // Mode state machine
    // ==================================================================

    @Test
    public void testNavigateModeDoesNotConsumeClick() {
        assertFalse(picker.touchDown(100, 100, 0, Buttons.LEFT));
    }

    @Test
    public void testPaintModeConsumesLeftClick() {
        picker.setPaintMode(true);
        assertTrue(picker.touchDown(100, 100, 0, Buttons.LEFT));
    }

    @Test
    public void testSculptModeConsumesLeftClick() {
        picker.setSculptMode(true);
        assertTrue(picker.touchDown(100, 100, 0, Buttons.LEFT));
    }

    @Test
    public void testRightClickNeverConsumed() {
        picker.setPaintMode(true);
        picker.setSculptMode(true);
        assertFalse(picker.touchDown(100, 100, 0, Buttons.RIGHT));
    }

    @Test
    public void testModesAreIndependent() {
        picker.setPaintMode(true);
        picker.setSculptMode(false);
        assertTrue(picker.touchDown(10, 10, 0, Buttons.LEFT));

        picker.setPaintMode(false);
        picker.setSculptMode(true);
        assertTrue(picker.touchDown(10, 10, 0, Buttons.LEFT));

        picker.setPaintMode(false);
        picker.setSculptMode(false);
        assertFalse(picker.touchDown(10, 10, 0, Buttons.LEFT));
    }

    // ==================================================================
    // Brush settings validity
    // ==================================================================

    @Test
    public void testInvalidBrushStillConsumesClickInPaintMode() {
        picker.setPaintMode(true);
        picker.setBrushSettingsValid(false);
        // Consuming is correct: the click must not fall through to the
        // camera controller. It just does not paint.
        assertTrue(picker.touchDown(10, 10, 0, Buttons.LEFT));
    }

    @Test
    public void testBrushValidityDoesNotGateSculptInput() {
        // Sculpt does not consult brushSettingsValid. This test documents
        // the current behaviour so a change is visible.
        picker.setSculptMode(true);
        picker.setBrushSettingsValid(false);
        assertTrue(picker.touchDown(10, 10, 0, Buttons.LEFT));
    }

    // ==================================================================
    // Undo / redo key handling
    // ==================================================================

    @Test
    public void testUndoOnEmptyStackIsSafe() {
        manager.undo(loader);
    }

    @Test
    public void testRedoOnEmptyStackIsSafe() {
        manager.redo(loader);
    }

    @Test
    public void testUndoRestoresSoilDepth() {
        int idx = 5 * loader.dimX + 5;
        TerrainEdit edit = new TerrainEdit();
        edit.recordPoint(
            idx,
            new float[]{loader.heights[idx], 0.5f, 0.33f, 0.33f, 0.34f},
            new float[]{loader.heights[idx], 0.9f, 0.5f, 0.3f, 0.2f}
        );
        manager.recordEdit(edit);

        loader.soilDepth[idx] = 0.9f;
        manager.undo(loader);
        assertEquals(0.5f, loader.soilDepth[idx], 0.001f);

        manager.redo(loader);
        assertEquals(0.9f, loader.soilDepth[idx], 0.001f);
    }

    @Test
    public void testUndoRestoresHeights() {
        int idx = 3 * loader.dimX + 3;
        float original = loader.heights[idx];

        TerrainEdit edit = new TerrainEdit();
        edit.recordPoint(
            idx,
            new float[]{original, 0.5f, 0.33f, 0.33f, 0.34f},
            new float[]{original - 3f, 0f, 0.33f, 0.33f, 0.34f}
        );
        manager.recordEdit(edit);

        loader.heights[idx] = original - 3f;
        manager.undo(loader);
        assertEquals(original, loader.heights[idx], 0.001f);
    }

    // ==================================================================
    // Hover listener
    // ==================================================================

    @Test
    public void testMouseMovedWithoutListenerIsSafe() {
        assertFalse(picker.mouseMoved(100, 100));
    }

    // ==================================================================
    // Listener setters are null-tolerant
    // ==================================================================

    @Test
    public void testNullListenersAreTolerated() {
        picker.setPointInspectionListener(null);
        picker.setTerrainChangeListener(null);
        picker.setGeometryChangeListener(null);
        // Reaching here without an exception is the assertion.
    }

    private Object invokePrivate(
        Object object,
        String methodName,
        Class<?>[] parameterTypes,
        Object... args
    ) throws Exception {

        Method method =
            object.getClass()
                .getDeclaredMethod(
                    methodName,
                    parameterTypes
                );

        method.setAccessible(true);

        return method.invoke(
            object,
            args
        );
    }

    private RayPicking createPickerWithRealTerrain(
        ElvLoader terrain,
        PerspectiveCamera testCamera,
        List<Mesh> meshes
    ) {

        PaintBrush brush =
            new PaintBrush(terrain);

        TerrainBrush sculptBrush =
            new TerrainBrush(terrain);

        HistoryManager history =
            new HistoryManager(50);

        return new RayPicking(
            meshes,
            testCamera,
            terrain,
            brush,
            sculptBrush,
            history
        );
    }

    @Test
    public void testWorldPositionMapsToExpectedGridPoint()
        throws Exception {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        terrain.gridSpacing = 2.0f;

        RayPicking testPicker =
            new RayPicking(
                Collections.emptyList(),
                camera,
                terrain,
                new PaintBrush(terrain),
                new TerrainBrush(terrain),
                new HistoryManager(50)
            );

        Vector3 hit =
            new Vector3(
                4.1f,
                1.0f,
                5.9f
            );

        int[] grid =
            (int[])
                invokePrivate(
                    testPicker,
                    "getGridPoint",
                    new Class<?>[]{
                        Vector3.class
                    },
                    hit
                );

        assertEquals(
            2,
            grid[0]
        );

        assertEquals(
            3,
            grid[1]
        );
    }

    @Test
    public void testWorldPositionClampsToTerrainBounds()
        throws Exception {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        terrain.gridSpacing = 1.0f;

        RayPicking testPicker =
            new RayPicking(
                Collections.emptyList(),
                camera,
                terrain,
                new PaintBrush(terrain),
                new TerrainBrush(terrain),
                new HistoryManager(50)
            );

        Vector3 negativeHit =
            new Vector3(
                -100.0f,
                1.0f,
                -100.0f
            );

        int[] minimumGrid =
            (int[])
                invokePrivate(
                    testPicker,
                    "getGridPoint",
                    new Class<?>[]{
                        Vector3.class
                    },
                    negativeHit
                );

        assertEquals(
            0,
            minimumGrid[0]
        );

        assertEquals(
            0,
            minimumGrid[1]
        );

        Vector3 tooLargeHit =
            new Vector3(
                1000.0f,
                1.0f,
                1000.0f
            );

        int[] maximumGrid =
            (int[])
                invokePrivate(
                    testPicker,
                    "getGridPoint",
                    new Class<?>[]{
                        Vector3.class
                    },
                    tooLargeHit
                );

        assertEquals(
            terrain.dimX - 1,
            maximumGrid[0]
        );

        assertEquals(
            terrain.dimY - 1,
            maximumGrid[1]
        );
    }

    @Test
    public void testInspectPointReturnsStoredTerrainValues()
        throws Exception {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        int gx = 3;
        int gz = 4;

        int index =
            gz * terrain.dimX + gx;

        terrain.heights[index] = 12.5f;
        terrain.soilDepth[index] = 1.25f;
        terrain.sand[index] = 0.60f;
        terrain.silt[index] = 0.25f;
        terrain.clay[index] = 0.15f;

        RayPicking testPicker =
            new RayPicking(
                Collections.emptyList(),
                camera,
                terrain,
                new PaintBrush(terrain),
                new TerrainBrush(terrain),
                new HistoryManager(50)
            );

        final int[] receivedGrid =
            new int[2];

        final float[] receivedValues =
            new float[5];

        final boolean[] called =
            {false};

        testPicker.setPointInspectionListener(
            (
                x,
                z,
                elevation,
                depth,
                sand,
                silt,
                clay
            ) -> {

                called[0] = true;

                receivedGrid[0] = x;
                receivedGrid[1] = z;

                receivedValues[0] = elevation;
                receivedValues[1] = depth;
                receivedValues[2] = sand;
                receivedValues[3] = silt;
                receivedValues[4] = clay;
            }
        );

        invokePrivate(
            testPicker,
            "inspectPoint",
            new Class<?>[]{
                int[].class
            },
            new int[]{
                gx,
                gz
            }
        );

        assertTrue(
            called[0]
        );

        assertEquals(
            gx,
            receivedGrid[0]
        );

        assertEquals(
            gz,
            receivedGrid[1]
        );

        assertEquals(
            12.5f,
            receivedValues[0],
            0.001f
        );

        assertEquals(
            1.25f,
            receivedValues[1],
            0.001f
        );

        assertEquals(
            0.60f,
            receivedValues[2],
            0.001f
        );

        assertEquals(
            0.25f,
            receivedValues[3],
            0.001f
        );

        assertEquals(
            0.15f,
            receivedValues[4],
            0.001f
        );
    }

    @Test
    public void testInspectionReflectsUpdatedSoilValues()
        throws Exception {

        ElvLoader terrain =
            TestTerrainData.flatMedium();

        int gx = 5;
        int gz = 5;

        int index =
            gz * terrain.dimX + gx;

        RayPicking testPicker =
            new RayPicking(
                Collections.emptyList(),
                camera,
                terrain,
                new PaintBrush(terrain),
                new TerrainBrush(terrain),
                new HistoryManager(50)
            );

        final float[] received =
            new float[4];

        testPicker.setPointInspectionListener(
            (
                x,
                z,
                elevation,
                depth,
                sand,
                silt,
                clay
            ) -> {

                received[0] = depth;
                received[1] = sand;
                received[2] = silt;
                received[3] = clay;
            }
        );

        terrain.soilDepth[index] = 1.8f;
        terrain.sand[index] = 0.2f;
        terrain.silt[index] = 0.3f;
        terrain.clay[index] = 0.5f;

        invokePrivate(
            testPicker,
            "inspectPoint",
            new Class<?>[]{
                int[].class
            },
            new int[]{
                gx,
                gz
            }
        );

        assertEquals(
            1.8f,
            received[0],
            0.001f
        );

        assertEquals(
            0.2f,
            received[1],
            0.001f
        );

        assertEquals(
            0.3f,
            received[2],
            0.001f
        );

        assertEquals(
            0.5f,
            received[3],
            0.001f
        );
    }


    @Test
    public void testKnownVisibleTerrainCanBePicked()
        throws Exception {

        TestGdxSetup.runOnGdxThread(() -> {

            ElvLoader terrain =
                TestTerrainData.flatSmall();

            List<Mesh> meshes =
                TerrainMeshBuilder.buildTerrain(
                    terrain,
                    terrain.heights
                );

            float centreX =
                (terrain.dimX - 1)
                    * terrain.gridSpacing
                    * 0.5f;

            float centreZ =
                (terrain.dimY - 1)
                    * terrain.gridSpacing
                    * 0.5f;

            int centreGridX =
                terrain.dimX / 2;

            int centreGridZ =
                terrain.dimY / 2;

            int centreIndex =
                centreGridZ * terrain.dimX
                    + centreGridX;

            float centreY =
                terrain.heights[centreIndex];

            int screenWidth =
                Gdx.graphics.getWidth();

            int screenHeight =
                Gdx.graphics.getHeight();

            PerspectiveCamera testCamera =
                new PerspectiveCamera(
                    67f,
                    screenWidth,
                    screenHeight
                );

            testCamera.position.set(
                centreX,
                centreY + 10f,
                centreZ + 10f
            );

            testCamera.lookAt(
                centreX,
                centreY,
                centreZ
            );

            testCamera.up.set(
                Vector3.Y
            );

            testCamera.near = 0.1f;
            testCamera.far = 1000f;
            testCamera.update();

            RayPicking testPicker =
                createPickerWithRealTerrain(
                    terrain,
                    testCamera,
                    meshes
                );

            int screenCentreX =
                screenWidth / 2;

            int screenCentreY =
                screenHeight / 2;

            try {
                Integer tile =
                    (Integer)
                        invokePrivate(
                            testPicker,
                            "pickTile",
                            new Class<?>[]{
                                int.class,
                                int.class
                            },
                            screenCentreX,
                            screenCentreY
                        );

                assertNotNull(tile);

                Vector3 hit =
                    (Vector3)
                        invokePrivate(
                            testPicker,
                            "intersectTile",
                            new Class<?>[]{
                                int.class,
                                int.class,
                                int.class
                            },
                            tile,
                            screenCentreX,
                            screenCentreY
                        );

                assertNotNull(hit);

                int[] grid =
                    (int[])
                        invokePrivate(
                            testPicker,
                            "getGridPoint",
                            new Class<?>[]{
                                Vector3.class
                            },
                            hit
                        );

                assertTrue(
                    grid[0] >= 0 &&
                    grid[0] < terrain.dimX
                );

                assertTrue(
                    grid[1] >= 0 &&
                    grid[1] < terrain.dimY
                );

            } catch (Exception e) {
                throw new RuntimeException(e);

            } finally {
                for (Mesh mesh : meshes) {
                    mesh.dispose();
                }
            }
        });
    }

    @Test
    public void testPickingOutsideTerrainReturnsNoTile()
        throws Exception {

        TestGdxSetup.runOnGdxThread(() -> {

            ElvLoader terrain =
                TestTerrainData.flatSmall();

            List<Mesh> meshes =
                TerrainMeshBuilder.buildTerrain(
                    terrain,
                    terrain.heights
                );

            PerspectiveCamera testCamera =
                new PerspectiveCamera(
                    67f,
                    800,
                    600
                );

            testCamera.position.set(
                1.5f,
                8.0f,
                1.0f
            );

            testCamera.lookAt(
                1.5f,
                1.0f,
                1.0f
            );

            testCamera.near = 0.1f;
            testCamera.far = 100f;
            testCamera.update();

            RayPicking testPicker =
                createPickerWithRealTerrain(
                    terrain,
                    testCamera,
                    meshes
                );

            try {
                Integer tile =
                    (Integer)
                        invokePrivate(
                            testPicker,
                            "pickTile",
                            new Class<?>[]{
                                int.class,
                                int.class
                            },
                            -10000,
                            -10000
                        );

                assertNull(
                    tile
                );

            } catch (Exception e) {
                throw new RuntimeException(e);

            } finally {
                for (Mesh mesh : meshes) {
                    mesh.dispose();
                }
            }
        });
    }
}

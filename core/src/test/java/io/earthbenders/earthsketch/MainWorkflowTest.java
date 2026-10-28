package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class MainWorkflowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void initialiseGdx() {
        TestGdxSetup.init();
    }

    // ==============================================================
    // Reflection helpers
    // ==============================================================

    private static Object getField(Object object, String name)
        throws Exception {

        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static boolean getBooleanField(Object object, String name)
        throws Exception {

        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(object);
    }

    private static float getFloatField(Object object, String name)
        throws Exception {

        Class<?> currentClass = object.getClass();

        while (currentClass != null) {
            try {
                Field field =
                    currentClass.getDeclaredField(name);

                field.setAccessible(true);

                return field.getFloat(object);

            } catch (NoSuchFieldException e) {
                currentClass =
                    currentClass.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static void setField(
        Object object,
        String name,
        Object value
    ) throws Exception {

        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void setBooleanField(
        Object object,
        String name,
        boolean value
    ) throws Exception {

        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(object, value);
    }

    private static Object invokePrivate(
        Object object,
        String name,
        Class<?>[] parameterTypes,
        Object... arguments
    ) throws Exception {

        Method method =
            object.getClass().getDeclaredMethod(
                name,
                parameterTypes
            );

        method.setAccessible(true);

        return method.invoke(object, arguments);
    }

    // ==============================================================
    // Test terrain helper
    // ==============================================================

    private static ElvLoader createTerrain(
        int dimX,
        int dimY,
        float spacing,
        float baseHeight
    ) {

        ElvLoader loader = new ElvLoader();

        loader.dimX = dimX;
        loader.dimY = dimY;
        loader.gridSpacing = spacing;
        loader.latitude = -33.9f;

        int size = dimX * dimY;

        loader.heights = new float[size];
        loader.soilDepth = new float[size];
        loader.sand = new float[size];
        loader.silt = new float[size];
        loader.clay = new float[size];
        loader.maxDepth = new float[size];
        loader.smoothHeights = new float[size];

        for (int i = 0; i < size; i++) {
            loader.heights[i] =
                baseHeight + (i * 0.25f);

            loader.soilDepth[i] = 0.5f;

            loader.sand[i] = 0.6f;
            loader.silt[i] = 0.3f;
            loader.clay[i] = 0.1f;

            loader.maxDepth[i] = 2.0f;
            loader.smoothHeights[i] =
                loader.heights[i];
        }

        return loader;
    }

    // ==============================================================
    // BACKLOG ITEM 6
    // Terrain replacement
    // ==============================================================

    @Test
    public void testTerrainReplacementUpdatesActiveTerrain()
        throws Exception {

        TestGdxSetup.runOnGdxThread(() -> {

            Main main = new Main();

            try {
                ElvLoader terrainA =
                    createTerrain(
                        4,
                        3,
                        1.0f,
                        0.0f
                    );

                ElvLoader terrainB =
                    createTerrain(
                        8,
                        6,
                        2.0f,
                        20.0f
                    );

                File fileA =
                    tempFolder.newFile("terrainA.elv");

                File fileB =
                    tempFolder.newFile("terrainB.elv");

                invokePrivate(
                    main,
                    "adoptLoader",
                    new Class<?>[]{
                        ElvLoader.class,
                        File.class
                    },
                    terrainA,
                    fileA
                );

                Object firstLoader =
                    getField(main, "loader");

                PerspectiveCamera firstCamera =
                    (PerspectiveCamera)
                        getField(main, "camera");

                float firstFar =
                    firstCamera.far;

                invokePrivate(
                    main,
                    "adoptLoader",
                    new Class<?>[]{
                        ElvLoader.class,
                        File.class
                    },
                    terrainB,
                    fileB
                );

                ElvLoader activeLoader =
                    (ElvLoader)
                        getField(main, "loader");

                PerspectiveCamera secondCamera =
                    (PerspectiveCamera)
                        getField(main, "camera");

                float terrainSize =
                    getFloatField(main, "terrainSize");

                assertSame(
                    terrainA,
                    firstLoader
                );

                assertSame(
                    terrainB,
                    activeLoader
                );

                assertEquals(
                    8,
                    activeLoader.dimX
                );

                assertEquals(
                    6,
                    activeLoader.dimY
                );

                assertNotSame(
                    firstCamera,
                    secondCamera
                );

                assertNotEquals(
                    firstFar,
                    secondCamera.far,
                    0.001f
                );

                assertEquals(
                    terrainSize * 4f,
                    secondCamera.far,
                    0.001f
                );

                @SuppressWarnings("unchecked")
                List<?> terrainMeshes =
                    (List<?>)
                        getField(
                            main,
                            "terrainMeshes"
                        );

                assertNotNull(
                    terrainMeshes
                );

                assertFalse(
                    terrainMeshes.isEmpty()
                );

                PaintBrush paintBrush =
                    (PaintBrush)
                        getField(
                            main,
                            "paintBrush"
                        );

                RayPicking rayPicker =
                    (RayPicking)
                        getField(
                            main,
                            "rayPicker"
                        );

                assertSame(
                    terrainB,
                    getField(
                        paintBrush,
                        "loader"
                    )
                );

                assertSame(
                    terrainB,
                    getField(
                        rayPicker,
                        "loader"
                    )
                );

            } catch (Exception e) {
                throw new RuntimeException(e);

            } finally {
                main.dispose();
            }
        });
    }

    // ==============================================================
    // BACKLOG ITEMS 8 + 10
    // Save persists current data and clears dirty state
    // ==============================================================

    @Test
    public void testSaveCurrentFilePersistsChangesAndClearsDirtyState()
        throws Exception {

        Main main = new Main();

        File file =
            tempFolder.newFile(
                "save_test.elv"
            );

        ElvLoader loader =
            createTerrain(
                4,
                3,
                1.0f,
                1.0f
            );

        loader.soilDepth[0] = 1.25f;
        loader.sand[0] = 0.7f;
        loader.silt[0] = 0.2f;
        loader.clay[0] = 0.1f;

        setField(
            main,
            "loader",
            loader
        );

        setField(
            main,
            "currentFile",
            file
        );

        setBooleanField(
            main,
            "hasUnsavedChanges",
            true
        );

        boolean result =
            (Boolean)
                invokePrivate(
                    main,
                    "saveCurrentFile",
                    new Class<?>[]{}
                );

        assertTrue(result);

        assertFalse(
            getBooleanField(
                main,
                "hasUnsavedChanges"
            )
        );

        ElvLoader reopened =
            new ElvLoader();

        reopened.load(
            file.getAbsolutePath()
        );

        assertEquals(
            1.25f,
            reopened.soilDepth[0],
            0.001f
        );

        assertEquals(
            0.7f,
            reopened.sand[0],
            0.001f
        );

        assertEquals(
            0.2f,
            reopened.silt[0],
            0.001f
        );

        assertEquals(
            0.1f,
            reopened.clay[0],
            0.001f
        );

        main.dispose();
    }

    // ==============================================================
    // BACKLOG ITEM 9
    // Autosave target must be separate from original
    // ==============================================================

    @Test
    public void testAutosaveUsesSeparateRecoveryLocation()
        throws Exception {

        Main main = new Main();

        File directory =
            tempFolder.newFolder(
                "autosave_test"
            );

        File terrainFile =
            new File(
                directory,
                "mountain.elv"
            );

        File autosaveFile =
            (File)
                invokePrivate(
                    main,
                    "getAutosaveFileFor",
                    new Class<?>[]{
                        File.class
                    },
                    terrainFile
                );

        assertNotNull(
            autosaveFile
        );

        assertNotEquals(
            terrainFile.getAbsolutePath(),
            autosaveFile.getAbsolutePath()
        );

        assertEquals(
            ".earthsketch-autosave",
            autosaveFile
                .getParentFile()
                .getName()
        );

        assertEquals(
            "mountain.elv.autosave.elv",
            autosaveFile.getName()
        );

        main.dispose();
    }

    // ==============================================================
    // BACKLOG ITEM 9
    // Background autosave receives independent snapshot
    // ==============================================================

    @Test
    public void testAutosaveSnapshotIsDeepCopy()
        throws Exception {

        Main main = new Main();

        ElvLoader original =
            createTerrain(
                4,
                3,
                1.0f,
                5.0f
            );

        setField(
            main,
            "loader",
            original
        );

        ElvLoader snapshot =
            (ElvLoader)
                invokePrivate(
                    main,
                    "createAutosaveSnapshot",
                    new Class<?>[]{}
                );

        assertNotNull(snapshot);

        assertNotSame(
            original.heights,
            snapshot.heights
        );

        assertNotSame(
            original.soilDepth,
            snapshot.soilDepth
        );

        assertNotSame(
            original.sand,
            snapshot.sand
        );

        assertNotSame(
            original.silt,
            snapshot.silt
        );

        assertNotSame(
            original.clay,
            snapshot.clay
        );

        float snapshotDepth =
            snapshot.soilDepth[0];

        float snapshotSand =
            snapshot.sand[0];

        original.soilDepth[0] = 1.9f;
        original.sand[0] = 0.2f;

        assertEquals(
            snapshotDepth,
            snapshot.soilDepth[0],
            0.001f
        );

        assertEquals(
            snapshotSand,
            snapshot.sand[0],
            0.001f
        );

        main.dispose();
    }

    // ==============================================================
    // BACKLOG ITEM 10
    // Clean document may close immediately
    // ==============================================================

    @Test
    public void testCloseAllowedWhenThereAreNoUnsavedChanges()
        throws Exception {

        Main main = new Main();

        setBooleanField(
            main,
            "hasUnsavedChanges",
            false
        );

        assertTrue(
            main.requestClose()
        );

        main.dispose();
    }

    @Test
    public void testCameraSettingsRecalculateForDifferentTerrainSizes()
        throws Exception {

        TestGdxSetup.runOnGdxThread(() -> {

            Main main = new Main();

            try {
                ElvLoader small =
                    createTerrain(
                        4,
                        4,
                        1.0f,
                        0.0f
                    );

                ElvLoader large =
                    createTerrain(
                        40,
                        40,
                        5.0f,
                        0.0f
                    );

                File smallFile =
                    tempFolder.newFile("small.elv");

                File largeFile =
                    tempFolder.newFile("large.elv");

                invokePrivate(
                    main,
                    "adoptLoader",
                    new Class<?>[]{
                        ElvLoader.class,
                        File.class
                    },
                    small,
                    smallFile
                );

                float smallTerrainSize =
                    getFloatField(main, "terrainSize");

                Object smallController =
                    getField(
                        main,
                        "cameraController"
                    );

                float smallScrollFactor =
                    getFloatField(
                        smallController,
                        "scrollFactor"
                    );

                invokePrivate(
                    main,
                    "adoptLoader",
                    new Class<?>[]{
                        ElvLoader.class,
                        File.class
                    },
                    large,
                    largeFile
                );

                float largeTerrainSize =
                    getFloatField(main, "terrainSize");

                Object largeController =
                    getField(
                        main,
                        "cameraController"
                    );

                float largeScrollFactor =
                    getFloatField(
                        largeController,
                        "scrollFactor"
                    );

                assertTrue(
                    largeTerrainSize >
                        smallTerrainSize
                );

                assertTrue(
                    Math.abs(largeScrollFactor) >
                        Math.abs(smallScrollFactor)
                );

            } catch (Exception e) {
                throw new RuntimeException(e);

            } finally {
                main.dispose();
            }
        });
    }
}
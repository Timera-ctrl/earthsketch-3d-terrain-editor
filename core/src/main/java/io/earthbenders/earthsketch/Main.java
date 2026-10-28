package io.earthbenders.earthsketch;

import java.io.IOException;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.system.MemoryStack;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

public class Main extends ApplicationAdapter {

    private List<Mesh> terrainMeshes;
    private Mesh terrainSkirt;
    private Mesh terrainBottom;

    private ElvLoader loader;
    private File currentFile;
    private boolean hasUnsavedChanges = false;
    private boolean exitApproved = false;

    private DebugPerformanceLogger perfLogger;

    // Autosave configuration and state.
    private static final float AUTOSAVE_INTERVAL_SECONDS = 60f;
    private float autoSaveTimer = 0f;
    private boolean autoSaveInProgress = false;
    private long documentGeneration = 0L;

    private ShaderProgram shaderProgram;
    private PerspectiveCamera camera;
    private CameraInputController cameraController;
    private float terrainSize;

    private PaintBrush paintBrush;
    private RayPicking rayPicker;
    private TerrainBrush terrainBrush;

    //--- Undo/Redo ------------
    private HistoryManager manager;
    private static final int MAX_STACK_SIZE = 50;

    private float[] baselineHeights;

    // ---- Cut box state ----
    private boolean cutEnabled    = false;
    private float   cutMinX       = 0f;
    private float   cutMaxX       = 1f;
    private float   cutMinZ       = 0f;
    private float   cutMaxZ       = 1f;
    private boolean cutKeepInside = true;

    // ---- Shell visibility ----
    private boolean showShell = true;

    // ---- Contour rings ----
    private boolean contourEnabled  = false;
    private float   contourInterval = 2f;
    private float   contourWidth    = 0.15f;

    // ---- Soil nails ----
    private List<Mesh> soilNails; // Changed for Mesh
    private boolean showSoilNails  = false;
    private int     soilNailStride = 4;
    private boolean soilNailsDirty = false;

    // ---- Surface colour view ----
    private TerrainMeshBuilder.ViewMode viewMode =
        TerrainMeshBuilder.ViewMode.COMPOSITION;

    private EarthBendersUI ui;
    private StartupUI startupUi;

    private final ExecutorService autosaveExecutor =
        Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EarthSketch-Autosave");
            thread.setDaemon(true);
            return thread;
        });

    private class BoundedCameraController extends CameraInputController {
        private final Vector3 zoomOffset = new Vector3();
        public BoundedCameraController(Camera camera) { super(camera); }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            float minDistance = terrainSize * 0.2f;
            float maxDistance = terrainSize * 2.5f;
            zoomOffset.set(camera.position).sub(target);
            float currentDistance = zoomOffset.len();
            if (currentDistance == 0f) return true;

            float zoomAmount = amountY * scrollFactor;
            float desiredDistance = currentDistance - zoomAmount;
            float boundedDistance = Math.max(minDistance, Math.min(maxDistance, desiredDistance));

            zoomOffset.nor().scl(boundedDistance);
            camera.position.set(target).add(zoomOffset);
            camera.update();
            return true;
        }
    }

    private File getAutosaveFileFor(File terrainFile) {
        if (terrainFile == null) {
            return null;
        }

        File parent = terrainFile.getParentFile();

        if (parent == null) {
            return null;
        }

        File autosaveDirectory =
            new File(parent, ".earthsketch-autosave");

        return new File(
            autosaveDirectory,
            terrainFile.getName() + ".autosave.elv"
        );
    }

    private File getAutosaveFile() {
        return getAutosaveFileFor(currentFile);
    }

    private ElvLoader createAutosaveSnapshot() {
        if (loader == null) {
            return null;
        }

        ElvLoader snapshot = new ElvLoader();

        snapshot.dimX = loader.dimX;
        snapshot.dimY = loader.dimY;
        snapshot.gridSpacing = loader.gridSpacing;
        snapshot.latitude = loader.latitude;

        // Clone all mutable arrays so the background save sees
        // a stable snapshot even while the user keeps editing.
        snapshot.heights = loader.heights.clone();
        snapshot.soilDepth = loader.soilDepth.clone();
        snapshot.sand = loader.sand.clone();
        snapshot.silt = loader.silt.clone();
        snapshot.clay = loader.clay.clone();

        return snapshot;
    }

    private void autosave() {
        if (
            loader == null
            || currentFile == null
            || !hasUnsavedChanges
            || autoSaveInProgress
        ) {
            return;
        }

        File sourceFile = currentFile;
        File autosaveFile = getAutosaveFileFor(sourceFile);

        if (autosaveFile == null) {
            return;
        }

        File autosaveDirectory = autosaveFile.getParentFile();

        if (
            !autosaveDirectory.exists()
            && !autosaveDirectory.mkdirs()
        ) {
            System.err.println("Could not create autosave directory.");

            if (ui != null) {
                ui.showStatusMessage("Autosave failed.");
            }

            return;
        }

        ElvLoader snapshot = createAutosaveSnapshot();

        if (snapshot == null) {
            return;
        }

        long generationAtStart = documentGeneration;

        File temporaryFile = new File(
            autosaveDirectory,
            sourceFile.getName() + ".autosave.tmp"
        );

        autoSaveInProgress = true;

        autosaveExecutor.submit(() -> {
            try {
                snapshot.save(temporaryFile.getAbsolutePath());

                Gdx.app.postRunnable(() -> {
                    try {
                        if (
                            documentGeneration != generationAtStart
                            || currentFile == null
                            || !currentFile.equals(sourceFile)
                            || !hasUnsavedChanges
                        ) {
                            if (temporaryFile.exists()) {
                                temporaryFile.delete();
                            }
                            return;
                        }

                        try {
                            Files.move(
                                temporaryFile.toPath(),
                                autosaveFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                            );
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(
                                temporaryFile.toPath(),
                                autosaveFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            );
                        }

                        if (ui != null) {
                            ui.showStatusMessage(
                                "Autosaved " + sourceFile.getName()
                            );
                        }

                    } catch (IOException e) {
                        e.printStackTrace();

                        if (ui != null) {
                            ui.showStatusMessage("Autosave failed.");
                        }

                    } finally {
                        autoSaveInProgress = false;

                        if (temporaryFile.exists()) {
                            temporaryFile.delete();
                        }
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();

                Gdx.app.postRunnable(() -> {
                    autoSaveInProgress = false;

                    if (temporaryFile.exists()) {
                        temporaryFile.delete();
                    }

                    if (ui != null) {
                        ui.showStatusMessage("Autosave failed.");
                    }
                });
            }
        });
    }

    private void deleteAutosaveFile() {
        File autosaveFile = getAutosaveFile();

        if (
            autosaveFile != null
            && autosaveFile.exists()
            && !autosaveFile.delete()
        ) {
            System.err.println(
                "Could not remove old autosave file: "
                    + autosaveFile.getAbsolutePath()
            );
        }
    }

    @Override
    public void create() {
        createTerrainShader();
        perfLogger = new DebugPerformanceLogger();
        startupUi = new StartupUI(this::requestInitialTerrainLoad);
        Gdx.input.setInputProcessor(startupUi.getStage());
    }

    private void requestInitialTerrainLoad() {
        File selectedFile = chooseTerrainFile();
        if (selectedFile == null) return;
        loadTerrain(selectedFile);
    }

    private File chooseTerrainFile() {
        String result = TinyFileDialogs.tinyfd_openFileDialog(
            "Open EarthSketch Terrain", "", null, "ELV files", false);
        if (result == null) return null;

        File selectedFile = new File(result);
        if (!selectedFile.getName().toLowerCase().endsWith(".elv")) {
            showErrorDialog("Invalid File", "Please select an .elv file.");
            return null;
        }
        return selectedFile;
    }

    private void disposeTerrainResources() {
        if (terrainMeshes != null) {
            for (Mesh mesh : terrainMeshes) if (mesh != null) mesh.dispose();
            terrainMeshes = null;
        }
        if (terrainSkirt != null)  { terrainSkirt.dispose();  terrainSkirt = null; }
        if (terrainBottom != null) { terrainBottom.dispose(); terrainBottom = null; }
        if (soilNails != null) {
                for (Mesh m : soilNails) m.dispose();
                soilNails = null;
            }
        }

    private void setupInputProcessors() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(ui.getStage());
        multiplexer.addProcessor(rayPicker);
        multiplexer.addProcessor(cameraController);
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void showLoadFailure(File selectedFile, IOException e) {
        System.out.println(
            "Could not load ELV file: "
                + selectedFile.getAbsolutePath()
        );

        e.printStackTrace();

        String reason = e.getMessage();

        if (reason == null || reason.isBlank()) {
            reason = "The file could not be read.";
        }

        showErrorDialog(
            "Load Failed",
            "EarthSketch could not load the selected terrain."
                + "\n\nReason: "
                + reason
        );
    }

    private void loadTerrain(File selectedFile) {
        File loadSource = chooseRecoverySource(selectedFile);

        boolean recoveringAutosave =
            !loadSource.equals(selectedFile);

        ElvLoader newLoader = new ElvLoader();

        try {
            newLoader.load(loadSource.getAbsolutePath());

        } catch (IOException e) {

            if (recoveringAutosave) {
                System.err.println(
                    "Autosave recovery failed: " + e.getMessage()
                );

                File autosaveFile =
                    getAutosaveFileFor(selectedFile);

                if (
                    autosaveFile != null
                    && autosaveFile.exists()
                ) {
                    autosaveFile.delete();
                }

                newLoader = new ElvLoader();

                try {
                    newLoader.load(
                        selectedFile.getAbsolutePath()
                    );

                    recoveringAutosave = false;

                    showErrorDialog(
                        "Autosave Recovery Failed",
                        "The autosave could not be recovered. "
                            + "EarthSketch loaded the original terrain instead."
                    );

                } catch (IOException originalError) {
                    showLoadFailure(selectedFile, originalError);
                    return;
                }

            } else {
                showLoadFailure(selectedFile, e);
                return;
            }
        }

        adoptLoader(newLoader, selectedFile);

        hasUnsavedChanges = recoveringAutosave;

        if (recoveringAutosave) {
            ui.showStatusMessage(
                "Recovered autosave for " + selectedFile.getName()
            );
        } else {
            ui.showStatusMessage(
                "Loaded " + selectedFile.getName()
            );
        }
    }

    private void adoptLoader(ElvLoader newLoader, File sourceFile) {
        disposeTerrainResources();
        loader = newLoader;
        baselineHeights = newLoader.heights.clone();

        // A newly loaded document always starts in a neutral, predictable state.
        cutEnabled = false;
        cutKeepInside = true;
        showShell = true;
        contourEnabled = false;
        showSoilNails = false;
        viewMode = TerrainMeshBuilder.ViewMode.COMPOSITION;

        documentGeneration++;
        autoSaveTimer = 0f;

        currentFile = sourceFile;
        hasUnsavedChanges = (sourceFile == null);

        if (startupUi != null) { startupUi.dispose(); startupUi = null; }

        terrainMeshes = TerrainMeshBuilder.buildTerrain(loader, baselineHeights);
        terrainSkirt  = TerrainMeshBuilder.buildTerrainSkirt(loader);
        terrainBottom = TerrainMeshBuilder.buildTerrainBottom(loader);

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float height : loader.heights) {
            if (height < minH) minH = height;
            if (height > maxH) maxH = height;
        }

        float terrainW = (loader.dimX - 1) * loader.gridSpacing;
        float terrainH = (loader.dimY - 1) * loader.gridSpacing;
        float terrainHeight = maxH - minH;
        float centerX = terrainW / 2f;
        float centerZ = terrainH / 2f;
        float centerHeight = (minH + maxH) / 2f;
        terrainSize = (float) Math.sqrt(terrainH * terrainH + terrainW * terrainW + terrainHeight * terrainHeight);

        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(centerX, centerHeight + terrainSize * 0.5f, centerZ + terrainSize * 0.8f);
        camera.lookAt(centerX, centerHeight, centerZ);
        camera.near = 1f;
        camera.far = terrainSize * 4f;
        camera.update();

        cameraController = new BoundedCameraController(camera);
        cameraController.target.set(centerX, centerHeight, centerZ);
        cameraController.scrollFactor = -terrainSize * 0.01f;
        cameraController.translateUnits = terrainSize * 0.1f;
        cameraController.rotateButton = Buttons.LEFT;
        cameraController.translateButton = Buttons.MIDDLE;

        manager = new HistoryManager(MAX_STACK_SIZE);

        paintBrush   = new PaintBrush(loader);
        terrainBrush = new TerrainBrush(loader);

        rayPicker = new RayPicking(terrainMeshes, camera, loader, paintBrush, terrainBrush, manager);
        rayPicker.setBaselineHeights(baselineHeights);
        rayPicker.setRefreshTerrainColorsCallback(this::refreshTerrainColors);
        rayPicker.setTerrainChangeListener(() -> {
            hasUnsavedChanges = true;
            soilNailsDirty = true;
        });
        rayPicker.setGeometryChangeListener(this::onGeometryChanged);

        if (ui != null) ui.dispose();

        ui = new EarthBendersUI(paintBrush, rayPicker, cameraController,
            this::requestOpenTerrain, this::saveCurrentFile, this::saveAs,
            this::generateTerrainComposition);

        rayPicker.setPointInspectionListener(ui::showPointInfo);
        // rayPicker.setHoverListener(ui);
        ui.setTerrainBrush(terrainBrush);

        ui.setCutListener((enabled, minX, maxX, minZ, maxZ, keepInside) -> {
            cutEnabled    = enabled;
            cutMinX       = minX;
            cutMaxX       = maxX;
            cutMinZ       = minZ;
            cutMaxZ       = maxZ;
            cutKeepInside = keepInside;
        });
        ui.setShellToggleListener(v -> showShell = v);
        ui.setContourListener((enabled, interval, width) -> {
            contourEnabled  = enabled;
            contourInterval = interval;
            contourWidth    = width;
        });
        ui.setCutBakeListener(this::bakeCut);
        ui.setSoilNailListener((enabled, stride) -> {
            showSoilNails  = enabled;
            soilNailStride = stride;
            soilNailsDirty = true;
        });
        ui.setViewModeListener(mode -> {
            viewMode = mode;
            // Refresh colours immediately so the mode switch is visible
            // the moment the user clicks.
            refreshTerrainColors();
        });
        ui.setUndoRedoActions(this::undoLastEdit, this::redoLastEdit);

        float worldMax = (loader.dimX - 1) * loader.gridSpacing;
        ui.setCutPlaneBounds(0f, worldMax);

        cutEnabled    = false;
        cutKeepInside = true;
        cutMinX       = 0f;
        cutMaxX       = worldMax;
        cutMinZ       = 0f;
        cutMaxZ       = worldMax;

        // Initial colour pass, honouring the current view mode.
        refreshTerrainColors();

        soilNailsDirty = true;

        setupInputProcessors();

        if(System.getProperty("earthsketch.perftest")!=null){
            System.out.println("Starting stress test...");
            runPaintStressTest();
        }

        System.out.println("Loaded terrain: " +
            (sourceFile != null ? sourceFile.getAbsolutePath() : "(in-memory crop)"));
        System.out.println("NAVIGATION MODE");
    }
    // need to add a line run{} inside build.gradle inside lwjgl3 folder, add the following line to let this run 
    // systemProperty 'earthsketch.perftest', System.getProperty('earthsketch.perftest')
    private void runPaintStressTest(){
        int[] radiiToTest = {5, 15, 30, 50};
        boolean[] blendOptions = {false, true};
        boolean[] colorUpdateOptions = {false, true};

        // Save original soil state so each configuration starts from the same clean baseline.
        float[] originalDepth = loader.soilDepth.clone();
        float[] originalSand = loader.sand.clone();
        float[] originalSilt = loader.silt.clone();
        float[] originalClay = loader.clay.clone();

        for (int radius : radiiToTest) {
            for (boolean blend : blendOptions) {
                for (boolean colorUpdate : colorUpdateOptions) {

                    // Reset terrain soil state before every configuration, for a fair comparison.
                    System.arraycopy(originalDepth, 0, loader.soilDepth, 0, originalDepth.length);
                    System.arraycopy(originalSand, 0, loader.sand, 0, originalSand.length);
                    System.arraycopy(originalSilt, 0, loader.silt, 0, originalSilt.length);
                    System.arraycopy(originalClay, 0, loader.clay, 0, originalClay.length);

                    paintBrush.setRadius(radius);
                    paintBrush.setBlendEnabled(blend); 
                    paintBrush.beginStroke();

                    for (int x = 0; x < loader.dimX; x += 2) {
                        long frameStart = System.nanoTime();

                        paintBrush.paintAt(x, loader.dimY / 2);

                        if (colorUpdate) {
                            refreshTerrainColors(); 
                        }

                        float deltaTime = (System.nanoTime() - frameStart) / 1_000_000_000f;
                        perfLogger.logFrame(loader.dimX, radius, blend, colorUpdate, deltaTime);
                    }

                    paintBrush.endStroke();
                }
            }
        }

        // Restore the terrain to its original, unpainted state after all sweeps.
        System.arraycopy(originalDepth, 0, loader.soilDepth, 0, originalDepth.length);
        System.arraycopy(originalSand, 0, loader.sand, 0, originalSand.length);
        System.arraycopy(originalSilt, 0, loader.silt, 0, originalSilt.length);
        System.arraycopy(originalClay, 0, loader.clay, 0, originalClay.length);
        refreshTerrainColors();

        try {
            perfLogger.writeToFile("perf_results.csv");
            System.out.println("Performance results appended to assets/perf_results.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * Repaints the terrain vertex colours using the current view mode.
     * Called whenever soil data changes or the user switches modes.
     */
    private void refreshTerrainColors() {
        if (terrainMeshes == null || loader == null) return;
        TerrainMeshBuilder.updateTerrainColors(terrainMeshes, loader, viewMode);
    }

    private void undoLastEdit() {
        if (manager == null || loader == null) return;
        manager.undo(loader);
        onGeometryChanged();
        refreshTerrainColors();
    }

    private void redoLastEdit() {
        if (manager == null || loader == null) return;
        manager.redo(loader);
        onGeometryChanged();
        refreshTerrainColors();
    }

    private void bakeCut() {
        if (!cutEnabled) {
            showErrorDialog("No slice active", "Enable the slice first, then bake.");
            return;
        }
        if (!cutKeepInside) {
            showErrorDialog("Cannot bake", "Baking requires Keep Inside mode. A hole in the middle cannot become a terrain.");
            return;
        }

        int gxMin = Math.round(cutMinX / loader.gridSpacing);
        int gxMax = Math.round(cutMaxX / loader.gridSpacing);
        int gzMin = Math.round(cutMinZ / loader.gridSpacing);
        int gzMax = Math.round(cutMaxZ / loader.gridSpacing);

        gxMin = Math.max(0, Math.min(loader.dimX - 1, gxMin));
        gxMax = Math.max(0, Math.min(loader.dimX - 1, gxMax));
        gzMin = Math.max(0, Math.min(loader.dimY - 1, gzMin));
        gzMax = Math.max(0, Math.min(loader.dimY - 1, gzMax));
        if (gxMin > gxMax) { int t = gxMin; gxMin = gxMax; gxMax = t; }
        if (gzMin > gzMax) { int t = gzMin; gzMin = gzMax; gzMax = t; }

        ElvLoader cropped = loader.cropTo(gxMin, gzMin, gxMax, gzMax);
        if (cropped == null) {
            showErrorDialog("Slice too small", "The slice must cover at least 2x2 grid points.");
            return;
        }
        adoptLoader(cropped, null);
    }

    private void generateTerrainComposition() {
        if (loader == null) return;

        int generatedPoints = loader.generateTerrainBasedComposition();
        if (generatedPoints > 0) {
            refreshTerrainColors();
            hasUnsavedChanges = true;
            soilNailsDirty = true;
        }
        System.out.println("Generated terrain composition for " + generatedPoints + " points.");
    }

    private void requestOpenTerrain() {
        if (!hasUnsavedChanges) { openTerrainChooser(); return; }

        ui.showUnsavedChangesDialog(
            "Save changes to the current terrain before opening another file?",
            () -> { if (saveCurrentFile()) openTerrainChooser(); },
            this::openTerrainChooser,
            () -> {}
        );
    }

    private void openTerrainChooser() {
        File selectedFile = chooseTerrainFile();
        if (selectedFile == null) return;
        loadTerrain(selectedFile);
    }

    private void createTerrainShader() {
        String vertexShader =
            "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec3 v_worldPos;\n" +
            "varying float v_baselineY;\n" +
            "varying float v_baselineValid;\n" +
            "void main(){\n" +
            "   v_color = a_color;\n" +
            "   v_normal = a_normal;\n" +
            "   v_worldPos = a_position.xyz;\n" +
            "   v_baselineY = a_texCoord0.x;\n" +
            "   v_baselineValid = a_texCoord0.y;\n" +
            "   gl_Position = u_projTrans * a_position;\n" +
            "}";

        String fragmentShader =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec4 v_color;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec3 v_worldPos;\n" +
            "varying float v_baselineY;\n" +
            "varying float v_baselineValid;\n" +
            "uniform vec3  u_lightDir;\n" +
            "uniform int   u_cutEnabled;\n" +
            "uniform float u_cutMinX;\n" +
            "uniform float u_cutMaxX;\n" +
            "uniform float u_cutMinZ;\n" +
            "uniform float u_cutMaxZ;\n" +
            "uniform int   u_cutKeepInside;\n" +
            "uniform int   u_contourEnabled;\n" +
            "uniform float u_contourInterval;\n" +
            "uniform float u_contourWidth;\n" +
            "uniform int   u_disableBackFaceColor;\n" +
            "void main(){\n" +
            "   if (u_cutEnabled == 1) {\n" +
            "       float inX = step(u_cutMinX, v_worldPos.x) * step(v_worldPos.x, u_cutMaxX);\n" +
            "       float inZ = step(u_cutMinZ, v_worldPos.z) * step(v_worldPos.z, u_cutMaxZ);\n" +
            "       float inside = inX * inZ;\n" +
            "       if (u_cutKeepInside == 1) {\n" +
            "           if (inside < 0.5) discard;\n" +
            "       } else {\n" +
            "           if (inside > 0.5) discard;\n" +
            "       }\n" +
            "   }\n" +
            "   vec3 base = v_color.rgb;\n" +
            "   if (u_disableBackFaceColor == 0 && !gl_FrontFacing) base = vec3(0.22, 0.16, 0.10);\n" +
            "   float b = max(dot(normalize(v_normal), normalize(u_lightDir)), 0.15);\n" +
            "   b = b * 0.65 + 0.35;\n" +
            "   vec3 lit = base * b;\n" +
            "   if (u_contourEnabled == 1 && v_baselineValid > 0.5) {\n" +
            "       float dug = v_baselineY - v_worldPos.y;\n" +
            "       float absDug = abs(dug);\n" +
            "       if (absDug > 0.05) {\n" +
            "           float phase = mod(absDug, u_contourInterval);\n" +
            "           if (phase < u_contourWidth) {\n" +
            "               vec3 ringColor = (dug > 0.0)\n" +
            "                   ? vec3(0.05, 0.05, 0.05)\n" +
            "                   : vec3(0.9, 0.8, 0.4);\n" +
            "               lit = mix(lit, ringColor, 0.8);\n" +
            "           }\n" +
            "       }\n" +
            "   }\n" +
            "   gl_FragColor = vec4(lit, v_color.a);\n" +
            "}";

        shaderProgram = new ShaderProgram(vertexShader, fragmentShader);
        if (!shaderProgram.isCompiled()) {
            throw new RuntimeException("Shader compile error: " + shaderProgram.getLog());
        }
    }

    private void rebuildSoilNailsIfNeeded() {
            if (!showSoilNails) {
                if (soilNails != null) {
                    for (Mesh m : soilNails) m.dispose();
                    soilNails = null;
                }
                soilNailsDirty = false;
                return;
            }
            if (Gdx.input.isButtonPressed(Buttons.LEFT)) return;
            if (!soilNailsDirty && soilNails != null) return;

            if (soilNails != null) {
                for (Mesh m : soilNails) m.dispose();
                soilNails = null;
            }
            soilNails = SoilNailBuilder.build(loader, soilNailStride);
            soilNailsDirty = false;
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.18f, 0.20f, 0.24f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (startupUi != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            startupUi.update();
            startupUi.render();
            return;
        }

        if (camera == null || shaderProgram == null || ui == null) return;

        // Periodically autosave modified terrain data.
        if (hasUnsavedChanges) {
            autoSaveTimer += Gdx.graphics.getDeltaTime();

            if (autoSaveTimer >= AUTOSAVE_INTERVAL_SECONDS) {
                autoSaveTimer = 0f;
                autosave();
            }
        } else {
            autoSaveTimer = 0f;
        }

        rebuildSoilNailsIfNeeded();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        cameraController.update();
        camera.update();

        shaderProgram.bind();
        shaderProgram.setUniformMatrix("u_projTrans", camera.combined);
        shaderProgram.setUniformf("u_lightDir", new Vector3(0.5f, 1f, 0.3f).nor());

        shaderProgram.setUniformi("u_cutEnabled", cutEnabled ? 1 : 0);
        shaderProgram.setUniformf("u_cutMinX", cutMinX);
        shaderProgram.setUniformf("u_cutMaxX", cutMaxX);
        shaderProgram.setUniformf("u_cutMinZ", cutMinZ);
        shaderProgram.setUniformf("u_cutMaxZ", cutMaxZ);
        shaderProgram.setUniformi("u_cutKeepInside", cutKeepInside ? 1 : 0);

        shaderProgram.setUniformi("u_contourEnabled", contourEnabled ? 1 : 0);
        shaderProgram.setUniformf("u_contourInterval", contourInterval);
        shaderProgram.setUniformf("u_contourWidth", contourWidth);

        shaderProgram.setUniformi("u_disableBackFaceColor", 0);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        for (Mesh mesh : terrainMeshes) mesh.render(shaderProgram, GL20.GL_TRIANGLES);
        if (showShell) {
            terrainBottom.render(shaderProgram, GL20.GL_TRIANGLES);
            terrainSkirt.render(shaderProgram, GL20.GL_TRIANGLES);
        }

        if (showSoilNails && soilNails != null) {
            shaderProgram.setUniformi("u_cutEnabled", 0);
            shaderProgram.setUniformi("u_contourEnabled", 0);
            shaderProgram.setUniformi("u_disableBackFaceColor", 1);
            for (Mesh m : soilNails) {
                m.render(shaderProgram, GL20.GL_TRIANGLES);
            }
            shaderProgram.setUniformi("u_disableBackFaceColor", 0);
            shaderProgram.setUniformi("u_cutEnabled", cutEnabled ? 1 : 0);
            shaderProgram.setUniformi("u_contourEnabled", contourEnabled ? 1 : 0);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        ui.update();
        ui.render();
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            camera.update();
        }
        if (ui != null) ui.resize(width, height);
        if (startupUi != null) startupUi.resize(width, height);
    }

    @Override
    public void dispose() {
        autosaveExecutor.shutdownNow();
        disposeTerrainResources();
        if (shaderProgram != null) shaderProgram.dispose();
        if (ui != null) ui.dispose();
        if (startupUi != null) startupUi.dispose();
    }

    private boolean saveCurrentFile() {
        if (currentFile == null) return saveAs();
        try {
            loader.save(currentFile.getAbsolutePath());
            documentGeneration++;
            hasUnsavedChanges = false;
            autoSaveTimer = 0f;

            deleteAutosaveFile();

            if (ui != null) {
                ui.showStatusMessage(
                    "Saved " + currentFile.getName()
                );
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog(
                "Save Failed",
                "EarthSketch could not save the current terrain."
            );
            return false;
        }
    }

    private boolean saveAs() {
        while (true) {
            String result = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save EarthSketch Terrain As",
                currentFile != null ? currentFile.getName() : "terrain.elv",
                null, "ELV files");

            if (result == null) return false;
            if (!result.toLowerCase().endsWith(".elv")) result += ".elv";

            File selectedFile = new File(result);
            try {
                File oldAutosaveFile = getAutosaveFile();

                loader.save(selectedFile.getAbsolutePath());

                currentFile = selectedFile;
                documentGeneration++;
                hasUnsavedChanges = false;
                autoSaveTimer = 0f;

                if (
                    oldAutosaveFile != null
                    && oldAutosaveFile.exists()
                ) {
                    oldAutosaveFile.delete();
                }

                deleteAutosaveFile();

                if (ui != null) {
                    ui.showStatusMessage(
                        "Saved as " + selectedFile.getName()
                    );
                }

                return true;
            } catch (IOException e) {
                e.printStackTrace();
                showErrorDialog("Save Failed", "EarthSketch could not save the selected file.");
                return false;
            }
        }
    }

    private void showErrorDialog(String title, String message) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            TinyFileDialogs.tinyfd_messageBox(
                stack.UTF8(title), stack.UTF8(message),
                stack.UTF8("ok"), stack.UTF8("error"), true);
        }
    }

    public boolean requestClose() {
        if (exitApproved) return true;
        if (!hasUnsavedChanges) return true;
        if (ui == null) return true;

        ui.showUnsavedChangesDialog(
            "Save changes to the current terrain before closing EarthSketch?",
            () -> { if (saveCurrentFile()) { exitApproved = true; Gdx.app.exit(); } },
            () -> { exitApproved = true; Gdx.app.exit(); },
            () -> {}
        );
        return false;
    }

    private void onGeometryChanged() {
        if (terrainMeshes == null) return;

        TerrainMeshBuilder.updateTerrainGeometry(terrainMeshes, loader);

        if (terrainSkirt != null)  terrainSkirt.dispose();
        if (terrainBottom != null) terrainBottom.dispose();
        terrainSkirt  = TerrainMeshBuilder.buildTerrainSkirt(loader);
        terrainBottom = TerrainMeshBuilder.buildTerrainBottom(loader);

        rayPicker.rebuildBounds(terrainMeshes);
        hasUnsavedChanges = true;
        soilNailsDirty = true;

        // Sculpt changes soil depth too, so depth-mode colours must refresh.
        refreshTerrainColors();
    }

    private boolean askRecoverAutosave(
        File originalFile,
        File autosaveFile
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            String message =
                "EarthSketch found newer autosaved work for:\n\n"
                + originalFile.getName()
                + "\n\nRecover the autosaved changes?";

            return TinyFileDialogs.tinyfd_messageBox(
                stack.UTF8("Recover Autosave"),
                stack.UTF8(message),
                stack.UTF8("yesno"),
                stack.UTF8("question"),
                true
            );
        }
    }

    private File chooseRecoverySource(File selectedFile) {
        File autosaveFile =
            getAutosaveFileFor(selectedFile);

        if (
            autosaveFile == null
            || !autosaveFile.isFile()
        ) {
            return selectedFile;
        }

        if (
            selectedFile.lastModified()
                >= autosaveFile.lastModified()
        ) {
            if (!autosaveFile.delete()) {
                System.err.println(
                    "Could not delete stale autosave: "
                        + autosaveFile.getAbsolutePath()
                );
            }

            return selectedFile;
        }

        boolean recover =
            askRecoverAutosave(
                selectedFile,
                autosaveFile
            );

        if (recover) {
            return autosaveFile;
        }

        if (!autosaveFile.delete()) {
            System.err.println(
                "Could not discard autosave: "
                    + autosaveFile.getAbsolutePath()
            );
        }

        return selectedFile;
    }
}

package io.earthbenders.earthsketch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;

import java.util.function.Consumer;

public class EarthBendersUI {

    private final Stage stage;
    private final Skin skin;

    private final PaintBrush paintBrush;
    private final RayPicking rayPicker;
    private final CameraInputController cameraController;
    private final Runnable onLoadFile;
    private final Runnable onSaveFile;
    private final Runnable onSaveAsFile;
    private final Runnable onGenerateTerrainComposition;

    private Table root;
    private Table toolbar;
    private Table paintPanel;
    private ScrollPane paintScrollPane;
    private Label statusLabel;

    // Paint brush controls
    private TextField radiusField;
    private TextField depthField;
    private TextField sandField;
    private TextField siltField;
    private TextField clayField;
    private SoilCompositionPalette soilPalette;
    private TextField strengthField;
    private TextButton randomVariationButton;
    private TextField variationField;
    private TextButton slopeAwareDepthButton;
    private TextButton slopeAwareCompositionButton;
    private TextButton blendButton;
    private TextButton depthAccumulateButton;

    private TextField falloffField;
    private Label falloffStatus;
    private boolean falloffValid = true;

    private Label compositionStatus;
    private Label radiusStatus;
    private Label depthStatus;
    private Label strengthStatus;
    private Label variationStatus;

    private boolean radiusValid = true;
    private boolean depthValid = true;
    private boolean compositionValid = true;
    private boolean strengthValid = true;
    private boolean variationValid = true;

    // ---- Listeners ----
    public interface CutListener {
        void onCutChanged(boolean enabled,
                          float minX, float maxX,
                          float minZ, float maxZ,
                          boolean keepInside);
    }
    public interface CutBakeListener { void onBakeRequested(); }
    public interface ContourListener {
        void onContourChanged(boolean enabled, float interval, float width);
    }
    public interface SoilNailListener {
        void onSoilNailChanged(boolean enabled, int stride);
    }
    public interface ViewModeListener {
        void onViewModeChanged(TerrainMeshBuilder.ViewMode mode);
    }

    private TerrainBrush terrainBrush;
    private CutListener cutListener;
    private CutBakeListener cutBakeListener;
    private ContourListener contourListener;
    private SoilNailListener soilNailListener;
    private ViewModeListener viewModeListener;
    private Consumer<Boolean> shellListener;

    private float terrainMin = 0f, terrainMax = 1f;

    // Sculpt panel
    private Table sculptPanel;
    private ScrollPane sculptScrollPane;
    private Table slicePanel;
    private ScrollPane sliceScrollPane;
    private TextField sculptRadiusField;
    private TextField sculptStrengthField;
    private TextField sculptFalloffField;
    private TextField sculptLevelField;
    private Label sculptRadiusStatus;
    private Label sculptStatus;

    private TextButton sculptDigButton;
    private TextButton sculptFillButton;
    private TextButton sculptFlattenButton;
    private TextButton sculptLevelButton;
    private TextButton sculptSmoothButton;

    // Cut controls
    private TextButton cutToggle;
    private TextButton cutModeToggle;
    private TextField  cutMinXField;
    private TextField  cutMaxXField;
    private TextField  cutMinZField;
    private TextField  cutMaxZField;
    private boolean    cutKeepInside = true;

    // Shell toggle
    private TextButton shellToggle;

    // Contour rings
    private TextButton contourToggle;
    private TextField  contourIntervalField;

    // Soil nails
    private TextButton soilNailToggle;
    private TextField  soilNailStrideField;

    // Toolbar
    private TextButton navigateButton;
    private TextButton paintButton;
    private TextButton sculptButton;
    private TextButton sliceButton;
    private TextButton compositionViewButton;
    private TextButton depthViewButton;
    private Runnable undoAction;
    private Runnable redoAction;

    private enum InteractionMode { NAVIGATE, PAINT, SCULPT, SLICE }
    private InteractionMode interactionMode = InteractionMode.NAVIGATE;

    private boolean sculptRadiusValid   = true;
    private boolean sculptStrengthValid = true;
    private boolean sculptFalloffValid  = true;

    public EarthBendersUI(PaintBrush paintBrush, RayPicking rayPicker,
                          CameraInputController cameraController,
                          Runnable onLoadFile, Runnable onSaveFile,
                          Runnable onSaveAsFile, Runnable onGenerateTerrainComposition) {
        this.paintBrush = paintBrush;
        this.rayPicker = rayPicker;
        this.cameraController = cameraController;
        this.onLoadFile = onLoadFile;
        this.onSaveFile = onSaveFile;
        this.onSaveAsFile = onSaveAsFile;
        this.onGenerateTerrainComposition = onGenerateTerrainComposition;

        stage = new Stage(new ScreenViewport()) {
            private final Vector2 scrollPointer = new Vector2();

            @Override
            public boolean scrolled(float amountX, float amountY) {
                scrollPointer.set(Gdx.input.getX(), Gdx.input.getY());
                screenToStageCoordinates(scrollPointer);
                Actor target = hit(scrollPointer.x, scrollPointer.y, true);

                boolean overToolPanel = target != null
                    && ((paintScrollPane != null
                         && (target == paintScrollPane || target.isDescendantOf(paintScrollPane)))
                        || (sculptScrollPane != null
                         && (target == sculptScrollPane || target.isDescendantOf(sculptScrollPane)))
                        || (sliceScrollPane != null
                         && (target == sliceScrollPane || target.isDescendantOf(sliceScrollPane))));

                if (!overToolPanel) {
                    setScrollFocus(null);
                    return false;
                }

                return super.scrolled(amountX, amountY);
            }
        };
        skin = new Skin();

        createSkin();
        createUI();
    }

    private void createSkin() {
        BitmapFont font = new BitmapFont();
        font.getData().setScale(1.05f);
        skin.add("default-font", font);

        Pixmap roundedPixmap = createRoundedPixmap(64, 64, 16);
        Texture roundedTexture = new Texture(roundedPixmap);
        roundedTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        roundedPixmap.dispose();

        NinePatch roundedPatch = new NinePatch(roundedTexture, 16, 16, 16, 16);
        skin.add("rounded", roundedPatch);

        Pixmap whitePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixmap.setColor(Color.WHITE);
        whitePixmap.fill();
        Texture whiteTexture = new Texture(whitePixmap);
        whitePixmap.dispose();
        skin.add("white", whiteTexture);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.up = roundedDrawable(new Color(0.16f, 0.17f, 0.20f, 1f));
        buttonStyle.over = roundedDrawable(new Color(0.21f, 0.23f, 0.27f, 1f));
        buttonStyle.checked = roundedDrawable(new Color(0.23f, 0.38f, 0.60f, 1f));
        buttonStyle.down = roundedDrawable(new Color(0.18f, 0.30f, 0.48f, 1f));
        buttonStyle.fontColor = new Color(0.92f, 0.94f, 0.97f, 1f);
        buttonStyle.overFontColor = Color.WHITE;
        buttonStyle.checkedFontColor = Color.WHITE;
        skin.add("default", buttonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font,
            new Color(0.92f, 0.94f, 0.97f, 1f));
        skin.add("default", labelStyle);

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = skin.newDrawable("rounded",
            new Color(0.15f, 0.16f, 0.19f, 1f));
        textFieldStyle.disabledBackground =
            skin.newDrawable("rounded",
                new Color(0.10f, 0.11f, 0.13f, 1f));
        textFieldStyle.disabledFontColor = new Color(0.45f, 0.47f, 0.52f, 1f);
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);
        skin.add("default", textFieldStyle);

        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = font;
        windowStyle.titleFontColor = Color.WHITE;
        windowStyle.background = skin.newDrawable("rounded",
            new Color(0.10f, 0.11f, 0.14f, 0.98f));
        skin.add("default", windowStyle);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white",
            new Color(0.10f, 0.11f, 0.14f, 0.96f));
        scrollPaneStyle.vScroll = skin.newDrawable("white",
            new Color(0.15f, 0.16f, 0.19f, 1f));
        scrollPaneStyle.vScrollKnob = skin.newDrawable("rounded",
            new Color(0.45f, 0.48f, 0.55f, 1f));
        skin.add("default", scrollPaneStyle);

        // Tooltip style — small dark bubble with wrapping text.
        TextTooltip.TextTooltipStyle tooltipStyle = new TextTooltip.TextTooltipStyle();
        tooltipStyle.background = skin.newDrawable("rounded",
            new Color(0.05f, 0.06f, 0.08f, 0.96f));
        tooltipStyle.label = new Label.LabelStyle(font,
            new Color(0.94f, 0.94f, 0.94f, 1f));
        tooltipStyle.wrapWidth = 340f;
        skin.add("default", tooltipStyle);
    }

    /**
     * Creates ninepatch and sets colour so buttons can use this instead
     * @param color
     * @return NinePatchDrawable patch which is more rounded than before
     */
    private Drawable roundedDrawable(Color color) {
        NinePatch patch = new NinePatch(skin.get("rounded", NinePatch.class));
        patch.setColor(color);
        return new NinePatchDrawable(patch);
    }

    /**
     * Attaches a hover tooltip to an actor. Shown after the standard
     * scene2d hover delay so it doesn't flash while the user moves
     * the mouse across the toolbar.
     */
    private void addTooltip(Actor actor, String text) {
        if (actor == null || text == null) return;
        TextTooltip tooltip = new TextTooltip(text, skin);
        actor.addListener(tooltip);
    }

    private void createUI() {
        root = new Table();
        root.setFillParent(true);
        root.top();
        stage.addActor(root);

        stage.getRoot().addCaptureListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y,
                                                int pointer, int button) {
                Actor target = event.getTarget();
                boolean insidePanel =
                    target != null
                    && (target == paintScrollPane
                        || target.isDescendantOf(paintScrollPane)
                        || target == sculptScrollPane
                        || target.isDescendantOf(sculptScrollPane)
                        || target == sliceScrollPane
                        || target.isDescendantOf(sliceScrollPane));
                if (!insidePanel) {
                    stage.setKeyboardFocus(null);
                    stage.setScrollFocus(null);
                }
                return false;
            }
        });

        createPaintPanel();
        createSculptPanel();
        createSlicePanel();
        createToolbar();

        root.add(toolbar).growX().height(150f);
        root.row();

        Table content = new Table();
        content.add().grow();

        paintScrollPane = new ScrollPane(paintPanel, skin);
        paintScrollPane.setFadeScrollBars(false);
        paintScrollPane.setScrollingDisabled(true, false);
        paintScrollPane.setOverscroll(false, false);
        paintScrollPane.setFlickScroll(false);
        paintScrollPane.setVisible(false);
        addScrollFocusOnHover(paintScrollPane);

        sculptScrollPane = new ScrollPane(sculptPanel, skin);
        sculptScrollPane.setFadeScrollBars(false);
        sculptScrollPane.setScrollingDisabled(true, false);
        sculptScrollPane.setOverscroll(false, false);
        sculptScrollPane.setFlickScroll(false);
        sculptScrollPane.setVisible(false);
        addScrollFocusOnHover(sculptScrollPane);

        sliceScrollPane = new ScrollPane(slicePanel, skin);
        sliceScrollPane.setFadeScrollBars(false);
        sliceScrollPane.setScrollingDisabled(true, false);
        sliceScrollPane.setOverscroll(false, false);
        sliceScrollPane.setFlickScroll(false);
        sliceScrollPane.setVisible(false);
        addScrollFocusOnHover(sliceScrollPane);

        Stack toolPanelStack = new Stack();
        toolPanelStack.add(paintScrollPane);
        toolPanelStack.add(sculptScrollPane);
        toolPanelStack.add(sliceScrollPane);

        content.add(toolPanelStack)
            .width(290f)
            .growY();

        root.add(content).grow();
    }

    private void createToolbar() {
        toolbar = new Table();
        toolbar.setBackground(skin.newDrawable(
            "white",
            new Color(0.09f, 0.10f, 0.12f, 1f)
        ));

        toolbar.left().top();
        toolbar.pad(8f, 18f, 8f, 18f);

        // ------------------------------------------------------------
        // Top row
        // ------------------------------------------------------------

        Label title = new Label("EarthSketch", skin);
        title.setAlignment(Align.left);

        TextButton loadButton = new TextButton("Load", skin);
        TextButton saveButton = new TextButton("Save", skin);
        TextButton saveAsButton = new TextButton("Save As", skin);

        TextButton undoButton = new TextButton("Undo", skin);
        TextButton redoButton = new TextButton("Redo", skin);

        addTooltip(loadButton,
            "Open an .elv terrain file from disk. "
            + "You'll be prompted to save unsaved changes first if there are any.");
        addTooltip(saveButton,
            "Save the current terrain back to its file. "
            + "Quick-saves without asking for a filename.");
        addTooltip(saveAsButton,
            "Save the current terrain to a new .elv file. "
            + "Asks for a filename and location.");
        addTooltip(undoButton,
            "Undo the last brush or sculpt stroke. "
            + "Shortcut: Ctrl+Z.");
        addTooltip(redoButton,
            "Redo the stroke you just undid. "
            + "Shortcut: Ctrl+Y.");

        loadButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (loadButton.isChecked()) {
                    if (onLoadFile != null) onLoadFile.run();
                    loadButton.setChecked(false);
                }
            }
        });

        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (saveButton.isChecked()) {
                    if (onSaveFile != null) onSaveFile.run();
                    saveButton.setChecked(false);
                }
            }
        });

        saveAsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (saveAsButton.isChecked()) {
                    if (onSaveAsFile != null) onSaveAsFile.run();
                    saveAsButton.setChecked(false);
                }
            }
        });

        undoButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (undoButton.isChecked()) {
                    if (undoAction != null) undoAction.run();
                    undoButton.setChecked(false);
                }
            }
        });

        redoButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (redoButton.isChecked()) {
                    if (redoAction != null) redoAction.run();
                    redoButton.setChecked(false);
                }
            }
        });

        toolbar.add(title).left().padRight(24f);

        toolbar.add(loadButton).width(68f).height(32f).padRight(4f);
        toolbar.add(saveButton).width(68f).height(32f).padRight(4f);
        toolbar.add(saveAsButton).width(82f).height(32f).padRight(16f);
        toolbar.add(undoButton).width(68f).height(32f).padRight(4f);
        toolbar.add(redoButton).width(68f).height(32f);

        toolbar.add().expandX();

        statusLabel = new Label("", skin);
        statusLabel.setAlignment(Align.right);
        statusLabel.setColor(new Color(0.55f, 0.90f, 0.60f, 1f));

        toolbar.add(statusLabel).right();

        // ------------------------------------------------------------
        // Mode row
        // ------------------------------------------------------------

        toolbar.row().padTop(7f);

        navigateButton = new TextButton("Navigate", skin);
        paintButton = new TextButton("Paint", skin);
        sculptButton = new TextButton("Sculpt", skin);
        sliceButton = new TextButton("Slice", skin);

        addTooltip(navigateButton,
            "Camera navigation only. Left-drag to rotate, "
            + "middle-drag to pan, wheel to zoom. Clicks on the terrain "
            + "open the point information dialog.");
        addTooltip(paintButton,
            "Paint soil composition and depth onto the terrain. "
            + "Left-drag paints. Right-drag rotates the camera. "
            + "Brush settings are in the side panel.");
        addTooltip(sculptButton,
            "Modify the terrain shape itself: dig, fill, flatten, "
            + "level, or smooth. Left-drag sculpts. Right-drag rotates.");
        addTooltip(sliceButton,
            "Preview a rectangular slice of the terrain, then bake it "
            + "into a smaller terrain. Useful for cropping large files "
            + "down to a region of interest.");

        ButtonGroup<TextButton> modeGroup = new ButtonGroup<>(
            navigateButton, paintButton, sculptButton, sliceButton);
        modeGroup.setMinCheckCount(1);
        modeGroup.setMaxCheckCount(1);
        navigateButton.setChecked(true);

        ChangeListener modeListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TextButton selected = (TextButton) actor;
                if (!selected.isChecked()) return;

                if (actor == navigateButton) {
                    interactionMode = InteractionMode.NAVIGATE;
                } else if (actor == paintButton) {
                    interactionMode = InteractionMode.PAINT;
                } else if (actor == sculptButton) {
                    interactionMode = InteractionMode.SCULPT;
                } else {
                    interactionMode = InteractionMode.SLICE;
                }

                navigateButton.setChecked(actor == navigateButton);
                paintButton.setChecked(actor == paintButton);
                sculptButton.setChecked(actor == sculptButton);
                sliceButton.setChecked(actor == sliceButton);

                boolean paint = interactionMode == InteractionMode.PAINT;
                boolean sculpt = interactionMode == InteractionMode.SCULPT;
                boolean slice = interactionMode == InteractionMode.SLICE;

                rayPicker.setPaintMode(paint);
                rayPicker.setSculptMode(sculpt);

                paintScrollPane.setVisible(paint);
                sculptScrollPane.setVisible(sculpt);
                sliceScrollPane.setVisible(slice);

                if (!slice && cutToggle != null && cutToggle.isChecked()) {
                    cutToggle.setChecked(false);
                    pushCutState();
                }

                if (paint || sculpt) {
                    cameraController.rotateButton = Buttons.RIGHT;
                    cameraController.translateButton = Buttons.MIDDLE;
                } else {
                    cameraController.rotateButton = Buttons.LEFT;
                    cameraController.translateButton = Buttons.MIDDLE;
                    stage.setScrollFocus(null);
                }

                pushBrushValidity();
            }
        };

        navigateButton.addListener(modeListener);
        paintButton.addListener(modeListener);
        sculptButton.addListener(modeListener);
        sliceButton.addListener(modeListener);

        toolbar.add(new Label("Mode:", skin)).left().width(75f);

        final float modeButtonWidth = 82f;
        final float modeButtonGap = 6f;

        toolbar.add(navigateButton).width(modeButtonWidth).height(30f).padRight(modeButtonGap);
        toolbar.add(paintButton).width(modeButtonWidth).height(30f).padRight(modeButtonGap);
        toolbar.add(sculptButton).width(modeButtonWidth).height(30f).padRight(modeButtonGap);
        toolbar.add(sliceButton).width(modeButtonWidth).height(30f);

        // ------------------------------------------------------------
        // View row
        // ------------------------------------------------------------

        toolbar.row().padTop(6f);

        compositionViewButton = new TextButton("Composition", skin);
        depthViewButton = new TextButton("Depth", skin);
        compositionViewButton.setChecked(true);

        addTooltip(compositionViewButton,
            "Colour the terrain by soil composition: "
            + "pink = sand, blue = silt, orange = clay. "
            + "Unpainted points render as default brown.");
        addTooltip(depthViewButton,
            "Colour the terrain by soil depth using a "
            + "green-to-gold-to-red ramp. Deeper soil is red, "
            + "shallow is green.");

        ChangeListener viewListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TextButton selected = (TextButton) actor;
                if (!selected.isChecked()) return;

                compositionViewButton.setChecked(actor == compositionViewButton);
                depthViewButton.setChecked(actor == depthViewButton);

                if (viewModeListener != null) {
                    TerrainMeshBuilder.ViewMode mode =
                        actor == depthViewButton
                            ? TerrainMeshBuilder.ViewMode.DEPTH
                            : TerrainMeshBuilder.ViewMode.COMPOSITION;
                    viewModeListener.onViewModeChanged(mode);
                }
            }
        };

        compositionViewButton.addListener(viewListener);
        depthViewButton.addListener(viewListener);

        toolbar.add(new Label("View:", skin)).left().width(75f);
        toolbar.add(compositionViewButton).width(100f).height(30f).padRight(4f);
        toolbar.add(depthViewButton).width(72f).height(30f);

        // ------------------------------------------------------------
        // Overlay row
        // ------------------------------------------------------------

        toolbar.row().padTop(6f);

        shellToggle = new TextButton("Shell", skin);
        shellToggle.setChecked(true);
        addTooltip(shellToggle,
            "Show the terrain's side walls and bottom cap, making "
            + "it look like a solid block. Turn off to see just the "
            + "surface as a flat sheet.");

        shellToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (shellListener != null) shellListener.accept(shellToggle.isChecked());
            }
        });

        soilNailToggle = new TextButton("Soil Nails", skin);
        addTooltip(soilNailToggle,
            "Overlay short vertical pins at sampled grid points. "
            + "Pin length and colour encode soil depth, so you can "
            + "read thickness at a glance from any viewing angle.");

        soilNailToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                soilNailStrideField.setDisabled(!soilNailToggle.isChecked());
                pushSoilNailState();
            }
        });

        soilNailStrideField = new TextField("4", skin);
        soilNailStrideField.setDisabled(true);
        soilNailStrideField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                pushSoilNailState();
            }
        });

        contourToggle = new TextButton("Depth Rings", skin);
        addTooltip(contourToggle,
            "Draw contour lines on the terrain surface at regular "
            + "intervals of depth change. Helps visualise how the "
            + "soil thickness varies across the landscape.");

        contourToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                contourIntervalField.setDisabled(!contourToggle.isChecked());
                pushContourState();
            }
        });

        contourIntervalField = new TextField("2", skin);
        contourIntervalField.setDisabled(true);
        contourIntervalField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                pushContourState();
            }
        });

        toolbar.add(new Label("Overlays:", skin)).left().width(75f);
        toolbar.add(shellToggle).width(82f).height(30f).padRight(4f);
        toolbar.add(soilNailToggle).width(92f).height(30f).padRight(4f);
        toolbar.add(contourToggle).width(100f).height(30f);
    }

    private void createPaintPanel() {
        paintPanel = new Table();
        paintPanel.setBackground(skin.newDrawable("white",
            new Color(0.10f, 0.11f, 0.14f, 0.96f)));
        paintPanel.pad(18f);
        paintPanel.top().left();

        paintPanel.add(new Label("Brush Settings", skin)).left().padBottom(18f);
        paintPanel.row();

        TextButton generateCompositionButton = new TextButton("Generate Terrain Composition", skin);
        addTooltip(generateCompositionButton,
            "Auto-generate soil composition for every unpainted point, "
            + "based on slope and curvature. Slopes get more sand, "
            + "concave hollows get more clay. Painted areas are "
            + "left untouched.");
        generateCompositionButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (generateCompositionButton.isChecked()) {
                    if (onGenerateTerrainComposition != null) onGenerateTerrainComposition.run();
                    generateCompositionButton.setChecked(false);
                }
            }
        });
        paintPanel.add(generateCompositionButton).width(210f).height(36f).left().padBottom(10f);
        paintPanel.row();

        radiusField = new TextField(Float.toString(paintBrush.getRadius()), skin);
        depthField  = new TextField(Float.toString(paintBrush.getDepth()), skin);
        falloffField = new TextField(Float.toString(paintBrush.getFalloff()), skin);
        sandField   = new TextField(Float.toString(paintBrush.getSand() * 100f), skin);
        siltField   = new TextField(Float.toString(paintBrush.getSilt() * 100f), skin);
        clayField   = new TextField(Float.toString(paintBrush.getClay() * 100f), skin);
        strengthField  = new TextField(Float.toString(paintBrush.getStrength() * 100f), skin);
        variationField = new TextField(Float.toString(paintBrush.getVariation() * 100f), skin);

        blendButton = new TextButton("Blend", skin);
        blendButton.setChecked(paintBrush.isBlendEnabled());
        addTooltip(blendButton,
            "When on, brush strength scales how aggressively composition "
            + "is pushed toward the target. When off, composition is set "
            + "to the target directly. Defaults to off.");

        randomVariationButton = new TextButton("Random Variation", skin);
        randomVariationButton.setChecked(paintBrush.isRandomVariationEnabled());
        addTooltip(randomVariationButton,
            "Add a small random offset to composition and depth on each "
            + "stamp, giving a more natural, less uniform result. "
            + "The Variation field controls the size of the offset.");

        slopeAwareDepthButton = new TextButton("Slope-Aware Depth", skin);
        slopeAwareDepthButton.setChecked(paintBrush.isSlopeAwareDepthEnabled());
        addTooltip(slopeAwareDepthButton,
            "Reduce painted depth on steep slopes. Based on the terrain "
            + "model: thin soil on inclines, thicker on flats. Turn off "
            + "to paint uniform depth regardless of slope.");

        slopeAwareCompositionButton = new TextButton("Slope-Aware Composition", skin);
        slopeAwareCompositionButton.setChecked(paintBrush.isSlopeAwareCompositionEnabled());
        addTooltip(slopeAwareCompositionButton,
            "Shift the composition you paint toward terrain-appropriate "
            + "values: sandier on slopes, more clay in hollows. Turn off "
            + "to paint exactly the composition you entered.");

        if (!paintBrush.isBlendEnabled() && !paintBrush.isDepthAccumulateEnabled()) {
            paintBrush.setStrength(1.0f);
            strengthField.setText("100.0");
        }
        strengthField.setDisabled(
            !paintBrush.isBlendEnabled() && !paintBrush.isDepthAccumulateEnabled()
        );
        variationField.setDisabled(!paintBrush.isRandomVariationEnabled());

        radiusStatus    = new Label("", skin);
        depthStatus     = new Label("", skin);
        falloffStatus   = new Label("", skin);
        strengthStatus  = new Label("", skin);
        variationStatus = new Label("", skin);
        Color warningColor = new Color(1f, 0.45f, 0.45f, 1f);
        radiusStatus.setColor(warningColor);
        depthStatus.setColor(warningColor);
        strengthStatus.setColor(warningColor);
        variationStatus.setColor(warningColor);
        falloffStatus.setColor(warningColor);

        paintPanel.add(new Label("Radius", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(radiusField).width(150f).height(36f).padBottom(4f);
        paintPanel.row();
        paintPanel.add(radiusStatus).left().padBottom(10f);
        paintPanel.row();

        paintPanel.add(new Label("Falloff (0-1)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(falloffField).width(150f).height(36f).padBottom(4f);
        paintPanel.row();
        paintPanel.add(falloffStatus).left().padBottom(10f);
        paintPanel.row();
        paintPanel.add(new Label("Depth", skin)).left().padTop(12f).padBottom(10f);
        paintPanel.row();
        paintPanel.add(new Label("Depth (m)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(depthField).width(150f).height(36f).padBottom(4f);
        paintPanel.row();
        paintPanel.add(depthStatus).left().padBottom(10f);
        paintPanel.row();

        depthAccumulateButton = new TextButton("Depth: Set", skin);
        depthAccumulateButton.setChecked(paintBrush.isDepthAccumulateEnabled());
        addTooltip(depthAccumulateButton,
            "Toggle between Set and Airbrush depth modes. "
            + "Set: each stroke writes the target depth. "
            + "Airbrush: each pass deposits a fraction of the target, "
            + "accumulating up to the terrain's max depth.");
        depthAccumulateButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                boolean on = depthAccumulateButton.isChecked();
                depthAccumulateButton.setText(on ? "Depth: Airbrush" : "Depth: Set");
                paintBrush.setDepthAccumulateEnabled(on);
                updateStrengthFieldEnabled();
                pushBrushValidity();
            }
        });
        paintPanel.add(depthAccumulateButton).width(190f).height(36f).left().padBottom(10f);
        paintPanel.row();

        paintPanel.add(slopeAwareDepthButton).width(190f).height(36f).left().padBottom(14f);
        paintPanel.row();

        paintPanel.add(new Label("Soil Composition", skin)).left().padTop(12f).padBottom(8f);
        paintPanel.row();

        soilPalette = new SoilCompositionPalette(
            paintBrush.getSand(), paintBrush.getSilt(), paintBrush.getClay(),
            (sand, silt, clay) -> {
                sandField.setText(String.format("%.1f", paintBrush.getSand() * 100f));
                siltField.setText(String.format("%.1f", paintBrush.getSilt() * 100f));
                clayField.setText(String.format("%.1f", paintBrush.getClay() * 100f));
                paintBrush.setComposition(sand, silt, clay);
                compositionValid = true;
                showCompositionValid();
                pushBrushValidity();
            }
        );
        paintPanel.add(soilPalette).width(180f).height(160f).padBottom(14f);
        paintPanel.row();

        paintPanel.add(new Label("Sand (%)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(sandField).width(150f).height(36f).padBottom(12f);
        paintPanel.row();

        paintPanel.add(new Label("Silt (%)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(siltField).width(150f).height(36f).padBottom(12f);
        paintPanel.row();

        paintPanel.add(new Label("Clay (%)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(clayField).width(150f).height(36f);
        paintPanel.row();

        compositionStatus = new Label("Total: 100% OK", skin);
        paintPanel.add(compositionStatus).left().padTop(12f).padBottom(10f);
        paintPanel.row();

        paintPanel.add(slopeAwareCompositionButton).width(190f).height(36f).left().padBottom(10f);
        paintPanel.row();
        paintPanel.add(blendButton).width(190f).height(36f).left().padBottom(10f);
        paintPanel.row();

        paintPanel.add(new Label("Intensity (%)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(strengthField).width(150f).height(36f).padBottom(4f);
        paintPanel.row();
        paintPanel.add(strengthStatus).left().padBottom(10f);
        paintPanel.row();

        paintPanel.add(new Label("Shared Effects", skin)).left().padTop(12f).padBottom(10f);
        paintPanel.row();
        paintPanel.add(randomVariationButton).width(190f).height(36f).left().padBottom(10f);
        paintPanel.row();
        paintPanel.add(new Label("Variation (%)", skin)).left().padBottom(6f);
        paintPanel.row();
        paintPanel.add(variationField).width(150f).height(36f).padBottom(4f);
        paintPanel.row();
        paintPanel.add(variationStatus).left().padBottom(10f);
        paintPanel.row();

        addBrushSettingListeners();
    }

    private void createSculptPanel() {
        sculptPanel = new Table();
        sculptPanel.setBackground(skin.newDrawable("white",
            new Color(0.10f, 0.11f, 0.14f, 0.96f)));
        sculptPanel.pad(18f);
        sculptPanel.top().left();

        sculptPanel.add(new Label("Terrain Sculpt", skin)).left().padBottom(14f);
        sculptPanel.row();

        Table modes = new Table();
        sculptDigButton     = new TextButton("Dig",     skin);
        sculptFillButton    = new TextButton("Fill",    skin);
        sculptFlattenButton = new TextButton("Flatten", skin);
        sculptLevelButton   = new TextButton("Level",   skin);
        sculptSmoothButton  = new TextButton("Smooth",  skin);

        addTooltip(sculptDigButton,
            "Lower the terrain and remove soil. Depth of removal is "
            + "controlled by the Strength field.");
        addTooltip(sculptFillButton,
            "Raise the terrain and add soil. Height of addition is "
            + "controlled by the Strength field.");
        addTooltip(sculptFlattenButton,
            "Smooth the terrain toward the local weighted average "
            + "elevation inside the brush. Useful for removing bumps.");
        addTooltip(sculptLevelButton,
            "Set the terrain inside the brush to a specific target "
            + "height. Target value is entered in the Level field below.");
        addTooltip(sculptSmoothButton,
            "Blur the terrain elevation without changing its average "
            + "height. Softer than Flatten — nudges toward neighbours "
            + "rather than snapping to a single level.");

        ChangeListener modeListener = new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                TextButton selected = (TextButton) a;
                if (!selected.isChecked()) {
                    if (!sculptDigButton.isChecked() && !sculptFillButton.isChecked()
                        && !sculptFlattenButton.isChecked() && !sculptLevelButton.isChecked()
                        && !sculptSmoothButton.isChecked()) {
                        selected.setChecked(true);
                    }
                    return;
                }

                sculptDigButton.setChecked(a == sculptDigButton);
                sculptFillButton.setChecked(a == sculptFillButton);
                sculptFlattenButton.setChecked(a == sculptFlattenButton);
                sculptLevelButton.setChecked(a == sculptLevelButton);
                sculptSmoothButton.setChecked(a == sculptSmoothButton);

                if (terrainBrush == null) return;
                if (a == sculptDigButton) terrainBrush.setMode(TerrainBrush.Mode.DIG);
                else if (a == sculptFillButton) terrainBrush.setMode(TerrainBrush.Mode.FILL);
                else if (a == sculptFlattenButton) terrainBrush.setMode(TerrainBrush.Mode.FLATTEN);
                else if (a == sculptLevelButton) terrainBrush.setMode(TerrainBrush.Mode.LEVEL);
                else terrainBrush.setMode(TerrainBrush.Mode.SMOOTH);
            }
        };
        sculptDigButton.addListener(modeListener);
        sculptFillButton.addListener(modeListener);
        sculptFlattenButton.addListener(modeListener);
        sculptLevelButton.addListener(modeListener);
        sculptSmoothButton.addListener(modeListener);
        sculptDigButton.setChecked(true);

        modes.add(sculptDigButton).width(105f).height(32f).pad(2f);
        modes.add(sculptFillButton).width(105f).height(32f).pad(2f);
        modes.row();
        modes.add(sculptFlattenButton).width(105f).height(32f).pad(2f);
        modes.add(sculptLevelButton).width(105f).height(32f).pad(2f);
        modes.row();
        modes.add(sculptSmoothButton).width(105f).height(32f).pad(2f);

        sculptPanel.add(modes).left().padBottom(14f);
        sculptPanel.row();

        sculptRadiusField   = new TextField("8",   skin);
        sculptStrengthField = new TextField("0.5", skin);
        sculptFalloffField  = new TextField("1.0", skin);
        sculptLevelField    = new TextField("0.0", skin);

        sculptRadiusStatus = new Label("", skin);
        sculptStatus       = new Label("", skin);
        Color warn = new Color(1f, 0.45f, 0.45f, 1f);
        sculptRadiusStatus.setColor(warn);
        sculptStatus.setColor(warn);

        sculptPanel.add(new Label("Radius (points)", skin)).left().padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptRadiusField).width(150f).height(34f).padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptRadiusStatus).left().padBottom(10f);
        sculptPanel.row();

        sculptPanel.add(new Label("Strength (m / stamp)", skin)).left().padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptStrengthField).width(150f).height(34f).padBottom(10f);
        sculptPanel.row();

        sculptPanel.add(new Label("Falloff (0-1)", skin)).left().padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptFalloffField).width(150f).height(34f).padBottom(10f);
        sculptPanel.row();

        sculptPanel.add(new Label("Level target (m)", skin)).left().padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptLevelField).width(150f).height(34f).padBottom(4f);
        sculptPanel.row();
        sculptPanel.add(sculptStatus).left().padBottom(14f);
        sculptPanel.row();

        addSculptListeners();
    }

    private void createSlicePanel() {
        slicePanel = new Table();
        slicePanel.setBackground(skin.newDrawable("white",
            new Color(0.10f, 0.11f, 0.14f, 0.96f)));
        slicePanel.pad(18f);
        slicePanel.top().left();

        slicePanel.add(new Label("Terrain Slice", skin)).left().padBottom(14f);
        slicePanel.row();

        cutToggle = new TextButton("Preview Slice", skin);
        addTooltip(cutToggle,
            "Show a rectangular cutaway of the terrain. Nothing is "
            + "deleted until you press Bake — this is preview only.");
        cutToggle.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { pushCutState(); }
        });
        slicePanel.add(cutToggle).width(200f).height(34f).left().padBottom(8f);
        slicePanel.row();

        cutModeToggle = new TextButton("Keep Inside", skin);
        addTooltip(cutModeToggle,
            "Toggle whether the preview keeps the region inside the "
            + "rectangle or outside it. Baking requires Keep Inside — "
            + "a hole in the middle cannot become a smaller terrain.");
        cutModeToggle.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!cutModeToggle.isChecked()) return;
                cutKeepInside = !cutKeepInside;
                cutModeToggle.setText(cutKeepInside ? "Keep Inside" : "Keep Outside");
                cutModeToggle.setChecked(false);
                pushCutState();
            }
        });
        slicePanel.add(cutModeToggle).width(200f).height(34f).left().padBottom(8f);
        slicePanel.row();

        cutMinXField = new TextField("0", skin);
        cutMaxXField = new TextField("0", skin);
        cutMinZField = new TextField("0", skin);
        cutMaxZField = new TextField("0", skin);

        ChangeListener fieldListener = new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (cutToggle.isChecked()) pushCutState();
            }
        };
        cutMinXField.addListener(fieldListener);
        cutMaxXField.addListener(fieldListener);
        cutMinZField.addListener(fieldListener);
        cutMaxZField.addListener(fieldListener);

        slicePanel.add(new Label("X range", skin)).left().padBottom(4f);
        slicePanel.row();
        Table xRow = new Table();
        xRow.add(cutMinXField).width(72f).height(34f).padRight(6f);
        xRow.add(new Label("to", skin)).padRight(6f);
        xRow.add(cutMaxXField).width(72f).height(34f);
        slicePanel.add(xRow).left().padBottom(8f);
        slicePanel.row();

        slicePanel.add(new Label("Z range", skin)).left().padBottom(4f);
        slicePanel.row();
        Table zRow = new Table();
        zRow.add(cutMinZField).width(72f).height(34f).padRight(6f);
        zRow.add(new Label("to", skin)).padRight(6f);
        zRow.add(cutMaxZField).width(72f).height(34f);
        slicePanel.add(zRow).left().padBottom(12f);
        slicePanel.row();

        TextButton bakeCutButton = new TextButton("Bake Slice", skin);
        addTooltip(bakeCutButton,
            "Permanently crop the terrain to the slice rectangle. "
            + "The new terrain replaces the current one in memory — "
            + "you'll be prompted to save if you have unsaved changes.");
        bakeCutButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (bakeCutButton.isChecked()) {
                    if (cutBakeListener != null) cutBakeListener.onBakeRequested();
                    bakeCutButton.setChecked(false);
                }
            }
        });
        slicePanel.add(bakeCutButton).width(200f).height(34f).left().padBottom(8f);
    }

    private void pushCutState() {
        if (cutListener == null) return;
        float minX = parseOr(cutMinXField, terrainMin);
        float maxX = parseOr(cutMaxXField, terrainMax);
        float minZ = parseOr(cutMinZField, terrainMin);
        float maxZ = parseOr(cutMaxZField, terrainMax);
        minX = Math.max(terrainMin, Math.min(terrainMax, minX));
        maxX = Math.max(terrainMin, Math.min(terrainMax, maxX));
        minZ = Math.max(terrainMin, Math.min(terrainMax, minZ));
        maxZ = Math.max(terrainMin, Math.min(terrainMax, maxZ));
        if (minX > maxX) { float t = minX; minX = maxX; maxX = t; }
        if (minZ > maxZ) { float t = minZ; minZ = maxZ; maxZ = t; }

        cutListener.onCutChanged(cutToggle.isChecked(), minX, maxX, minZ, maxZ, cutKeepInside);
    }

    private float parseOr(TextField f, float fallback) {
        try { return Float.parseFloat(f.getText()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private void pushContourState() {
        if (contourListener == null) return;
        float interval = 2f;
        try { interval = Float.parseFloat(contourIntervalField.getText()); }
        catch (NumberFormatException ignored) {}
        if (interval <= 0.05f) interval = 0.05f;
        contourListener.onContourChanged(contourToggle.isChecked(), interval, 0.15f);
    }

    private void pushSoilNailState() {
        if (soilNailListener == null) return;
        int stride = 4;
        try { stride = Integer.parseInt(soilNailStrideField.getText()); }
        catch (NumberFormatException ignored) {}
        if (stride < 1)  stride = 1;
        if (stride > 16) stride = 16;
        soilNailListener.onSoilNailChanged(soilNailToggle.isChecked(), stride);
    }

    private void addSculptListeners() {
        sculptRadiusField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                try {
                    float r = Float.parseFloat(sculptRadiusField.getText());
                    if (r > 0f && Float.isFinite(r)) {
                        terrainBrush.setRadius(r);
                        sculptRadiusValid = true;
                        sculptRadiusStatus.setText("");
                    } else {
                        sculptRadiusValid = false;
                        sculptRadiusStatus.setText("Radius must be > 0.");
                    }
                } catch (NumberFormatException ex) {
                    sculptRadiusValid = false;
                    sculptRadiusStatus.setText("Enter a valid number.");
                }
                pushBrushValidity();
            }
        });

        sculptStrengthField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                try {
                    float s = Float.parseFloat(sculptStrengthField.getText());
                    if (Float.isFinite(s)) {
                        terrainBrush.setStrength(s);
                        sculptStrengthValid = true;
                    } else {
                        sculptStrengthValid = false;
                        sculptStatus.setText("Strength must be finite.");
                    }
                } catch (NumberFormatException ex) {
                    sculptStrengthValid = false;
                    sculptStatus.setText("Enter a valid number.");
                }
                pushBrushValidity();
            }
        });

        sculptFalloffField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                try {
                    float f = Float.parseFloat(sculptFalloffField.getText());
                    if (f >= 0f && f <= 1f) {
                        terrainBrush.setFalloff(f);
                        sculptFalloffValid = true;
                    } else {
                        sculptFalloffValid = false;
                        sculptStatus.setText("Falloff must be 0-1.");
                    }
                } catch (NumberFormatException ex) {
                    sculptFalloffValid = false;
                    sculptStatus.setText("Enter a valid number.");
                }
                pushBrushValidity();
            }
        });

        sculptLevelField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                try {
                    float h = Float.parseFloat(sculptLevelField.getText());
                    if (Float.isFinite(h)) terrainBrush.setTargetHeight(h);
                } catch (NumberFormatException ignored) {}
            }
        });

        if (terrainBrush != null) {
            sculptRadiusField.setText(Float.toString(terrainBrush.getRadius()));
            sculptStrengthField.setText(Float.toString(terrainBrush.getStrength()));
            sculptFalloffField.setText(Float.toString(terrainBrush.getFalloff()));
            sculptLevelField.setText(Float.toString(terrainBrush.getTargetHeight()));
        }
    }

    private void showCompositionError(String message) {
        compositionStatus.setText(message);
        compositionStatus.setColor(new Color(1f, 0.45f, 0.45f, 1f));
    }

    private void showCompositionValid() {
        compositionStatus.setText("Total: 100% OK");
        compositionStatus.setColor(new Color(0.55f, 0.90f, 0.60f, 1f));
    }

    private void addBrushSettingListeners() {
        radiusField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { updateRadius(); }
        });

        falloffField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                updateFalloff();
            }
        });

        depthField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { updateDepth(); }
        });

        ChangeListener compositionListener = new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { updateComposition(); }
        };
        sandField.addListener(compositionListener);
        siltField.addListener(compositionListener);
        clayField.addListener(compositionListener);

        randomVariationButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                boolean enabled = randomVariationButton.isChecked();
                paintBrush.setRandomVariationEnabled(enabled);
                variationField.setDisabled(!enabled);
                pushBrushValidity();
            }
        });

        slopeAwareDepthButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                paintBrush.setSlopeAwareDepthEnabled(slopeAwareDepthButton.isChecked());
            }
        });

        slopeAwareCompositionButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                paintBrush.setSlopeAwareCompositionEnabled(slopeAwareCompositionButton.isChecked());
            }
        });

        variationField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { updateVariation(); }
        });

        strengthField.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { updateStrength(); }
        });

        blendButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                boolean enabled = blendButton.isChecked();
                paintBrush.setBlendEnabled(enabled);

                if (!enabled && !paintBrush.isDepthAccumulateEnabled()) {
                    paintBrush.setStrength(1.0f);
                    strengthField.setText("100.0");
                    strengthValid = true;
                    strengthStatus.setText("");
                }
                updateStrengthFieldEnabled();
                pushBrushValidity();
            }
        });
    }

    private void updateRadius() {
        try {
            float radius = Float.parseFloat(radiusField.getText());
            if (!Float.isFinite(radius)) {
                radiusValid = false; radiusStatus.setText("Enter a finite number.");
            } else if (radius < PaintBrush.MIN_RADIUS || radius > PaintBrush.MAX_RADIUS) {
                radiusValid = false;
                radiusStatus.setText("Radius must be " + (int) PaintBrush.MIN_RADIUS + "-" + (int) PaintBrush.MAX_RADIUS + ".");
            } else {
                paintBrush.setRadius(radius);
                radiusValid = true; radiusStatus.setText("");
            }
        } catch (NumberFormatException e) {
            radiusValid = false; radiusStatus.setText("Enter a valid number.");
        }
        pushBrushValidity();
    }

    private void updateFalloff() {
        try {
            float f = Float.parseFloat(falloffField.getText());
            if (Float.isFinite(f) && f >= 0f && f <= 1f) {
                paintBrush.setFalloff(f);
                falloffValid = true;
                falloffStatus.setText("");
            } else {
                falloffValid = false;
                falloffStatus.setText("Falloff must be 0-1.");
            }
        } catch (NumberFormatException e) {
            falloffValid = false;
            falloffStatus.setText("Enter a valid number.");
        }
        pushBrushValidity();
    }

    private void updateDepth() {
        try {
            float depth = Float.parseFloat(depthField.getText());
            if (!Float.isFinite(depth)) {
                depthValid = false; depthStatus.setText("Enter a finite number.");
            } else if (depth < 0f) {
                depthValid = false; depthStatus.setText("Depth cannot be negative.");
            } else {
                paintBrush.setDepth(depth);
                depthValid = true; depthStatus.setText("");
            }
        } catch (NumberFormatException e) {
            depthValid = false; depthStatus.setText("Enter a valid number.");
        }
        pushBrushValidity();
    }

    private void updateComposition() {
        try {
            float sand = Float.parseFloat(sandField.getText());
            float silt = Float.parseFloat(siltField.getText());
            float clay = Float.parseFloat(clayField.getText());

            if (!Float.isFinite(sand) || !Float.isFinite(silt) || !Float.isFinite(clay)) {
                compositionValid = false; showCompositionError("Enter finite percentages.");
                pushBrushValidity(); return;
            }
            if (sand < 0f || sand > 100f || silt < 0f || silt > 100f || clay < 0f || clay > 100f) {
                compositionValid = false; showCompositionError("Each value must be 0-100%.");
                pushBrushValidity(); return;
            }
            float total = sand + silt + clay;
            if (Math.abs(total - 100f) >= 0.01f) {
                compositionValid = false;
                showCompositionError(String.format("Total: %.1f%% - must equal 100%%", total));
                pushBrushValidity(); return;
            }

            paintBrush.setComposition(sand / 100f, silt / 100f, clay / 100f);
            soilPalette.setComposition(sand / 100f, silt / 100f, clay / 100f);
            compositionValid = true;
            showCompositionValid();
            pushBrushValidity();
        } catch (NumberFormatException e) {
            compositionValid = false; showCompositionError("Enter valid percentages.");
            pushBrushValidity();
        }
    }

    private void updateVariation() {
        try {
            float p = Float.parseFloat(variationField.getText());
            if (!Float.isFinite(p)) { variationValid = false; variationStatus.setText("Enter a finite number."); }
            else if (p < 0f || p > 100f) { variationValid = false; variationStatus.setText("Variation must be 0-100%."); }
            else { paintBrush.setVariation(p / 100f); variationValid = true; variationStatus.setText(""); }
        } catch (NumberFormatException e) {
            variationValid = false; variationStatus.setText("Enter a valid number.");
        }
        pushBrushValidity();
    }

    private void updateStrength() {
        try {
            float p = Float.parseFloat(strengthField.getText());
            if (!Float.isFinite(p)) { strengthValid = false; strengthStatus.setText("Enter a finite number."); }
            else if (p < 0f || p > 100f) { strengthValid = false; strengthStatus.setText("Strength must be 0-100%."); }
            else { paintBrush.setStrength(p / 100f); strengthValid = true; strengthStatus.setText(""); }
        } catch (NumberFormatException e) {
            strengthValid = false; strengthStatus.setText("Enter a valid number.");
        }
        pushBrushValidity();
    }

    public void showPointInfo(int gridX, int gridZ, float elevation, float depth,
                              float sand, float silt, float clay) {
        Actor dismissOverlay = new Actor();
        dismissOverlay.setBounds(0f, 0f, stage.getWidth(), stage.getHeight());

        Dialog dialog = new Dialog("Point Information", skin) {
            @Override protected void result(Object object) { dismissOverlay.remove(); }
        };

        dialog.getContentTable().pad(20f);
        dialog.text("Grid point: (" + gridX + ", " + gridZ + ")");
        dialog.getContentTable().row();
        dialog.text(String.format("Elevation: %.2f m", elevation));
        dialog.getContentTable().row();
        dialog.text(String.format("Soil depth: %.2f m", depth));
        dialog.getContentTable().row();
        dialog.text(String.format("Sand: %.1f%%", sand * 100f));
        dialog.getContentTable().row();
        dialog.text(String.format("Silt: %.1f%%", silt * 100f));
        dialog.getContentTable().row();
        dialog.text(String.format("Clay: %.1f%%", clay * 100f));

        dialog.button("Close", true);
        dialog.key(Input.Keys.ESCAPE, true);
        dialog.setModal(false);
        dialog.setMovable(true);
        dialog.setResizable(false);

        dismissOverlay.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                dialog.hide(); dismissOverlay.remove(); return true;
            }
        });
        dialog.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; }
        });

        stage.addActor(dismissOverlay);
        dialog.show(stage);
    }

    public void update() { stage.act(Gdx.graphics.getDeltaTime()); }
    public void render() { stage.draw(); }

    public void showStatusMessage(String message) {
        statusLabel.setText(message);
    }
    public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    public Stage getStage() { return stage; }
    public void setPaintPanelVisible(boolean visible) { paintScrollPane.setVisible(visible); }

    public void dispose() {
        if (soilPalette != null) soilPalette.dispose();
        stage.dispose();
        skin.dispose();
    }

    private Pixmap createRoundedPixmap(int width, int height, int radius) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(Color.WHITE);
        pixmap.fillRectangle(radius, 0, width - (radius * 2), height);
        pixmap.fillRectangle(0, radius, width, height - (radius * 2));
        pixmap.fillCircle(radius, radius, radius);
        pixmap.fillCircle(width - radius - 1, radius, radius);
        pixmap.fillCircle(radius, height - radius - 1, radius);
        pixmap.fillCircle(width - radius - 1, height - radius - 1, radius);
        return pixmap;
    }

    public void showUnsavedChangesDialog(String messageText, Runnable onSave,
                                          Runnable onDontSave, Runnable onCancel) {
        Dialog dialog = new Dialog("Unsaved Changes", skin) {
            @Override protected void result(Object object) {
                if ("save".equals(object)) { if (onSave != null) onSave.run(); }
                else if ("dontSave".equals(object)) { if (onDontSave != null) onDontSave.run(); }
                else { if (onCancel != null) onCancel.run(); }
            }
        };

        dialog.getContentTable().pad(20f);
        dialog.getButtonTable().pad(12f);

        Label message = new Label(messageText, skin);
        message.setWrap(true);
        dialog.getContentTable().add(message).width(360f).padBottom(12f);

        dialog.button("Save", "save");
        dialog.button("Don't Save", "dontSave");
        dialog.button("Cancel", "cancel");
        dialog.getButtonTable().getCells().forEach(cell -> cell.width(110f).height(38f).pad(6f));

        dialog.key(Input.Keys.ESCAPE, "cancel");
        dialog.setModal(true);
        dialog.setMovable(false);
        dialog.setResizable(false);
        dialog.setWidth(440f);
        dialog.show(stage);
    }

    private void pushBrushValidity() {
        boolean valid;

        if (paintButton != null && paintButton.isChecked()) {
            boolean variationCurrentlyValid = !randomVariationButton.isChecked() || variationValid;
            boolean strengthCurrentlyValid =
                (!blendButton.isChecked()
                    && !paintBrush.isDepthAccumulateEnabled())
                || strengthValid;
            valid = radiusValid && depthValid && compositionValid && falloffValid && strengthCurrentlyValid && variationCurrentlyValid;
        } else if (sculptButton != null && sculptButton.isChecked()) {
            valid = sculptRadiusValid && sculptStrengthValid && sculptFalloffValid;
        } else {
            valid = true;
        }

        rayPicker.setBrushSettingsValid(valid);
    }

    private void updateStrengthFieldEnabled() {
        boolean on = paintBrush.isBlendEnabled()
                || paintBrush.isDepthAccumulateEnabled();
        strengthField.setDisabled(!on);
    }

    public void setTerrainBrush(TerrainBrush b) { this.terrainBrush = b; }
    public void setCutListener(CutListener l)   { this.cutListener = l; }
    public void setShellToggleListener(Consumer<Boolean> l) { this.shellListener = l; }
    public void setContourListener(ContourListener l)       { this.contourListener = l; }
    public void setCutBakeListener(CutBakeListener l)       { this.cutBakeListener = l; }
    public void setSoilNailListener(SoilNailListener l)     { this.soilNailListener = l; }
    public void setViewModeListener(ViewModeListener l)     { this.viewModeListener = l; }
    public void setUndoRedoActions(Runnable undo, Runnable redo) { this.undoAction = undo; this.redoAction = redo; }

    public void setCutPlaneBounds(float min, float max) {
        this.terrainMin = min;
        this.terrainMax = max;
        if (cutMinXField != null) {
            cutMinXField.setText(Float.toString(min));
            cutMaxXField.setText(Float.toString(max));
            cutMinZField.setText(Float.toString(min));
            cutMaxZField.setText(Float.toString(max));
        }
    }

    private void addScrollFocusOnHover(ScrollPane pane) {
        pane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                stage.setScrollFocus(pane);
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (toActor == null || (toActor != pane && !toActor.isDescendantOf(pane))) {
                    stage.setScrollFocus(null);
                }
            }
        });
    }
}

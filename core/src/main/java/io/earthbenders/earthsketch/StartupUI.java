package io.earthbenders.earthsketch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Full-screen welcome screen showing the EarthSketch splash artwork
 * with a Load Terrain button overlaid near the bottom centre.
 */
public class StartupUI {

    private static final String SPLASH_PATH = "startup/splash.jpeg";

    private static final Color LETTERBOX =
        new Color(0.165f, 0.157f, 0.208f, 1f);

    private final Stage stage;
    private final Skin skin;
    private final Texture splashTexture;
    private final Texture pixel;
    private final Actor splashActor;

    public StartupUI(Runnable onLoadFile) {
        stage = new Stage(new ScreenViewport());
        skin = new Skin();

        splashTexture = new Texture(Gdx.files.internal(SPLASH_PATH));
        splashTexture.setFilter(
            Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear
        );

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        createSkin();

        splashActor = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                float screenW = stage.getViewport().getWorldWidth();
                float screenH = stage.getViewport().getWorldHeight();

                batch.setColor(LETTERBOX);
                batch.draw(pixel, 0f, 0f, screenW, screenH);

                float imageW = splashTexture.getWidth();
                float imageH = splashTexture.getHeight();
                float scale  = Math.max(screenW / imageW, screenH / imageH);
                float drawW  = imageW * scale;
                float drawH  = imageH * scale;
                float drawX  = (screenW - drawW) / 2f;
                float drawY  = (screenH - drawH) / 2f;

                batch.setColor(Color.WHITE);
                batch.draw(splashTexture, drawX, drawY, drawW, drawH);

            }
        };

        splashActor.setBounds(
            0f, 0f,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        stage.addActor(splashActor);

        // Load button, centred near the bottom.
        TextButton loadButton = new TextButton("Load Terrain", skin);
        loadButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (onLoadFile != null) {
                    onLoadFile.run();
                }
            }
        });

        Label welcomeLabel = new Label("Load a terrain to begin.", skin);
        welcomeLabel.setColor(new Color(0.82f, 0.72f, 0.50f, 0.85f)); // muted gold

        Table buttonLayer = new Table();
        buttonLayer.setFillParent(true);
        buttonLayer.bottom();
        buttonLayer.padBottom(14f);
        buttonLayer.add(loadButton).width(220f).height(52f);
        buttonLayer.row().padTop(6f);
        buttonLayer.add(welcomeLabel);

        stage.addActor(buttonLayer);
    }

    private void createSkin() {
        BitmapFont font = new BitmapFont();
        font.getData().setScale(1.4f);
        skin.add("default-font", font);

        Pixmap whitePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixmap.setColor(Color.WHITE);
        whitePixmap.fill();
        Texture whiteTexture = new Texture(whitePixmap);
        whitePixmap.dispose();
        skin.add("white", whiteTexture);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;

        // Warm gold, echoing the energy trails in the splash.
        buttonStyle.up      = skin.newDrawable("white",
            new Color(0.72f, 0.55f, 0.30f, 0.92f));
        buttonStyle.over    = skin.newDrawable("white",
            new Color(0.86f, 0.68f, 0.40f, 0.96f));
        buttonStyle.down    = skin.newDrawable("white",
            new Color(0.58f, 0.42f, 0.22f, 1f));
        buttonStyle.fontColor = new Color(0.10f, 0.08f, 0.05f, 1f);
        buttonStyle.overFontColor = new Color(0.10f, 0.08f, 0.05f, 1f);
        buttonStyle.downFontColor = Color.WHITE;
        skin.add("default", buttonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);
    }

    public void update() {
        stage.act(Gdx.graphics.getDeltaTime());
    }

    public void render() {
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        splashActor.setBounds(0f, 0f, width, height);
    }

    public Stage getStage() {
        return stage;
    }

    public void dispose() {
        splashTexture.dispose();
        pixel.dispose();
        stage.dispose();
        skin.dispose();
    }
}

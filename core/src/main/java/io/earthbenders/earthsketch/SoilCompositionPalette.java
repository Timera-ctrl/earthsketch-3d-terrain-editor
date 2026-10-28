
package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

public class SoilCompositionPalette extends Widget {

    public interface CompositionListener {
        void changed(float sand, float silt, float clay);
    }

    private final Texture triangleTexture;
    private final Texture markerTexture;

    private final CompositionListener listener;

    private float sand;
    private float silt;
    private float clay;

    private float markerX;
    private float markerY;

    public SoilCompositionPalette(
        float sand,
        float silt,
        float clay,
        CompositionListener listener
    ) {
        this.listener = listener;

        //
        triangleTexture = createTriangleTexture(180, 160);
        markerTexture = createMarkerTexture(10);

        setComposition(sand, silt, clay);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(
                InputEvent event,
                float x,
                float y,
                int pointer,
                int button
            ) {
                updateFromClick(x, y);
                return true;
            }

            @Override
            public void touchDragged(
                InputEvent event,
                float x,
                float y,
                int pointer
            ) {
                updateFromClick(x, y);
            }
        });
    }

    @Override
    public float getPrefWidth() {
        return 180f;
    }

    @Override
    public float getPrefHeight() {
        return 160f;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float width = getWidth();
        float height = getHeight();

        batch.draw(triangleTexture, x, y, width, height);

        float markerSize = 10f;

        batch.draw(
            markerTexture,
            x + markerX - markerSize / 2f,
            y + markerY - markerSize / 2f,
            markerSize,
            markerSize
        );
    }

    public void setComposition(float sand, float silt, float clay) {
        this.sand = sand;
        this.silt = silt;
        this.clay = clay;

        updateMarkerPosition();
    }

    private void updateFromClick(float x, float y) {
        Vector2 sandVertex = getSandVertex();
        Vector2 siltVertex = getSiltVertex();
        Vector2 clayVertex = getClayVertex();

        float denominator =
            (siltVertex.y - clayVertex.y) * (sandVertex.x - clayVertex.x)
            + (clayVertex.x - siltVertex.x) * (sandVertex.y - clayVertex.y);

        float newSand =
            ((siltVertex.y - clayVertex.y) * (x - clayVertex.x)
            + (clayVertex.x - siltVertex.x) * (y - clayVertex.y))
            / denominator;

        float newSilt =
            ((clayVertex.y - sandVertex.y) * (x - clayVertex.x)
            + (sandVertex.x - clayVertex.x) * (y - clayVertex.y))
            / denominator;

        float newClay = 1f - newSand - newSilt;

        if (newSand < 0f || newSilt < 0f || newClay < 0f) {
            return;
        }

        sand = newSand;
        silt = newSilt;
        clay = newClay;

        markerX = x;
        markerY = y;

        if (listener != null) {
            listener.changed(sand, silt, clay);
        }
    }

    private void updateMarkerPosition() {
        Vector2 sandVertex = getSandVertex();
        Vector2 siltVertex = getSiltVertex();
        Vector2 clayVertex = getClayVertex();

        markerX =
            sand * sandVertex.x
            + silt * siltVertex.x
            + clay * clayVertex.x;

        markerY =
            sand * sandVertex.y
            + silt * siltVertex.y
            + clay * clayVertex.y;
    }

    private Vector2 getSandVertex() {
        return new Vector2(
            getWidth() * 0.08f,
            getHeight() * 0.08f
        );
    }

    private Vector2 getSiltVertex() {
        return new Vector2(
            getWidth() * 0.92f,
            getHeight() * 0.08f
        );
    }

    private Vector2 getClayVertex() {
        return new Vector2(
            getWidth() * 0.5f,
            getHeight() * 0.92f
        );
    }

    private Texture createTriangleTexture(int width, int height) {
        Pixmap pixmap =
            new Pixmap(width, height, Pixmap.Format.RGBA8888);

        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        Vector2 sandVertex =
            new Vector2(width * 0.08f, height * 0.08f);

        Vector2 siltVertex =
            new Vector2(width * 0.92f, height * 0.08f);

        Vector2 clayVertex =
            new Vector2(width * 0.5f, height * 0.92f);

        Color sandColor = SoilPalette.SAND_COLOR;
        Color siltColor = SoilPalette.SILT_COLOR;
        Color clayColor = SoilPalette.CLAY_COLOR;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                float denominator =
                    (siltVertex.y - clayVertex.y)
                    * (sandVertex.x - clayVertex.x)
                    + (clayVertex.x - siltVertex.x)
                    * (sandVertex.y - clayVertex.y);

                float sand =
                    ((siltVertex.y - clayVertex.y)
                    * (x - clayVertex.x)
                    + (clayVertex.x - siltVertex.x)
                    * (y - clayVertex.y))
                    / denominator;

                float silt =
                    ((clayVertex.y - sandVertex.y)
                    * (x - clayVertex.x)
                    + (sandVertex.x - clayVertex.x)
                    * (y - clayVertex.y))
                    / denominator;

                float clay = 1f - sand - silt;

                if (sand >= 0f && silt >= 0f && clay >= 0f) {
                    Color color = new Color(
                        sandColor.r * sand
                            + siltColor.r * silt
                            + clayColor.r * clay,

                        sandColor.g * sand
                            + siltColor.g * silt
                            + clayColor.g * clay,

                        sandColor.b * sand
                            + siltColor.b * silt
                            + clayColor.b * clay,

                        1f
                    );

                    pixmap.drawPixel(
                        x,
                        height - 1 - y,
                        Color.rgba8888(color)
                    );
                }
            }
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return texture;
    }

    private Texture createMarkerTexture(int size) {
        Pixmap pixmap =
            new Pixmap(size, size, Pixmap.Format.RGBA8888);

        pixmap.setColor(Color.WHITE);
        pixmap.fillCircle(size / 2, size / 2, size / 2);

        pixmap.setColor(Color.BLACK);
        pixmap.drawCircle(size / 2, size / 2, size / 2 - 1);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return texture;
    }

    public void dispose() {
        triangleTexture.dispose();
        markerTexture.dispose();
    }
}
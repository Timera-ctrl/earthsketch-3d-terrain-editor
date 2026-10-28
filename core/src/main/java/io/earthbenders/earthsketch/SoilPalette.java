package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.Color;

public class SoilPalette {

    // Base colour used for pure sand
    public static final Color SAND_COLOR = new Color(1.0f, 0.4f, 0.7f, 1.0f);

    // Base colour used for pure silt.
    public static final Color SILT_COLOR = new Color(0.0f, 0.4f, 1.0f, 1.0f);

    // Base colour used for pure clay.
    public static final Color CLAY_COLOR = new Color(1.0f, 0.5f, 0.0f, 1.0f);

    // Default terrain colour used when no soil composition has been painted.
    private static final Color DEFAULT_COLOR = new Color(0.55f, 0.4f, 0.2f, 1.0f);

    // Returns a blended packed colour based on the sand, silt, and clay proportions.
    public static float getColor(float sand, float silt, float clay) {
        float total = sand + silt + clay;

        // If no soil composition has been assigned, use the default terrain colour.
        if (total == 0.0f) {
            return DEFAULT_COLOR.toFloatBits();
        }

        float normsand = sand / total;
        float normclay = clay / total;
        float normsilt = silt / total;

        float red   = normsand * SAND_COLOR.r + normsilt * SILT_COLOR.r + normclay * CLAY_COLOR.r;
        float green = normsand * SAND_COLOR.g + normsilt * SILT_COLOR.g + normclay * CLAY_COLOR.g;
        float blue  = normsand * SAND_COLOR.b + normsilt * SILT_COLOR.b + normclay * CLAY_COLOR.b;

        return Color.toFloatBits(red, green, blue, 1.0f);
    }

    /**
     * Depth ramp. Maps soil thickness to a green -> gold -> deep-red
     * gradient. Deliberately shares no colours with the composition
     * palette so the two view modes are visually unambiguous.
     *
     * @param depth     soil depth in metres
     * @param maxDepth  the largest soil depth currently present, used
     *                  as the full-scale value so the ramp spans 0..1
     */
    public static float getDepthColor(float depth, float maxDepth) {
        float t = (maxDepth > 0.001f)
            ? Math.min(Math.max(depth / maxDepth, 0f), 1f)
            : 0f;

        float r, g, b;
        if (t < 0.5f) {
            float u = t * 2f;
            r = 0.65f + (0.95f - 0.65f) * u;
            g = 0.85f + (0.75f - 0.85f) * u;
            b = 0.60f + (0.30f - 0.60f) * u;
        } else {
            float u = (t - 0.5f) * 2f;
            r = 0.95f + (0.55f - 0.95f) * u;
            g = 0.75f + (0.15f - 0.75f) * u;
            b = 0.30f + (0.10f - 0.30f) * u;
        }

        return Color.toFloatBits(r, g, b, 1.0f);
    }
}

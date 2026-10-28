package io.earthbenders.earthsketch;

import org.junit.Test;

import com.badlogic.gdx.graphics.Color;

import static org.junit.Assert.*;

public class SoilPaletteTest {

    @Test
    public void testGetColor_PureSand() {
        // Base colour used for pure sand.
        final Color SAND_COLOR = new Color(1.0f, 0.4f, 0.7f, 1.0f);
        float color = SoilPalette.getColor(1.0f, 0.0f, 0.0f);
        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(SAND_COLOR, unpacked);
    }

    @Test
    public void testGetColor_PureSilt() {
        // Base colour used for pure silt.
        final Color SILT_COLOR = new Color(0.0f, 0.4f, 1.0f, 1.0f);

        float color = SoilPalette.getColor(0.0f, 1.0f, 0.0f);
        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(SILT_COLOR, unpacked);
    }

    @Test
    public void testGetColor_PureClay() {
        // Base colour used for pure clay.
        final Color CLAY_COLOR = new Color(1.0f, 0.5f, 0.0f, 1.0f);

        float color = SoilPalette.getColor(0.0f, 0.0f, 1.0f);
        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(CLAY_COLOR, unpacked);
    }

    @Test
    public void testGetColor_Balanced() {
        // Base colour used for pure sand.
        final Color SAND_COLOR = new Color(1.0f, 0.4f, 0.7f, 1.0f);
        // Base colour used for pure silt.
        final Color SILT_COLOR = new Color(0.0f, 0.4f, 1.0f, 1.0f);
        // Base colour used for pure clay.
        final Color CLAY_COLOR = new Color(1.0f, 0.5f, 0.0f, 1.0f);

        float color = SoilPalette.getColor(0.33f, 0.33f, 0.34f);
        float total = 0.33f + 0.33f + 0.34f;

        float normsand = 0.33f/total;
        float normsilt = 0.33f/total;
        float normclay = 0.34f/total;

        // Blend the red channel using the soil composition proportions.
        float red = normsand * SAND_COLOR.r + normsilt * SILT_COLOR.r + normclay * CLAY_COLOR.r;

        // Blend the green channel using the soil composition proportions.
        float green = normsand * SAND_COLOR.g + normsilt * SILT_COLOR.g + normclay * CLAY_COLOR.g;

        // Blend the blue channel using the soil composition proportions.
        float blue = normsand * SAND_COLOR.b + normsilt * SILT_COLOR.b + normclay * CLAY_COLOR.b;

        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(red, unpacked.r, 0.005f);
        assertEquals(green, unpacked.g, 0.005f);
        assertEquals(blue, unpacked.b, 0.005f);
    }

    @Test
    public void testGetColor_Default() {
        float color = SoilPalette.getColor(0.0f, 0.0f, 0.0f);
        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(0.55f, unpacked.r, 0.005f);
        assertEquals(0.4f, unpacked.g, 0.005f);
        assertEquals(0.2f, unpacked.b, 0.005f);
    }

    @Test
    public void testGetColor_AllSame() {
        // Base colour used for pure sand.
        final Color SAND_COLOR = new Color(1.0f, 0.4f, 0.7f, 1.0f);
        // Base colour used for pure silt.
        final Color SILT_COLOR = new Color(0.0f, 0.4f, 1.0f, 1.0f);
        // Base colour used for pure clay.
        final Color CLAY_COLOR = new Color(1.0f, 0.5f, 0.0f, 1.0f);

        float total = 0.5f + 0.5f + 0.5f;

        float normsand = 0.5f/total;
        float normsilt = 0.5f/total;
        float normclay = 0.5f/total;

        // Blend the red channel using the soil composition proportions.
        float red = normsand * SAND_COLOR.r + normsilt * SILT_COLOR.r + normclay * CLAY_COLOR.r;

        // Blend the green channel using the soil composition proportions.
        float green = normsand * SAND_COLOR.g + normsilt * SILT_COLOR.g + normclay * CLAY_COLOR.g;

        // Blend the blue channel using the soil composition proportions.
        float blue = normsand * SAND_COLOR.b + normsilt * SILT_COLOR.b + normclay * CLAY_COLOR.b;

        float color = SoilPalette.getColor(0.5f, 0.5f, 0.5f);
        Color unpacked = new Color();

        Color.abgr8888ToColor(unpacked, color);
        assertEquals(red, unpacked.r, 0.005f);
        assertEquals(green, unpacked.g, 0.005f);
        assertEquals(blue, unpacked.b, 0.005f);
    }

    @Test
    public void testGetColor_ReturnsPackedFloat() {
        float color = SoilPalette.getColor(0.33f, 0.33f, 0.34f);

        Color unpacked = new Color();
        Color.abgr8888ToColor(unpacked, color);

        // a valid packed color should always unpack to channels in [0,1]
        assertTrue(unpacked.r >= 0f && unpacked.r <= 1f);
        assertTrue(unpacked.g >= 0f && unpacked.g <= 1f);
        assertTrue(unpacked.b >= 0f && unpacked.b <= 1f);
        assertTrue(unpacked.a >= 0f && unpacked.a <= 1f);
    }

    @Test
    public void testGetColor_DifferentCompositions_ProduceDifferentColors() {
        float color1 = SoilPalette.getColor(1.0f, 0.0f, 0.0f);
        float color2 = SoilPalette.getColor(0.0f, 1.0f, 0.0f);
        float color3 = SoilPalette.getColor(0.0f, 0.0f, 1.0f);

        assertNotEquals(color1, color2, 0.001f);
        assertNotEquals(color2, color3, 0.001f);
        assertNotEquals(color1, color3, 0.001f);
    }

    @Test
    public void testGetColor_MixedComposition() {
        // Base colour used for pure sand.
        final Color SAND_COLOR = new Color(1.0f, 0.4f, 0.7f, 1.0f);
        // Base colour used for pure silt.
        final Color SILT_COLOR = new Color(0.0f, 0.4f, 1.0f, 1.0f);
        // Base colour used for pure clay.
        final Color CLAY_COLOR = new Color(1.0f, 0.5f, 0.0f, 1.0f);


        float color = SoilPalette.getColor(0.6f, 0.3f, 0.1f);
        //check that the returned color values follow the formula
        float red = 0.6f * SAND_COLOR.r + 0.3f * SILT_COLOR.r + 0.1f * CLAY_COLOR.r;
        float green = 0.6f * SAND_COLOR.g + 0.3f * SILT_COLOR.g + 0.1f * CLAY_COLOR.g;
        float blue = 0.6f * SAND_COLOR.b + 0.3f * SILT_COLOR.b + 0.1f * CLAY_COLOR.b;

        Color unpacked = new Color();
        Color.abgr8888ToColor(unpacked, color);

        //check rgb values lie in range
        assertEquals(red, unpacked.r, 0.005f);
        assertEquals(green, unpacked.g , 0.005f );
        assertEquals(blue, unpacked.b, 0.005f);

    }

    @Test
    public void testGetColor_ExtremeValues() {
        // Test with values that sum to more than 1
        float color1 = SoilPalette.getColor(2.0f, 0.0f, 0.0f);

        assertNotEquals(0.0f, color1);
        assertTrue( 1f > color1 );

        // Test with values that are extremely small and sum to less than one
        float color2 = SoilPalette.getColor(0.001f, 0.001f, 0.001f);
        assertNotEquals(0.0f, color2);
}
}

package io.earthbenders.earthsketch;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

import static org.junit.Assert.*;

public class ElvLoaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private ElvLoader loader;

    @Before
    public void setUp() {
        loader = new ElvLoader();
    }

    // ==================================================================
    // Valid loads
    // ==================================================================

    @Test
    public void testLoadValidElvFile() throws IOException {
        File tempFile = tempFolder.newFile("valid.elv");
        TestTerrainData.writeFlatElv(tempFile);

        loader.load(tempFile.getAbsolutePath());

        assertEquals(4, loader.dimX);
        assertEquals(3, loader.dimY);
        assertEquals(1.0f, loader.gridSpacing, 0.001f);
        assertEquals(45.0f, loader.latitude, 0.001f);
        assertNotNull(loader.heights);
        assertEquals(12, loader.heights.length);
    }

    @Test
    public void testLoadElvWithSoilData() throws IOException {
        File tempFile = tempFolder.newFile("with_soil.elv");
        TestTerrainData.writeElvWithSoil(tempFile);

        loader.load(tempFile.getAbsolutePath());

        assertEquals(12, loader.soilDepth.length);
        assertEquals(0.5f, loader.soilDepth[0], 0.001f);
        assertEquals(0.6f, loader.sand[0], 0.001f);
        assertEquals(0.3f, loader.silt[0], 0.001f);
        assertEquals(0.1f, loader.clay[0], 0.001f);
    }

    @Test
    public void testLoadMissingSoilDataLeavesCompositionUnset() throws IOException {
        File tempFile = tempFolder.newFile("no_soil.elv");
        TestTerrainData.writeFlatElv(tempFile);

        loader.load(tempFile.getAbsolutePath());

        assertNotNull(loader.sand);
        for (int i = 0; i < loader.sand.length; i++) {
            assertEquals("Sand at " + i + " should be unassigned",
                0f, loader.sand[i], 0.0001f);
            assertEquals("Silt at " + i + " should be unassigned",
                0f, loader.silt[i], 0.0001f);
            assertEquals("Clay at " + i + " should be unassigned",
                0f, loader.clay[i], 0.0001f);
        }
    }

    @Test
    public void testLoadLargeFile() throws IOException {
        File tempFile = tempFolder.newFile("large.elv");
        TestTerrainData.writeLargeElv(tempFile, 100);

        loader.load(tempFile.getAbsolutePath());

        assertEquals(100, loader.dimX);
        assertEquals(100, loader.dimY);
        assertEquals(10000, loader.heights.length);
        assertEquals(10000, loader.soilDepth.length);
    }

    // ==================================================================
    // Invalid input
    // ==================================================================

    @Test(expected = IOException.class)
    public void testLoadInvalidHeader() throws IOException {
        File tempFile = tempFolder.newFile("invalid_header.elv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            bw.write("abc def 1.0 45.0");
            bw.newLine();
        }
        loader.load(tempFile.getAbsolutePath());
    }

    @Test(expected = IOException.class)
    public void testLoadEmptyFile() throws IOException {
        File tempFile = tempFolder.newFile("empty.elv");
        loader.load(tempFile.getAbsolutePath());
    }

    @Test(expected = IOException.class)
    public void testLoadIncompleteHeightData() throws IOException {
        File tempFile = tempFolder.newFile("incomplete.elv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            bw.write("4 3 1.0 45.0");
            bw.newLine();
            bw.write("1 2 3 4");
            bw.newLine();
            bw.write("5 6 7 8");
            bw.newLine();
        }
        loader.load(tempFile.getAbsolutePath());
    }

    @Test(expected = IOException.class)
    public void testLoadInvalidSoilData() throws IOException {
        File tempFile = tempFolder.newFile("invalid_soil.elv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            bw.write("4 3 1.0 45.0");
            bw.newLine();
            for (int i = 0; i < 3; i++) {
                bw.write("1.0 1.0 1.0 1.0");
                bw.newLine();
            }
            bw.write("SOIL");
            bw.newLine();
            bw.write("1.0,2.0,3.0,4.0");
            bw.newLine();
            bw.write("1.0,2.0,3.0"); // only 3 values, expected 4
            bw.newLine();
        }
        loader.load(tempFile.getAbsolutePath());
    }

    @Test(expected = IOException.class)
    public void testLoadHeaderWithWrongNumberOfFields() throws IOException {
        File tempFile = tempFolder.newFile("bad_header_count.elv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            bw.write("4 3 1.0");
            bw.newLine();
        }
        loader.load(tempFile.getAbsolutePath());
    }

    @Test(expected = IOException.class)
    public void testLoadNonNumericHeight() throws IOException {
        File tempFile = tempFolder.newFile("bad_height.elv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
            bw.write("2 2 1.0 45.0");
            bw.newLine();
            bw.write("1.0 abc");
            bw.newLine();
            bw.write("3.0 4.0");
            bw.newLine();
        }
        loader.load(tempFile.getAbsolutePath());
    }

    // ==================================================================
    // Save round-trip
    // ==================================================================

    @Test
    public void testSaveAndLoad() throws IOException {
        File loadFile = tempFolder.newFile("valid.elv");
        TestTerrainData.writeElvWithSoil(loadFile);
        loader.load(loadFile.getAbsolutePath());

        File saveFile = tempFolder.newFile("saved.elv");
        loader.save(saveFile.getAbsolutePath());

        ElvLoader saved = new ElvLoader();
        saved.load(saveFile.getAbsolutePath());

        assertEquals(loader.dimX, saved.dimX);
        assertEquals(loader.dimY, saved.dimY);
        assertEquals(loader.gridSpacing, saved.gridSpacing, 0.001f);
        assertEquals(loader.latitude, saved.latitude, 0.001f);
        assertArrayEquals(loader.heights, saved.heights, 0.001f);
        assertArrayEquals(loader.soilDepth, saved.soilDepth, 0.001f);
        assertArrayEquals(loader.sand, saved.sand, 0.001f);
        assertArrayEquals(loader.silt, saved.silt, 0.001f);
        assertArrayEquals(loader.clay, saved.clay, 0.001f);
    }

    // ==================================================================
    // Debug method
    // ==================================================================

    @Test
    public void testDebugFalseIsNoOp() throws IOException {
        File tempFile = tempFolder.newFile("debug.elv");
        TestTerrainData.writeFlatElv(tempFile);
        loader.load(tempFile.getAbsolutePath());

        // verbose=false does nothing; must not throw.
        loader.debug(false);
    }
}

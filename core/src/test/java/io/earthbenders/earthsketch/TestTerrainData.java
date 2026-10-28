package io.earthbenders.earthsketch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public final class TestTerrainData {

    private TestTerrainData() {}

    // ------------------------------------------------------------------
    // In-memory loader factories
    // ------------------------------------------------------------------

    /** Flat 4x3 terrain, gridSpacing 1, height 1.0, uniform soil 0.5m. */
    public static ElvLoader flatSmall() {
        ElvLoader l = new ElvLoader();
        l.dimX = 4;
        l.dimY = 3;
        l.gridSpacing = 1.0f;
        l.latitude = 45.0f;

        int n = l.dimX * l.dimY;
        l.heights = new float[n];
        l.soilDepth = new float[n];
        l.sand = new float[n];
        l.silt = new float[n];
        l.clay = new float[n];
        l.maxDepth = new float[n];
        l.smoothHeights = new float[n];

        for (int i = 0; i < n; i++) {
            l.heights[i] = 1.0f;
            l.soilDepth[i] = 0.5f;
            l.sand[i] = 0.33f;
            l.silt[i] = 0.33f;
            l.clay[i] = 0.34f;
            l.maxDepth[i] = 2.0f;
            l.smoothHeights[i] = 1.0f;
        }
        return l;
    }

    /** Flat 10x10 terrain, gridSpacing 1, height 1.0, maxDepth 2.0m. */
    public static ElvLoader flatMedium() {
        ElvLoader l = new ElvLoader();
        l.dimX = 10;
        l.dimY = 10;
        l.gridSpacing = 1.0f;
        l.latitude = 45.0f;

        int n = 100;
        l.heights = new float[n];
        l.soilDepth = new float[n];
        l.sand = new float[n];
        l.silt = new float[n];
        l.clay = new float[n];
        l.maxDepth = new float[n];
        l.smoothHeights = new float[n];

        for (int i = 0; i < n; i++) {
            l.heights[i] = 1.0f;
            l.soilDepth[i] = 0.5f;
            l.sand[i] = 0.33f;
            l.silt[i] = 0.33f;
            l.clay[i] = 0.34f;
            l.maxDepth[i] = 2.0f;
            l.smoothHeights[i] = 1.0f;
        }
        return l;
    }

    /** 3x3 height array with a single sharp peak in the centre. */
    public static float[] peakHeights3x3() {
        return new float[]{
            0, 0, 0,
            1, 5, 1,
            2, 2, 2
        };
    }

    /** 3x3 height array with a single valley in the centre. */
    public static float[] valleyHeights3x3() {
        return new float[]{
            5, 5, 5,
            5, 0, 5,
            5, 5, 5
        };
    }

    /** Completely flat 3x3 height array. */
    public static float[] flatHeights3x3() {
        return new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1};
    }

    // ------------------------------------------------------------------
    // ELV file writers
    // ------------------------------------------------------------------

    /** Writes a minimal valid ELV: 4x3, no SOIL block, heights all 1.0. */
    public static void writeFlatElv(File file) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("4 3 1.0 45.0");
            bw.newLine();
            for (int row = 0; row < 3; row++) {
                bw.write("1.0 1.0 1.0 1.0");
                bw.newLine();
            }
        }
    }

    /** Writes a 4x3 ELV with a SOIL block, uniform composition. */
    public static void writeElvWithSoil(File file) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("4 3 1.0 45.0");
            bw.newLine();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 4; col++) {
                    bw.write((row * 4 + col + 1) + " ");
                }
                bw.newLine();
            }
            bw.write("SOIL");
            bw.newLine();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 4; col++) {
                    bw.write("0.5,0.6,0.3,0.1 ");
                }
                bw.newLine();
            }
        }
    }

    /** Writes a dim x dim square ELV with heights increasing linearly. */
    public static void writeLargeElv(File file, int dim) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(dim + " " + dim + " 1.0 45.0");
            bw.newLine();
            for (int row = 0; row < dim; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < dim; col++) {
                    line.append((float) (row * dim + col) * 0.1f);
                    if (col < dim - 1) line.append(' ');
                }
                bw.write(line.toString());
                bw.newLine();
            }
            bw.write("SOIL");
            bw.newLine();
            for (int row = 0; row < dim; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < dim; col++) {
                    line.append("0.01,0.6,0.3,0.1");
                    if (col < dim - 1) line.append(' ');
                }
                bw.write(line.toString());
                bw.newLine();
            }
        }
    }
}

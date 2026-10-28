package io.earthbenders.earthsketch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.io.File; 

public class ElvLoader {

    // Terrain dimensions and metadata read from the ELV header.
    public int dimX, dimY;
    public float gridSpacing, latitude;

    // Terrain elevation and authored soil data stored as flattened 1D arrays.
    public float[] heights;
    public float[] soilDepth;
    public float[] sand;
    public float[] silt;
    public float[] clay; 
    public float[] maxDepth;
    public float[] smoothHeights;

    // Enables optional debugging output.
    public boolean verbose = false;

    // Maximum supported terrain size.
    private static final long MAX_TERRAIN_POINTS = 16_777_216L; // 4096 × 4096

    // Prevent excessively large files from exhausting application resources.
    private static final long MAX_FILE_SIZE_BYTES = 1_073_741_824L; // 1 GiB

    // Loads a small ELV file and prints its elevation values when debugging is enabled.
    public void debug(boolean verbose) throws IOException {
        if (verbose) {
            String path = "EarthSketch/assets/DATA/D1-256/D1-256.elv"; //testing this is terrible simply because of how a unit test should not have to use actual files, and this needs to load an actual file...tsk tsk
            ElvLoader loader = new ElvLoader();
            loader.load(path);
            System.out.println(Arrays.toString(loader.heights));
        }
    }

    // Loads terrain elevation data and, if present, authored soil data from an ELV file.
    public void load(String path) throws IOException {
        File file = new File(path);

        if (!file.isFile()) {
            throw new IOException(
                "Invalid ELV file: file does not exist or is not a regular file."
            );
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            throw new IOException(
                "Invalid ELV file: file is too large to load safely."
            );
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            // Read and validate the ELV header.
            String headerLine = br.readLine();

            if (headerLine == null) {
                throw new IOException("Invalid ELV file: missing header.");
            }

            String[] header = headerLine.trim().split("\\s+");

            if (header.length != 4) {
                throw new IOException(
                    "Invalid ELV file: expected 4 header values but found "
                        + header.length
                );
            }

            try {
                int newDimX = Integer.parseInt(header[0]);
                int newDimY = Integer.parseInt(header[1]);
                float newGridSpacing = Float.parseFloat(header[2]);
                float newLatitude = Float.parseFloat(header[3]);

                if (newDimX < 2 || newDimY < 2){
                    throw new IOException("Invalid ELV file: terrain dimensions must both be at least 2.");
                }

                if (!Float.isFinite(newGridSpacing) || newGridSpacing <= 0f) {
                    throw new IOException("Invalid ELV file: grid spacing must be a positive finite value.");
                }

                if (!Float.isFinite(newLatitude)) {
                    throw new IOException("Invalid ELV file: latitude must be a finite value.");
                }

                // Calculate using long so multiplication itself cannot overflow.
                long totalPointsLong = (long) newDimX * (long) newDimY;

                // Java arrays are indexed using int, so the terrain cannot exceed the maximum representable array size.
                if (totalPointsLong > Integer.MAX_VALUE) {
                    throw new IOException(
                        "Invalid ELV file: terrain dimensions are too large."
                    );
                }

                if (totalPointsLong > MAX_TERRAIN_POINTS){
                    throw new IOException("Terrain is too large to load safely.");
                }

                int totalPoints = (int) totalPointsLong;

                float[] newHeights = new float[totalPoints];
                float[] newSoilDepth = new float[totalPoints];
                float[] newSand = new float[totalPoints];
                float[] newSilt = new float[totalPoints];
                float[] newClay = new float[totalPoints];
                float[] newMaxDepth = new float[totalPoints];

                String line;

                int heightIdx = 0;

                while (heightIdx < totalPoints
                        && (line = br.readLine()) != null) {

                    String trimmedLine = line.trim();

                    // Ignore blank lines.
                    if (trimmedLine.isEmpty()){
                        continue;
                    }

                    // Soil cannot begin before all elevation values have been read.
                    if (trimmedLine.equalsIgnoreCase("SOIL")) {
                        throw new IOException("Invalid ELV file: SOIL section begins before all elevation values were provided.");
                    }

                    String[] vals = line.trim().split("\\s+");

                    for (String value : vals) {
                        if (heightIdx >= totalPoints){
                            throw new IOException("Invalid ELV file: too many elevation values. Expected " + totalPoints + ".");
                        }

                        float height = Float.parseFloat(value);

                        if (!Float.isFinite(height)){
                            throw new IOException("Invalid ELV file: non-finite elevation value at point " + heightIdx + ".");
                        }
                        
                        newHeights[heightIdx] = height;
                        heightIdx++;
                        
                    }
                }

                if (heightIdx != totalPoints) {
                    throw new IOException(
                        "Invalid ELV file: expected "
                            + totalPoints
                            + " height values but found "
                            + heightIdx
                    );
                }

                boolean hasSoil = false;

                while ((line = br.readLine()) != null) {

                    String trimmedLine = line.trim();

                    // Blank lines between elevation data and SOIL are allowed.
                    if (trimmedLine.isEmpty()) {
                        continue;
                    }

                    // If SOIL is present, begin reading soil data.
                    if (trimmedLine.equalsIgnoreCase("SOIL")) {
                        hasSoil = true;
                        break;
                    }

                    // Anything else after the expected elevation values is invalid.
                    throw new IOException(
                        "Invalid ELV file: unexpected data after elevation values. "
                            + "Expected SOIL section or end of file."
                    );
                }

                if (hasSoil) {
                    int soilIdx = 0;

                    while (soilIdx < totalPoints
                            && (line = br.readLine()) != null) {

                        if (line.trim().isEmpty()) {
                            continue;
                        }

                        String[] values = line.trim().split("\\s+");

                        for (String value : values) {
                            if (value.isEmpty()) {
                                continue;
                            }

                            if (soilIdx >= totalPoints) {
                                throw new IOException(
                                    "Invalid SOIL section: too many soil values. Expected "
                                        + totalPoints + "."
                                );
                            }

                            String[] items = value.split(",");

                            if (items.length != 4) {
                                throw new IOException(
                                    "Invalid soil data at point "
                                        + soilIdx
                                        + ": expected depth,sand,silt,clay"
                                );
                            }

                            float depth = Float.parseFloat(items[0].trim());
                            float sandValue = Float.parseFloat(items[1].trim());
                            float siltValue = Float.parseFloat(items[2].trim());
                            float clayValue = Float.parseFloat(items[3].trim());

                            // All soil values must be real, finite numbers.
                            if (!Float.isFinite(depth)
                                    || !Float.isFinite(sandValue)
                                    || !Float.isFinite(siltValue)
                                    || !Float.isFinite(clayValue)) {

                                throw new IOException(
                                    "Invalid soil data at point "
                                        + soilIdx
                                        + ": soil values must be finite."
                                );
                            }

                            // Soil depth cannot be negative.
                            if (depth < 0f) {
                                throw new IOException(
                                    "Invalid soil data at point "
                                        + soilIdx
                                        + ": soil depth cannot be negative."
                                );
                            }

                            // Soil composition values are stored as proportions from 0 to 1.
                            if (sandValue < 0f || sandValue > 1f
                                    || siltValue < 0f || siltValue > 1f
                                    || clayValue < 0f || clayValue > 1f) {

                                throw new IOException(
                                    "Invalid soil data at point "
                                        + soilIdx
                                        + ": sand, silt and clay must each be between 0 and 1."
                                );
                            }

                            // A point may either have no authored composition yet (0,0,0),
                            // or a complete composition whose proportions sum to 1.
                            float totalComposition = sandValue + siltValue + clayValue;

                            boolean unassignedComposition =
                                sandValue == 0f
                                    && siltValue == 0f
                                    && clayValue == 0f;

                            if (!unassignedComposition
                                    && Math.abs(totalComposition - 1f) > 0.001f) {

                                throw new IOException(
                                    "Invalid soil data at point "
                                        + soilIdx
                                        + ": sand, silt and clay must sum to 1."
                                );
                            }

                            // Only store the values once they have passed validation.
                            newSoilDepth[soilIdx] = depth;
                            newSand[soilIdx] = sandValue;
                            newSilt[soilIdx] = siltValue;
                            newClay[soilIdx] = clayValue;

                            soilIdx++;
                        }
                    }

                    if (soilIdx != totalPoints) {
                        throw new IOException(
                            "Invalid SOIL section: expected "
                                + totalPoints
                                + " soil values but found "
                                + soilIdx
                        );
                    }

                    while ((line = br.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            throw new IOException(
                                "Invalid ELV file: unexpected data after SOIL section."
                            );
                        }
                    }
                }
                float[] newSmoothHeights =
                    GaussianBlur.blurCopy(
                        newHeights,
                        newDimX,
                        newDimY,
                        3,
                        1.5f
                    );

                for (int z = 0; z < newDimY; z++) {
                    for (int x = 0; x < newDimX; x++) {

                        int idx = z * newDimX + x;

                        newMaxDepth[idx] =
                            SoilDepthModel.calculateMaxDepth(
                                newSmoothHeights,
                                x,
                                z,
                                newDimX,
                                newDimY,
                                newGridSpacing
                            );
                    }
                }

                if (hasSoil) {
                    for (int i = 0; i < totalPoints; i++){
                        if (newSoilDepth[i] > newMaxDepth[i]) {
                            throw new IOException("Invalid soil data at point " + i + ": soil depth exceeds the maximum legal depth.");
                        }
                    }
                }

                dimX = newDimX;
                dimY = newDimY;
                gridSpacing = newGridSpacing;
                latitude = newLatitude;

                heights = newHeights;
                soilDepth = newSoilDepth;
                sand = newSand;
                silt = newSilt;
                clay = newClay;
                maxDepth = newMaxDepth;
                smoothHeights = newSmoothHeights;

            } catch (NumberFormatException e) {
                throw new IOException(
                    "Invalid ELV file: expected numeric terrain data.",
                    e
                );
            }
        }
    }

    public int generateTerrainBasedComposition() {

        int generatedPoints = 0;

        for (int z = 0; z < dimY; z++) {
            for (int x = 0; x < dimX; x++) {

                int idx = z * dimX + x;

                float currentTotal =
                    sand[idx]
                    + silt[idx]
                    + clay[idx];

                // Preserve composition that already exists.
                if (currentTotal > 0f) {
                    continue;
                }

                float[] composition =
                    SoilCompositionModel.calculateSoilComposition(
                        smoothHeights,
                        x,
                        z,
                        dimX,
                        dimY,
                        gridSpacing
                    );

                sand[idx] = composition[0];
                silt[idx] = composition[1];
                clay[idx] = composition[2];

                generatedPoints++;
            }
        }

        return generatedPoints;
    }

    // Saves the full terrain and soil data by rewriting the ELV file.
    public void save(String path) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {

            // Write the ELV header.
            bw.write(dimX + " " + dimY + " " + gridSpacing + " " + latitude);
            bw.newLine();

            // Write the terrain elevation values row by row.
            int heightIdx = 0;

            for (int z = 0; z < dimY; z++) {
                StringBuilder line = new StringBuilder();

                for (int x = 0; x < dimX; x++) {
                    line.append(heights[heightIdx]);
                    heightIdx++;

                    if (x < dimX - 1) {
                        line.append(" ");
                    }
                }

                bw.write(line.toString());
                bw.newLine();
            }

            // Mark the beginning of the authored soil data.
            bw.write("SOIL");
            bw.newLine();

            // Write depth, sand, silt, and clay values for every terrain point.
            int soilIdx = 0;

            for (int z = 0; z < dimY; z++) {
                StringBuilder line = new StringBuilder();

                for (int x = 0; x < dimX; x++) {
                    line.append(soilDepth[soilIdx]).append(",").append(sand[soilIdx]).append(",").append(silt[soilIdx]).append(",").append(clay[soilIdx]);
                    soilIdx++;

                    if (x < dimX - 1) {
                        line.append(" ");
                    }
                }

                bw.write(line.toString());
                bw.newLine();
            }
        }
    }

    /**
     * Returns a new ElvLoader containing the selected rectangular
     * subsection of this terrain.
     *
     * The rectangle is inclusive:
     * [gxMin..gxMax] x [gzMin..gzMax].
     *
     * Returns null if the selected area is smaller than 2x2.
     */
    public ElvLoader cropTo(int gxMin, int gzMin, int gxMax, int gzMax) {
        int newDimX = gxMax - gxMin + 1;
        int newDimY = gzMax - gzMin + 1;

        if (newDimX < 2 || newDimY < 2) {
            return null;
        }

        ElvLoader out = new ElvLoader();

        out.dimX = newDimX;
        out.dimY = newDimY;
        out.gridSpacing = this.gridSpacing;
        out.latitude = this.latitude;
        out.verbose = this.verbose;

        int n = newDimX * newDimY;

        out.heights = new float[n];
        out.soilDepth = new float[n];
        out.sand = new float[n];
        out.silt = new float[n];
        out.clay = new float[n];
        out.maxDepth = new float[n];
        out.smoothHeights = new float[n];

        for (int z = 0; z < newDimY; z++) {
            for (int x = 0; x < newDimX; x++) {
                int srcIdx = (gzMin + z) * this.dimX + (gxMin + x);
                int dstIdx = z * newDimX + x;

                out.heights[dstIdx] = this.heights[srcIdx];
                out.soilDepth[dstIdx] = this.soilDepth[srcIdx];
                out.sand[dstIdx] = this.sand[srcIdx];
                out.silt[dstIdx] = this.silt[srcIdx];
                out.clay[dstIdx] = this.clay[srcIdx];
                out.maxDepth[dstIdx] = this.maxDepth[srcIdx];
                out.smoothHeights[dstIdx] = this.smoothHeights[srcIdx];
            }
        }

        return out;
    }
}
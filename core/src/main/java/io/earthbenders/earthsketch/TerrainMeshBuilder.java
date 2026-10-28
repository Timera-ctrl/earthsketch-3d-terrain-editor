package io.earthbenders.earthsketch;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.math.Vector3;

public class TerrainMeshBuilder {

    /**
     * What the terrain surface colour represents.
     * NORMAL      -> neutral terrain colour
     * COMPOSITION -> soil composition palette (pink/blue/orange)
     * DEPTH       -> soil thickness ramp (green/gold/red)
     */
    public enum ViewMode {
        COMPOSITION,
        DEPTH
    }

    // Vertex layout used by every terrain mesh.
    // Stride 9 floats: pos.xyz, normal.xyz, packedColor, texCoord.x, texCoord.y
    //   texCoord.x = baseline height at this vertex (0 for skirt/bottom)
    //   texCoord.y = 1.0 if baseline is valid, 0.0 otherwise
    private static final VertexAttribute[] TERRAIN_ATTRS = {
        VertexAttribute.Position(),
        VertexAttribute.Normal(),
        VertexAttribute.ColorPacked(),
        VertexAttribute.TexCoords(0)
    };

    private static final int FLOATS_PER_VERTEX = 9;

    public static Vector3 slopeNorm(float[] heights, int x, int z, int dimX, int dimY, float gridSpacing) {
        int xLeft = Math.max(x - 1, 0);
        int xRight = Math.min(x + 1, dimX - 1);
        int zUp = Math.max(z - 1, 0);
        int zDown = Math.min(z + 1, dimY - 1);

        // Read the heights of the neighbouring grid points
        float hL = heights[z * dimX + xLeft];
        float hR = heights[z * dimX + xRight];
        float hU = heights[zUp * dimX + x];
        float hD = heights[zDown * dimX + x];

        float slopeX = (hL - hR) / ((xLeft - xRight) * gridSpacing);
        float slopeZ = (hU - hD) / ((zUp - zDown) * gridSpacing);

        Vector3 normal = new Vector3(-slopeX, 1f, -slopeZ);
        normal.nor();
        return normal;
    }

    public static List<Mesh> buildTerrain(ElvLoader loader, float[] baseline) {
        int tileSize = 128;
        int dimX = loader.dimX;
        int dimY = loader.dimY;
        float gridSpacing = loader.gridSpacing;
        float[] heights = loader.heights;

        List<Mesh> tilePieces = new ArrayList<>();

        for (int tileZ = 0; tileZ < dimY - 1; tileZ += tileSize - 1) {
            for (int tileX = 0; tileX < dimX - 1; tileX += tileSize - 1) {
                int endX = Math.min(tileX + tileSize, dimX);
                int endZ = Math.min(tileZ + tileSize, dimY);
                tilePieces.add(buildSubTerrain(
                    dimX, dimY, tileX, tileZ, endX, endZ,
                    gridSpacing, heights, baseline));
            }
        }
        return tilePieces;
    }

    public static Mesh buildSubTerrain(int dimX, int dimY, int tileX, int tileZ,
                                        int endX, int endZ,
                                        float gridSpacing, float[] heights, float[] baseline) {
        int subX = endX - tileX;
        int subZ = endZ - tileZ;
        int numVertices = subX * subZ;

        float[] vertices = new float[numVertices * FLOATS_PER_VERTEX];
        int vi = 0;

        for (int z = 0; z < subZ; z++) {
            for (int x = 0; x < subX; x++) {
                int gx = tileX + x;
                int gz = tileZ + z;
                int gIdx = gz * dimX + gx;

                vertices[vi++] = gx * gridSpacing;
                vertices[vi++] = heights[gIdx];
                vertices[vi++] = gz * gridSpacing;

                Vector3 normal = slopeNorm(heights, gx, gz, dimX, dimY, gridSpacing);
                vertices[vi++] = normal.x;
                vertices[vi++] = normal.y;
                vertices[vi++] = normal.z;

                vertices[vi++] = Color.toFloatBits(0.55f, 0.4f, 0.2f, 1.0f);

                vertices[vi++] = (baseline != null) ? baseline[gIdx] : 0f;
                vertices[vi++] = 1.0f;
            }
        }

        int numSpaces = (subX - 1) * (subZ - 1);
        short[] indices = new short[numSpaces * 6];
        int ii = 0;

        for (int z = 0; z < subZ - 1; z++) {
            for (int x = 0; x < subX - 1; x++) {
                int tl = z * subX + x;
                int tr = tl + 1;
                int bl = (z + 1) * subX + x;
                int br = bl + 1;
                indices[ii++] = (short) tl;
                indices[ii++] = (short) bl;
                indices[ii++] = (short) tr;
                indices[ii++] = (short) tr;
                indices[ii++] = (short) bl;
                indices[ii++] = (short) br;
            }
        }

        Mesh mesh = new Mesh(true, numVertices, indices.length, TERRAIN_ATTRS);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    public static Mesh buildTerrainSkirt(ElvLoader loader) {
        int dimX = loader.dimX;
        int dimY = loader.dimY;
        float gridSpacing = loader.gridSpacing;
        float[] heights = loader.heights;

        float terrainWidth = (dimX - 1) * gridSpacing;
        float terrainHeight = (dimY - 1) * gridSpacing;

        float minHeight = Float.MAX_VALUE;
        for (float h : heights) if (h < minHeight) minHeight = h;

        float skirtBottom = minHeight - 100f;
        int segments = Math.min(Math.max(dimX, dimY), 256);

        float[][][] edges = {
            {{0, 0}, {terrainWidth, 0}},
            {{terrainWidth, 0}, {terrainWidth, terrainHeight}},
            {{terrainWidth, terrainHeight}, {0, terrainHeight}},
            {{0, terrainHeight}, {0, 0}}
        };

        int totalVertices = 4 * segments * 2;
        float[] vertices = new float[totalVertices * FLOATS_PER_VERTEX];
        int totalIndices = 4 * (segments - 1) * 6;
        short[] indices = new short[totalIndices];

        int vi = 0, ii = 0;

        for (int e = 0; e < 4; e++) {
            float startX = edges[e][0][0], startZ = edges[e][0][1];
            float endX = edges[e][1][0], endZ = edges[e][1][1];

            for (int i = 0; i < segments; i++) {
                float t = (float) i / (segments - 1);
                float x = startX + (endX - startX) * t;
                float z = startZ + (endZ - startZ) * t;

                int ix = Math.round(x / gridSpacing);
                int iz = Math.round(z / gridSpacing);
                ix = Math.max(0, Math.min(dimX - 1, ix));
                iz = Math.max(0, Math.min(dimY - 1, iz));
                float height = heights[iz * dimX + ix];

                float nx = 0f, ny = -0.7f, nz = 0f;
                float eps = 0.1f;
                if (x <= eps) nx = -0.7f;
                else if (x >= terrainWidth - eps) nx = 0.7f;
                else if (z <= eps) nz = -0.7f;
                else if (z >= terrainHeight - eps) nz = 0.7f;

                if (x <= eps && z <= eps) { nx = -0.7f; nz = -0.7f; }
                else if (x <= eps && z >= terrainHeight - eps) { nx = -0.7f; nz = 0.7f; }
                else if (x >= terrainWidth - eps && z <= eps) { nx = 0.7f; nz = -0.7f; }
                else if (x >= terrainWidth - eps && z >= terrainHeight - eps) { nx = 0.7f; nz = 0.7f; }

                float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
                if (len > 0f) { nx /= len; ny /= len; nz /= len; }

                vertices[vi++] = x; vertices[vi++] = height; vertices[vi++] = z;
                vertices[vi++] = nx; vertices[vi++] = ny; vertices[vi++] = nz;
                vertices[vi++] = Color.toFloatBits(0.4f, 0.3f, 0.15f, 1.0f);
                vertices[vi++] = 0f; vertices[vi++] = 0f;

                vertices[vi++] = x; vertices[vi++] = skirtBottom; vertices[vi++] = z;
                vertices[vi++] = nx; vertices[vi++] = ny; vertices[vi++] = nz;
                vertices[vi++] = Color.toFloatBits(0.15f, 0.1f, 0.05f, 1.0f);
                vertices[vi++] = 0f; vertices[vi++] = 0f;
            }

            int baseVertex = e * segments * 2;
            for (int i = 0; i < segments - 1; i++) {
                int tl = baseVertex + i * 2;
                int tr = tl + 2;
                int bl = tl + 1;
                int br = tr + 1;
                indices[ii++] = (short) tl;
                indices[ii++] = (short) bl;
                indices[ii++] = (short) tr;
                indices[ii++] = (short) tr;
                indices[ii++] = (short) br;
                indices[ii++] = (short) bl;
            }
        }

        Mesh mesh = new Mesh(true, totalVertices, totalIndices, TERRAIN_ATTRS);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    public static Mesh buildTerrainBottom(ElvLoader loader) {
        int dimX = loader.dimX;
        int dimY = loader.dimY;
        float gridSpacing = loader.gridSpacing;
        float[] heights = loader.heights;

        float terrainWidth = (dimX - 1) * gridSpacing;
        float terrainHeight = (dimY - 1) * gridSpacing;

        float minHeight = Float.MAX_VALUE;
        for (float h : heights) if (h < minHeight) minHeight = h;
        float bottomY = minHeight - 100f;

        float[] vertices = new float[4 * FLOATS_PER_VERTEX];
        int vi = 0;

        vertices[vi++] = 0f; vertices[vi++] = bottomY; vertices[vi++] = 0f;
        vertices[vi++] = 0f; vertices[vi++] = -1f; vertices[vi++] = 0f;
        vertices[vi++] = Color.toFloatBits(0.20f, 0.14f, 0.08f, 1.0f);
        vertices[vi++] = 0f; vertices[vi++] = 0f;

        vertices[vi++] = terrainWidth; vertices[vi++] = bottomY; vertices[vi++] = 0f;
        vertices[vi++] = 0f; vertices[vi++] = -1f; vertices[vi++] = 0f;
        vertices[vi++] = Color.toFloatBits(0.20f, 0.14f, 0.08f, 1.0f);
        vertices[vi++] = 0f; vertices[vi++] = 0f;

        vertices[vi++] = terrainWidth; vertices[vi++] = bottomY; vertices[vi++] = terrainHeight;
        vertices[vi++] = 0f; vertices[vi++] = -1f; vertices[vi++] = 0f;
        vertices[vi++] = Color.toFloatBits(0.20f, 0.14f, 0.08f, 1.0f);
        vertices[vi++] = 0f; vertices[vi++] = 0f;

        vertices[vi++] = 0f; vertices[vi++] = bottomY; vertices[vi++] = terrainHeight;
        vertices[vi++] = 0f; vertices[vi++] = -1f; vertices[vi++] = 0f;
        vertices[vi++] = Color.toFloatBits(0.20f, 0.14f, 0.08f, 1.0f);
        vertices[vi++] = 0f; vertices[vi++] = 0f;

        short[] indices = { 0, 1, 2, 0, 2, 3 };
        Mesh mesh = new Mesh(true, 4, 6, TERRAIN_ATTRS);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    /**
     * Updates terrain vertex colours according to the current view mode.
     *
     * COMPOSITION: colour from SoilPalette.getColor(sand, silt, clay).
     * DEPTH:       colour from SoilPalette.getDepthColor(depth, max),
     *              where max is the largest soil depth currently present
     *              so the ramp always spans the full range.
     */
    public static void updateTerrainColors(List<Mesh> terrainMeshes,
                                            ElvLoader loader,
                                            ViewMode mode) {
        float maxSoilDepth = 0.001f;
        for (float d : loader.soilDepth) {
            if (d > maxSoilDepth) maxSoilDepth = d;
        }

        for (Mesh mesh : terrainMeshes) {
            int fpv = mesh.getVertexSize() / 4;
            int numVertices = mesh.getNumVertices();
            float[] vertices = new float[numVertices * fpv];
            mesh.getVertices(vertices);

            for (int i = 0; i < numVertices; i++) {
                int vs = i * fpv;
                float x = vertices[vs];
                float z = vertices[vs + 2];

                int gridX = Math.round(x / loader.gridSpacing);
                int gridZ = Math.round(z / loader.gridSpacing);
                int index = gridZ * loader.dimX + gridX;

                if (mode == ViewMode.DEPTH) {
                    vertices[vs + 6] = SoilPalette.getDepthColor(
                        loader.soilDepth[index], maxSoilDepth);
                } else {
                    vertices[vs + 6] = SoilPalette.getColor(
                        loader.sand[index],
                        loader.silt[index],
                        loader.clay[index]);
                }
            }

            mesh.setVertices(vertices);
        }
    }

    public static void updateTerrainGeometry(List<Mesh> terrainMeshes, ElvLoader loader) {
        for (Mesh mesh : terrainMeshes) {
            int fpv = mesh.getVertexSize() / 4;
            int nv  = mesh.getNumVertices();
            float[] v = new float[nv * fpv];
            mesh.getVertices(v);

            for (int i = 0; i < nv; i++) {
                int vi = i * fpv;
                int gx = Math.round(v[vi] / loader.gridSpacing);
                int gz = Math.round(v[vi + 2] / loader.gridSpacing);
                gx = Math.max(0, Math.min(loader.dimX - 1, gx));
                gz = Math.max(0, Math.min(loader.dimY - 1, gz));
                int idx = gz * loader.dimX + gx;

                v[vi + 1] = loader.heights[idx];

                Vector3 n = slopeNorm(loader.heights, gx, gz,
                    loader.dimX, loader.dimY, loader.gridSpacing);
                v[vi + 3] = n.x;
                v[vi + 4] = n.y;
                v[vi + 5] = n.z;
            }

            mesh.setVertices(v);
        }
    }
}

package io.earthbenders.earthsketch;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a "bed of nails" mesh: one vertical spike per grid point,
 * sampled every `stride` cells. Each spike runs from bedrock to
 * surface, so spike height *is* soil thickness.
 */
public final class SoilNailBuilder {

    private SoilNailBuilder() {}

    private static final VertexAttribute[] ATTRS = {
        VertexAttribute.Position(),
        VertexAttribute.Normal(),
        VertexAttribute.ColorPacked(),
        VertexAttribute.TexCoords(0)
    };

    private static final int FLOATS_PER_VERTEX = 9;
    private static final int VERTS_PER_NAIL     = 8;
    private static final int INDICES_PER_NAIL   = 12;

    // 4000 * 8 = 32000 vertices per chunk, safely under the 32767
    // short-index ceiling regardless of terrain size.
    private static final int MAX_NAILS_PER_CHUNK = 4000;

    public static List<Mesh> build(ElvLoader loader, int stride) {
        List<Mesh> meshes = new ArrayList<>();
        if (stride < 1) stride = 1;

        int dimX = loader.dimX;
        int dimY = loader.dimY;
        float gs = loader.gridSpacing;

        // Pass 1: find the largest soil depth so the colour ramp spans
        // the actual data range.
        float maxDepth = 0.001f;
        for (int gz = 0; gz < dimY; gz += stride) {
            for (int gx = 0; gx < dimX; gx += stride) {
                float d = loader.soilDepth[gz * dimX + gx];
                if (d > maxDepth) maxDepth = d;
            }
        }

        float spacing = gs * stride;
        float halfWidth = Math.min(spacing * 0.35f, gs * 2f);

        // Pass 2: emit nails into rolling chunk buffers. When a buffer
        // fills, flush it to a Mesh and start a fresh one.
        float[] verts = new float[
            MAX_NAILS_PER_CHUNK * VERTS_PER_NAIL * FLOATS_PER_VERTEX
        ];
        short[] idx = new short[
            MAX_NAILS_PER_CHUNK * INDICES_PER_NAIL
        ];
        int vertFloats   = 0;
        int idxCount     = 0;
        int nailsInChunk = 0;

        for (int gz = 0; gz < dimY; gz += stride) {
            for (int gx = 0; gx < dimX; gx += stride) {
                int i = gz * dimX + gx;
                float soilDepth = loader.soilDepth[i];
                if (soilDepth <= 0.001f) continue;

                if (nailsInChunk == MAX_NAILS_PER_CHUNK) {
                    meshes.add(flushChunk(verts, vertFloats, idx, idxCount));
                    vertFloats   = 0;
                    idxCount     = 0;
                    nailsInChunk = 0;
                }

                float topY = loader.heights[i] + soilDepth;
                float botY = loader.heights[i] - soilDepth;
                float cx   = gx * gs;
                float cz   = gz * gs;
                float col  = SoilPalette.getDepthColor(soilDepth, maxDepth);

                int vBase = nailsInChunk * VERTS_PER_NAIL;

                // ---- Quad 1: spans Z at fixed X. Normal +X. ----
                verts[vertFloats++] = cx;              // 0
                verts[vertFloats++] = topY;
                verts[vertFloats++] = cz - halfWidth;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx;              // 1
                verts[vertFloats++] = topY;
                verts[vertFloats++] = cz + halfWidth;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx;              // 2
                verts[vertFloats++] = botY;
                verts[vertFloats++] = cz + halfWidth;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx;              // 3
                verts[vertFloats++] = botY;
                verts[vertFloats++] = cz - halfWidth;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                // ---- Quad 2: spans X at fixed Z. Normal +Z. ----
                verts[vertFloats++] = cx - halfWidth;  // 4
                verts[vertFloats++] = topY;
                verts[vertFloats++] = cz;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx + halfWidth;  // 5
                verts[vertFloats++] = topY;
                verts[vertFloats++] = cz;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx + halfWidth;  // 6
                verts[vertFloats++] = botY;
                verts[vertFloats++] = cz;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                verts[vertFloats++] = cx - halfWidth;  // 7
                verts[vertFloats++] = botY;
                verts[vertFloats++] = cz;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 1f;
                verts[vertFloats++] = col;
                verts[vertFloats++] = 0f;
                verts[vertFloats++] = 0f;

                // Chunk-local indices — vBase never approaches 32767.
                idx[idxCount++] = (short) (vBase + 0);
                idx[idxCount++] = (short) (vBase + 1);
                idx[idxCount++] = (short) (vBase + 2);
                idx[idxCount++] = (short) (vBase + 0);
                idx[idxCount++] = (short) (vBase + 2);
                idx[idxCount++] = (short) (vBase + 3);

                idx[idxCount++] = (short) (vBase + 4);
                idx[idxCount++] = (short) (vBase + 5);
                idx[idxCount++] = (short) (vBase + 6);
                idx[idxCount++] = (short) (vBase + 4);
                idx[idxCount++] = (short) (vBase + 6);
                idx[idxCount++] = (short) (vBase + 7);

                nailsInChunk++;
            }
        }

        if (nailsInChunk > 0) {
            meshes.add(flushChunk(verts, vertFloats, idx, idxCount));
        }

        return meshes;
    }

    private static Mesh flushChunk(float[] verts, int vertFloats,
                                   short[] idx, int idxCount) {
        float[] v = new float[vertFloats];
        System.arraycopy(verts, 0, v, 0, vertFloats);

        short[] i = new short[idxCount];
        System.arraycopy(idx, 0, i, 0, idxCount);

        Mesh mesh = new Mesh(
            true,
            vertFloats / FLOATS_PER_VERTEX,
            idxCount,
            ATTRS
        );
        mesh.setVertices(v);
        mesh.setIndices(i);
        return mesh;
    }
}

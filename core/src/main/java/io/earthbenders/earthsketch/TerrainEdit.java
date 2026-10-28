package io.earthbenders.earthsketch;

import java.util.HashMap;
import java.util.Map;

/**
 * Records the "before" and "after" state of a set of grid points so the
 * edit can be undone and redone.
 *
 * Each entry is a 5-element float array:
 *     [height, soilDepth, sand, silt, clay]
 *
 * Both PaintBrush (soil only) and TerrainBrush (height + soilDepth)
 * record through this same format. PaintBrush stores the current height
 * unchanged in both before and after, which is harmless - applyMap
 * writes it back but nothing actually moved.
 */
public class TerrainEdit {

    private final Map<Integer, float[]> before = new HashMap<>();
    private final Map<Integer, float[]> after  = new HashMap<>();

    public void recordPoint(int index, float[] beforeValues, float[] afterValues) {
        before.put(index, beforeValues);
        after.put(index, afterValues);
    }

    public void applyBefore(ElvLoader loader) { applyMap(before, loader); }
    public void applyAfter(ElvLoader loader)  { applyMap(after, loader); }

    private void applyMap(Map<Integer, float[]> map, ElvLoader loader) {
        for (Map.Entry<Integer, float[]> entry : map.entrySet()) {
            int index = entry.getKey();
            float[] v = entry.getValue();
            loader.heights[index]   = v[0];
            loader.soilDepth[index] = v[1];
            loader.sand[index]      = v[2];
            loader.silt[index]      = v[3];
            loader.clay[index]      = v[4];
        }
    }

    public boolean isEmpty() {
        return before.isEmpty();
    }
}

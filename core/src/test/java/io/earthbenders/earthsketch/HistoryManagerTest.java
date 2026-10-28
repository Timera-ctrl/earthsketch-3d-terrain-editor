package io.earthbenders.earthsketch;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryManagerTest {

    private ElvLoader loader;
    private HistoryManager manager;

    @Before
    public void setUp() {
        loader = new ElvLoader();
        loader.dimX = 3;
        loader.dimY = 3;
        loader.gridSpacing = 1.0f;
        loader.heights = new float[9];
        loader.soilDepth = new float[9];
        loader.sand = new float[9];
        loader.silt = new float[9];
        loader.clay = new float[9];

        manager = new HistoryManager(5);
    }

    // Helper: builds a TerrainEdit touching one index, varying only soilDepth.
    // Array order: {height, soilDepth, sand, silt, clay}
    private TerrainEdit makeEdit(int index, float height, float beforeDepth, float afterDepth) {
        TerrainEdit edit = new TerrainEdit();
        edit.recordPoint(index,
            new float[]{height, beforeDepth, 0.33f, 0.33f, 0.34f},
            new float[]{height, afterDepth, 0.33f, 0.33f, 0.34f});
        return edit;
    }

    // ===== Test 1 : undo last brush stroke =====

    @Test
    public void testUndo_RevertsLastStroke() {
        loader.soilDepth[4] = 0.8f;

        TerrainEdit edit = makeEdit(4, 0f, 0.0f, 0.8f);
        manager.recordEdit(edit);

        manager.undo(loader);

        assertEquals(0.0f, loader.soilDepth[4], 0.001f);
    }

    @Test
    public void testUndo_WithEmptyHistory_DoesNothing() {
        loader.soilDepth[4] = 0.8f;

        manager.undo(loader); // nothing recorded yet

        assertEquals(0.8f, loader.soilDepth[4], 0.001f);
    }

    @Test
    public void testUndo_DoesNotAffectHeights() {
        loader.heights[4] = 10f;
        loader.soilDepth[4] = 0.8f;

        TerrainEdit edit = makeEdit(4, 10f, 0.0f, 0.8f); // height stored, unchanged in before/after
        manager.recordEdit(edit);

        manager.undo(loader);

        assertEquals(10f, loader.heights[4], 0.001f); // height untouched by a soil-only edit
        assertEquals(0.0f, loader.soilDepth[4], 0.001f);
    }

    // ===== Test 2: multiple level undo history =====

    @Test
    public void testUndo_MultipleLevels() {
        loader.soilDepth[1] = 0.5f;
        manager.recordEdit(makeEdit(1, 0f, 0.0f, 0.5f));

        loader.soilDepth[1] = 1.0f;
        manager.recordEdit(makeEdit(1, 0f, 0.5f, 1.0f));

        loader.soilDepth[1] = 1.5f;
        manager.recordEdit(makeEdit(1, 0f, 1.0f, 1.5f));

        manager.undo(loader);
        assertEquals(1.0f, loader.soilDepth[1], 0.001f);

        manager.undo(loader);
        assertEquals(0.5f, loader.soilDepth[1], 0.001f);

        manager.undo(loader);
        assertEquals(0.0f, loader.soilDepth[1], 0.001f);
    }

    // ===== Test 3 : redo undone stroke =====

    @Test
    public void testRedo_ReappliesUndoneStroke() {
        loader.soilDepth[2] = 0.6f;
        manager.recordEdit(makeEdit(2, 0f, 0.0f, 0.6f));

        manager.undo(loader);
        assertEquals(0.0f, loader.soilDepth[2], 0.001f);

        manager.redo(loader);
        assertEquals(0.6f, loader.soilDepth[2], 0.001f);
    }

    @Test
    public void testRedo_WithEmptyRedoStack_DoesNothing() {
        loader.soilDepth[2] = 0.6f;

        manager.redo(loader); // nothing undone yet

        assertEquals(0.6f, loader.soilDepth[2], 0.001f);
    }

    @Test
    public void testUndoRedo_MultipleLevels() {
        loader.soilDepth[1] = 0.5f;
        manager.recordEdit(makeEdit(1, 0f, 0.0f, 0.5f));

        loader.soilDepth[1] = 1.0f;
        manager.recordEdit(makeEdit(1, 0f, 0.5f, 1.0f));

        manager.undo(loader); // back to 0.5
        manager.undo(loader); // back to 0.0

        manager.redo(loader); // forward to 0.5
        assertEquals(0.5f, loader.soilDepth[1], 0.001f);

        manager.redo(loader); // forward to 1.0
        assertEquals(1.0f, loader.soilDepth[1], 0.001f);
    }

    // ===== Test 4 : clear redo stack when a new edit is made after undo =====

    @Test
    public void testNewEdit_ClearsRedoStack() {
        loader.soilDepth[3] = 0.4f;
        manager.recordEdit(makeEdit(3, 0f, 0.0f, 0.4f));

        manager.undo(loader);
        assertEquals(0.0f, loader.soilDepth[3], 0.001f);

        // A new edit happens instead of redoing.
        loader.soilDepth[3] = 0.9f;
        manager.recordEdit(makeEdit(3, 0f, 0.0f, 0.9f));

        // Redo should now do nothing — the old redo entry was discarded.
        manager.redo(loader);
        assertEquals(0.9f, loader.soilDepth[3], 0.001f); // unchanged by redo
    }

    // ===== Test 5 : sensible memory history limit =====

    @Test
    public void testHistoryLimit_OldestEditsEvicted() {
        // manager was created with a cap of 5. Push 7 sequential edits to the SAME index,
        // each recording a distinct depth, so we can tell exactly which ones survive.
        for (int i = 1; i <= 7; i++) {
            float before = i - 1;
            float after = i;
            loader.soilDepth[0] = after;
            manager.recordEdit(makeEdit(0, 0f, before, after));
        }

        // Undo repeatedly. With a cap of 5, only the last 5 edits (values 7,6,5,4,3)
        // should be undoable, bottoming out at depth=2 (the "before" of the oldest surviving edit),
        // NOT depth=0 (which would only be reached if all 7 were still in history).
        for (int i = 0; i < 5; i++) {
            manager.undo(loader);
        }

        assertEquals(2.0f, loader.soilDepth[0], 0.001f);

        // A 6th undo should do nothing further — the oldest 2 edits were evicted.
        manager.undo(loader);
        assertEquals(2.0f, loader.soilDepth[0], 0.001f); // unchanged
    }
}
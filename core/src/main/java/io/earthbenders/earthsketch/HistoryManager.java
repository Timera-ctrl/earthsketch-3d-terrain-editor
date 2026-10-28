package io.earthbenders.earthsketch;

import java.util.ArrayDeque;
import java.util.Deque;

public class HistoryManager {
    private final Deque<TerrainEdit> undoStack = new ArrayDeque<>();
    private final Deque<TerrainEdit> redoStack = new ArrayDeque<>();
    private final int maxHistorySize;

    public HistoryManager(int maxHistorySize){
        this.maxHistorySize = maxHistorySize;
    }

    public void recordEdit(TerrainEdit edit){
        undoStack.push(edit);
        if(undoStack.size()>maxHistorySize){
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    public void undo(ElvLoader loader){
        if(undoStack.isEmpty()){
            return;
        }
        TerrainEdit edit = undoStack.pop();
        edit.applyBefore(loader);
        redoStack.push(edit);
    }

    public void redo(ElvLoader loader){
        if(redoStack.isEmpty()){
            return;
        }
        TerrainEdit edit = redoStack.pop();
        edit.applyAfter(loader);
        undoStack.push(edit);
    }

    
}

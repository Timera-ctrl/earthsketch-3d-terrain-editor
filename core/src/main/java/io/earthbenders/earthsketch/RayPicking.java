package io.earthbenders.earthsketch;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

public class RayPicking extends InputAdapter {

    public interface PointInspectionListener {
        void onPointInspected(int gridX, int gridZ, float elevation,
                              float depth, float sand, float silt, float clay);
    }
    public interface TerrainChangeListener { void onTerrainChanged(); }
    public interface GeometryChangeListener { void onGeometryChanged(); }
    /**public interface HoverListener {
        void onHover(int gridX, int gridZ, float elevation,
                     float soilDepth, float dugDepth);
        void onHoverExit();
    }*/

    // Bounding boxes for each terrain mesh chunk used as a fast first-stage intersection test.
    private final ArrayList<BoundingBox> bounds = new ArrayList<>();
    private final ArrayList<float[]> cachedVerts = new ArrayList<>();
    private final ArrayList<short[]> cachedIdx   = new ArrayList<>();

    private final Vector3 tmpA      = new Vector3();
    private final Vector3 tmpB      = new Vector3();
    private final Vector3 tmpC      = new Vector3();
    private final Vector3 tmpHit    = new Vector3();
    private final Vector3 tmpRayHit = new Vector3();

    private final PerspectiveCamera camera;
    private final List<Mesh> terrainMeshes;
    private final float gridSpacing;
    private final ElvLoader loader;
    private final HistoryManager manager;

    private PointInspectionListener pointInspectionListener;
    private TerrainChangeListener   terrainChangeListener;
    private GeometryChangeListener  geometryChangeListener;
    //private HoverListener           hoverListener;

    /**
     * Called whenever soil data changes so the caller can refresh the
     * terrain vertex colours using whatever view mode is active. RayPicking
     * itself does not know about ViewMode; it just signals "colours
     * need updating".
     */
    private Runnable refreshTerrainColorsCallback;

    private int mouseDownX, mouseDownY;
    private boolean leftMouseDown = false;
    private static final int CLICK_THRESHOLD = 5;

    private final PaintBrush   paintBrush;
    private final TerrainBrush terrainBrush;

    private boolean paintMode  = false;
    private boolean sculptMode = false;
    private boolean brushSettingsValid = true;

    private Integer lastPaintX = null;
    private Integer lastPaintZ = null;
    private boolean strokeActive = false;

    private float[] baselineHeights;

    public RayPicking(List<Mesh> terrainMeshes, PerspectiveCamera camera,
                      ElvLoader loader, PaintBrush paintBrush,
                      TerrainBrush terrainBrush, HistoryManager manager) {
        this.terrainMeshes = terrainMeshes;
        this.gridSpacing = loader.gridSpacing;
        this.loader = loader;
        this.paintBrush = paintBrush;
        this.terrainBrush = terrainBrush;
        this.camera = camera;
        this.manager = manager;

        rebuildBounds(terrainMeshes);
    }

    public void setPointInspectionListener(PointInspectionListener l) { this.pointInspectionListener = l; }
    public void setTerrainChangeListener(TerrainChangeListener l)     { this.terrainChangeListener = l; }
    public void setGeometryChangeListener(GeometryChangeListener l)   { this.geometryChangeListener = l; }
    // public void setHoverListener(HoverListener l)                     { this.hoverListener = l; }
    public void setBrushSettingsValid(boolean v)                      { this.brushSettingsValid = v; }
    public void setPaintMode(boolean on)                              { this.paintMode = on; }
    public void setSculptMode(boolean on)                             { this.sculptMode = on; }
    public void setBaselineHeights(float[] h)                         { this.baselineHeights = h; }
    public void setRefreshTerrainColorsCallback(Runnable r)           { this.refreshTerrainColorsCallback = r; }

    private void refreshTerrainColors() {
        if (refreshTerrainColorsCallback != null) refreshTerrainColorsCallback.run();
    }

    public void rebuildBounds(List<Mesh> meshes) {
        bounds.clear();
        cachedVerts.clear();
        cachedIdx.clear();

        for (Mesh mesh : meshes) {
            BoundingBox b = new BoundingBox();
            mesh.calculateBoundingBox(b);
            bounds.add(b);

            int fpv = mesh.getVertexSize() / 4;
            int nv  = mesh.getNumVertices();
            float[] v = new float[nv * fpv];
            mesh.getVertices(v);
            cachedVerts.add(v);

            short[] idx = new short[mesh.getNumIndices()];
            mesh.getIndices(idx);
            cachedIdx.add(idx);
        }
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Buttons.LEFT) return false;

        mouseDownX = screenX; mouseDownY = screenY;
        leftMouseDown = true;

        if (sculptMode) {
            Integer tile = pickTile(screenX, screenY);
            if (tile != null) {
                Vector3 hit = intersectTile(tile, screenX, screenY);
                if (hit != null) {
                    terrainBrush.beginStroke();
                    strokeActive = true;

                    int[] gp = getGridPoint(hit);
                    terrainBrush.sculptAt(gp[0], gp[1]);
                    lastPaintX = gp[0]; lastPaintZ = gp[1];
                    // notifyHover(gp[0], gp[1]);
                    notifyGeometryChanged();
                }
            }
            return true;
        }

        if (paintMode) {
            // Always consume the click in Paint mode so it never falls
            // through to the camera controller. If the brush fields are
            // invalid, we simply do not paint but the camera still
            // does not lurch on a misclick.
            if (brushSettingsValid) {
                Integer tile = pickTile(screenX, screenY);
                if (tile != null) {
                    Vector3 hit = intersectTile(tile, screenX, screenY);
                    if (hit != null) {
                        paintBrush.beginStroke();
                        strokeActive = true;

                        int[] gp = getGridPoint(hit);
                        paintPoint(gp[0], gp[1]);
                        lastPaintX = gp[0]; lastPaintZ = gp[1];
                    }
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button != Buttons.LEFT || !leftMouseDown) return false;

        lastPaintX = null; lastPaintZ = null;

        if (paintMode && strokeActive) {
            TerrainEdit edit = paintBrush.endStroke();
            strokeActive = false;
            if (edit != null && !edit.isEmpty()) manager.recordEdit(edit);
        }

        if (sculptMode && strokeActive) {
            TerrainEdit edit = terrainBrush.endStroke();
            strokeActive = false;
            if (edit != null && !edit.isEmpty()) manager.recordEdit(edit);
        }

        if (!paintMode && !sculptMode) {
            int dx = screenX - mouseDownX, dy = screenY - mouseDownY;
            if (Math.sqrt(dx * dx + dy * dy) <= CLICK_THRESHOLD) {
                Integer tile = pickTile(screenX, screenY);
                if (tile != null) {
                    Vector3 hit = intersectTile(tile, screenX, screenY);
                    if (hit != null) inspectPoint(getGridPoint(hit));
                }
            }
        }
        leftMouseDown = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (sculptMode && leftMouseDown) {
            Integer tile = pickTile(screenX, screenY);
            if (tile != null) {
                Vector3 hit = intersectTile(tile, screenX, screenY);
                if (hit != null) {
                    int[] gp = getGridPoint(hit);
                    if (lastPaintX != null && lastPaintZ != null) {
                        terrainBrush.stroke(lastPaintX, lastPaintZ, gp[0], gp[1]);
                    } else {
                        terrainBrush.sculptAt(gp[0], gp[1]);
                    }
                    lastPaintX = gp[0]; lastPaintZ = gp[1];
                    // notifyHover(gp[0], gp[1]);
                    notifyGeometryChanged();
                }
            }
            return true;
        }

        if (paintMode && leftMouseDown && brushSettingsValid) {
            Integer tile = pickTile(screenX, screenY);
            if (tile != null) {
                Vector3 hit = intersectTile(tile, screenX, screenY);
                if (hit != null) {
                    int[] gp = getGridPoint(hit);
                    if (lastPaintX != null && lastPaintZ != null) {
                        paintBrush.paintStroke(lastPaintX, lastPaintZ, gp[0], gp[1]);
                        if (terrainChangeListener != null) terrainChangeListener.onTerrainChanged();
                    } else {
                        paintPoint(gp[0], gp[1]);
                    }
                    lastPaintX = gp[0]; lastPaintZ = gp[1];
                    refreshTerrainColors();
                }
            }
            return true;
        }

        return false;
    }

    /** 
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (hoverListener == null || leftMouseDown) return false;

        Integer tile = pickTile(screenX, screenY);
        if (tile == null) { hoverListener.onHoverExit(); return false; }

        Vector3 hit = intersectTile(tile, screenX, screenY);
        if (hit == null) { hoverListener.onHoverExit(); return false; }

        int[] gp = getGridPoint(hit);
        notifyHover(gp[0], gp[1]);
        return false;
    }*/

    @Override
    public boolean keyDown(int keycode) {
        boolean ctrlHeld = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SYM);

        if (ctrlHeld && keycode == Input.Keys.Z) {
            manager.undo(loader);
            notifyGeometryChanged();
            refreshTerrainColors();
            return true;
        }
        if (ctrlHeld && keycode == Input.Keys.Y) {
            manager.redo(loader);
            notifyGeometryChanged();
            refreshTerrainColors();
            return true;
        }
        return false;
    }

    /**
    private void notifyHover(int gridX, int gridZ) {
        if (hoverListener == null) return;
        int idx = gridZ * loader.dimX + gridX;
        float elevation = loader.heights[idx];
        float soilDepth = loader.soilDepth[idx];
        float dug = (baselineHeights != null) ? (baselineHeights[idx] - elevation) : 0f;
        hoverListener.onHover(gridX, gridZ, elevation, soilDepth, dug);
    } */

    private Integer pickTile(int screenX, int screenY) {
        Ray ray = camera.getPickRay(screenX, screenY);
        float closest = Float.POSITIVE_INFINITY;
        Integer target = null;
        for (int i = 0; i < bounds.size(); i++) {
            if (Intersector.intersectRayBounds(ray, bounds.get(i), tmpRayHit)) {
                float d = ray.origin.dst(tmpRayHit);
                if (d < closest) { closest = d; target = i; }
            }
        }
        return target;
    }

    private Vector3 intersectTile(int tile, int screenX, int screenY) {
        Ray ray = camera.getPickRay(screenX, screenY);
        Mesh mesh = terrainMeshes.get(tile);
        int fpv = mesh.getVertexSize() / 4;
        float[] verts = cachedVerts.get(tile);
        short[] idx   = cachedIdx.get(tile);

        float closest = Float.POSITIVE_INFINITY;
        boolean found = false;

        for (int i = 0; i < idx.length - 2; i += 3) {
            int a = idx[i]     * fpv;
            int b = idx[i + 1] * fpv;
            int c = idx[i + 2] * fpv;
            tmpA.set(verts[a], verts[a+1], verts[a+2]);
            tmpB.set(verts[b], verts[b+1], verts[b+2]);
            tmpC.set(verts[c], verts[c+1], verts[c+2]);
            if (Intersector.intersectRayTriangle(ray, tmpA, tmpB, tmpC, tmpHit)) {
                float d = ray.origin.dst(tmpHit);
                if (d < closest) { closest = d; found = true; }
            }
        }
        if (!found) return null;

        for (int i = 0; i < idx.length - 2; i += 3) {
            int a = idx[i]     * fpv;
            int b = idx[i + 1] * fpv;
            int c = idx[i + 2] * fpv;
            tmpA.set(verts[a], verts[a+1], verts[a+2]);
            tmpB.set(verts[b], verts[b+1], verts[b+2]);
            tmpC.set(verts[c], verts[c+1], verts[c+2]);
            if (Intersector.intersectRayTriangle(ray, tmpA, tmpB, tmpC, tmpHit)) {
                if (Math.abs(ray.origin.dst(tmpHit) - closest) < 1e-4f) {
                    return new Vector3(tmpHit);
                }
            }
        }
        return null;
    }

    private int[] getGridPoint(Vector3 p) {
        int gx = Math.round(p.x / gridSpacing);
        int gz = Math.round(p.z / gridSpacing);
        gx = Math.max(0, Math.min(loader.dimX - 1, gx));
        gz = Math.max(0, Math.min(loader.dimY - 1, gz));
        return new int[]{gx, gz};
    }

    private void inspectPoint(int[] gp) {
        int idx = gp[1] * loader.dimX + gp[0];
        if (pointInspectionListener != null) {
            pointInspectionListener.onPointInspected(
                gp[0], gp[1],
                loader.heights[idx], loader.soilDepth[idx],
                loader.sand[idx], loader.silt[idx], loader.clay[idx]);
        }
    }

    private void paintPoint(int gx, int gz) {
        paintBrush.paintAt(gx, gz);
        refreshTerrainColors();
        if (terrainChangeListener != null) terrainChangeListener.onTerrainChanged();
    }

    private void notifyGeometryChanged() {
        if (geometryChangeListener != null) geometryChangeListener.onGeometryChanged();
    }
}

package io.earthbenders.earthsketch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Performance test class, stress tests painting with a repeatable harness
 * 
 * Simulates painting progmmatically, writing frame times to a separate file
 * (There already is a fps logger part of GDX but it doesn't measure to a granular enough level)
 * DebugPerformanceLogger
 */
public class DebugPerformanceLogger {
    private final List<String> rows = new ArrayList<>();

    public void logFrame(int dimX, int radius, boolean blend, boolean colorUpdate, float deltaTime){
        float fps = deltaTime > 0f ? 1f / deltaTime : 0f;
        rows.add(dimX + "," + radius + "," + blend + "," + colorUpdate + "," + deltaTime + "," + fps);
    }

    public void writeToFile(String path) throws IOException{
        boolean fileExists = new File(path).exists();
        try(BufferedWriter wr = new BufferedWriter(new FileWriter(path, true))){
            if(!fileExists){
                wr.write("dimX,radius,blend,colorUpdate,deltaTime");
                wr.newLine();
            }
            for(String row: rows){
                wr.write(row);
                wr.newLine();
            }
        }
        rows.clear();
    }
    
}

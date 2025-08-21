package dev.sortingdaemon.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class SDConfig {
    // Shared Gson instance with pretty-printing
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "sortingdaemon.json";

    // Singleton instance
    private static SDConfig INSTANCE;

    // Drag-to-drop feature settings
    public DragToDrop dragToDrop = new DragToDrop();

    public static class DragToDrop {
        public boolean enabled = true;        // Feature enabled
        public boolean requireShift = true;   // Require Shift modifier
        public int mouseButton = 0;           // 0 = left mouse button
    }

    // Quick deposit feature settings
    public static class QuickDeposit {
        public boolean enabled = true;
        public boolean includeHotbar = true;
        public boolean includeMain = true;      // Main 27 slots
        public boolean includeOffhand = false;
        public boolean includeArmor = false;
        public int defaultKey = 75;             // GLFW.GLFW_KEY_K
    }
    
    public QuickDeposit quickDeposit = new QuickDeposit();

    // Returns the global config instance, loading if necessary
    public static SDConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    // Loads config from file or creates default if missing
    private static SDConfig load() {
        try {
            Path cfgDir = FabricLoader.getInstance().getConfigDir();
            Path file = cfgDir.resolve(FILE_NAME);
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return GSON.fromJson(r, SDConfig.class);
                }
            }
            SDConfig def = new SDConfig();
            save(def);
            return def;
        } catch (Exception e) {
            e.printStackTrace();
            return new SDConfig();  // Fallback to defaults
        }
    }

    // Saves config to file
    public static void save(SDConfig cfg) {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

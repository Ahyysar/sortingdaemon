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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "sortingdaemon.json";
    private static SDConfig INSTANCE;

    public DragToDrop dragToDrop = new DragToDrop();

    public static class DragToDrop {
        public boolean enabled = true;        // фича включена
        public boolean requireShift = true;   // требовать Shift
        public int mouseButton = 0;           // 0 = ЛКМ (на будущее)
    }

    public static SDConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

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
            return new SDConfig(); // дефолт
        }
    }

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

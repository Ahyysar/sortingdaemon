package dev.sortingdaemon.fav;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

public final class FavoriteSlots {
    private static final String FILE = "sortingdaemon_favorites.json";
    // player inventory слоты 9..35 (27 штук) — отмечаем относительно игрока
    // Будем хранить BitSet размером 41 на всякий — индексы vanilla: 0..40
    private static final BitSet FAVORITES = new BitSet(41);

    private FavoriteSlots() {}

    public static boolean isFavorite(int playerInvIndex) {
        if (playerInvIndex < 0 || playerInvIndex > 40) return false;
        return FAVORITES.get(playerInvIndex);
    }

    public static boolean toggleFavorite(int playerInvIndex) {
        if (playerInvIndex < 9 || playerInvIndex > 35) return false; // разрешаем ТОЛЬКО 9..35
        FAVORITES.flip(playerInvIndex);
        save();
        return true;
    }

    public static void load() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
            if (!Files.exists(p)) return;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                BitSet bs = fromJson(o.getAsJsonArray("favorites"));
                FAVORITES.clear();
                FAVORITES.or(bs);
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
            JsonObject o = new JsonObject();
            o.add("favorites", toJson(FAVORITES));
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(o, w);
            }
        } catch (Exception ignored) {}
    }

    private static JsonArray toJson(BitSet bs) {
        JsonArray arr = new JsonArray();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) arr.add(i);
        return arr;
    }

    private static BitSet fromJson(JsonArray arr) {
        BitSet bs = new BitSet(41);
        for (var el : arr) bs.set(el.getAsInt());
        return bs;
    }
}

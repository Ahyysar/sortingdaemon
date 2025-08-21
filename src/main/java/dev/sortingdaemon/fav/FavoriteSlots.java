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
    // Config filename in the Fabric config directory
    private static final String FILE = "sortingdaemon_favorites.json";

    // Tracks favorite player-inventory slots using vanilla indices 0..40
    // Player inventory range: 9..35 (27 slots)
    private static final BitSet FAVORITES = new BitSet(41);

    private FavoriteSlots() {}

    // Returns true if the slot index is marked as favorite
    public static boolean isFavorite(int playerInvIndex) {
        if (playerInvIndex < 0 || playerInvIndex > 40) return false;
        return FAVORITES.get(playerInvIndex);
    }

    // Toggles favorite state for allowed player inventory slots (9..35)
    public static boolean toggleFavorite(int playerInvIndex) {
        if (playerInvIndex < 9 || playerInvIndex > 35) return false;
        FAVORITES.flip(playerInvIndex);
        save();
        return true;
    }

    // Loads favorites from JSON; ignores errors to avoid blocking startup
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

    // Saves favorites to JSON; best-effort with silent failure
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

    // Serializes set bits as an array of slot indices
    private static JsonArray toJson(BitSet bs) {
        JsonArray arr = new JsonArray();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) arr.add(i);
        return arr;
    }

    // Deserializes an array of slot indices into a BitSet
    private static BitSet fromJson(JsonArray arr) {
        BitSet bs = new BitSet(41);
        for (var el : arr) bs.set(el.getAsInt());
        return bs;
    }
}

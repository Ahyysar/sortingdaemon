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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

/**
 * Manages favorite slots in the player inventory.
 * Favorites are stored as a BitSet and persisted in a JSON file.
 */

public final class FavoriteSlots {

    // Checks if a given slot is marked as favorite in the player's main inventory
    public static boolean isPlayerInventoryFavorite(Slot slot, PlayerEntity player) {
        if (slot == null || player == null) return false;
        if (slot.inventory != player.getInventory()) return false;

        int idx     = slot.getIndex();                  // or slot.id in some mappings
        int hotbar  = PlayerInventory.getHotbarSize();  // usually 9
        int total   = player.getInventory().size();     // usually 36
        int mainCnt = total - hotbar;                   // usually 27

        boolean inMain = idx >= hotbar && idx < hotbar + mainCnt;
        return inMain && isFavorite(idx);
    }

    // Checks if a given slot belongs to the player's main inventory (excluding hotbar/armor/offhand)
    public static boolean isPlayerInventoryMainSlot(Slot slot, PlayerEntity player) {
        if (slot == null || player == null) return false;
        if (slot.inventory != player.getInventory()) return false;

        int idx    = slot.getIndex();                   // or slot.id in some mappings
        int hotbar = PlayerInventory.getHotbarSize();   // usually 9
        int total  = player.getInventory().size();      // usually 36
        int mainCnt = total - hotbar;                   // usually 27

        return idx >= hotbar && idx < hotbar + mainCnt;
    }


    // Config filename in the Fabric config directory
    private static final String FILE = "sortingdaemon_favorites.json";

    // Stores favorite state for vanilla indices 0..40 (27 usable slots in 9..35)
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

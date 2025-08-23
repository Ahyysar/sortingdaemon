package dev.sortingdaemon.sort;

import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves slot indices to be affected by sorting.
 * - Player inventory (PlayerScreenHandler): main inventory only (9..35), excludes hotbar (36..44),
 *   armor (5..8), offhand (45), and crafting grid (0..4).
 * - Other containers: container slots only (0..containerCount-1), excludes the 36 player slots.
 */
public final class InventoryRangeResolver {

    private InventoryRangeResolver() {}

    // Returns a list of slot indices eligible for sorting for the given screen handler
    public static List<Integer> resolveSlotIndices(ScreenHandler sh) {
        int total = sh.slots.size();

        // Creative: restrict to player main inventory (9..35) and filter by PlayerInventory owner
        if (sh.getClass().getName().contains("Creative")) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 9; i <= 35; i++) {
                var slot = sh.slots.get(i);
                if (slot.inventory instanceof net.minecraft.entity.player.PlayerInventory) {
                    ids.add(i);
                }
            }
            return ids;
        }

        // Player inventory: include main inventory 9..35
        if (sh instanceof PlayerScreenHandler) {
            List<Integer> ids = new ArrayList<>(27);
            for (int i = 9; i <= 35; i++) ids.add(i);
            return ids;
        }

        // Generic container: include only its own slots before the 36 player slots
        int containerCount = Math.max(0, total - 36);
        List<Integer> ids = new ArrayList<>(containerCount);
        for (int i = 0; i < containerCount; i++) ids.add(i);
        return ids;
    }
}

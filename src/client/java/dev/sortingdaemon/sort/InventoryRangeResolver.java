package dev.sortingdaemon.sort;

import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Возвращает список индексов слотов для сортировки.
 * - Инвентарь игрока (PlayerScreenHandler): только main 9..35 (27 слотов), без хотбара 36..44,
 *   без брони 5..8, оффхенда 45 и крафта 0..4.
 * - Любой контейнер: только его собственные слоты (0..containerCount-1), без 36 слотов игрока.
 */
public final class InventoryRangeResolver {

    private InventoryRangeResolver() {}

    public static List<Integer> resolveSlotIndices(ScreenHandler sh) {
        int total = sh.slots.size();

        // Инвентарь игрока
        if (sh instanceof PlayerScreenHandler) {
            List<Integer> ids = new ArrayList<>(27);
            // main inventory 9..35 (включительно)
            for (int i = 9; i <= 35; i++) ids.add(i);
            return ids;
        }

        // Контейнер: [0..containerCount-1], потом идут 36 слотов игрока (которые не трогаем)
        int containerCount = Math.max(0, total - 36);
        List<Integer> ids = new ArrayList<>(containerCount);
        for (int i = 0; i < containerCount; i++) ids.add(i);
        return ids;
    }
}

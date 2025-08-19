package dev.sortingdaemon.sort;

import net.minecraft.screen.ScreenHandler;

/**
 * Определяет какие слоты текущего GUI мы сортируем.
 * Эвристика: если в ScreenHandler есть внешние слоты (сундук и т.п.),
 * сортируем их (0..playerInvStart-1). Иначе — инвентарь игрока (последние 36).
 */
public class InventoryRangeResolver {
    public record Range(int startInclusive, int endExclusive) {}

    public static Range resolve(ScreenHandler sh) {
        int total = sh.slots.size();
        // 27 "рюкзак" + 9 хотбар = 36 слотов игрока
        int playerInvStart = Math.max(0, total - 36);

        // Если слоты до инвентаря игрока существуют — это контейнер (сундук и т.п.)
        if (playerInvStart > 0) return new Range(0, playerInvStart);

        // Иначе открыт чисто инвентарь игрока — сортируем его
        return new Range(playerInvStart, total);
    }
}

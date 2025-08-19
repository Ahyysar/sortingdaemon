package dev.sortingdaemon.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

/**
 * Полный проход за одно нажатие:
 *  1) Слить одинаковые стаки (до maxCount).
 *  2) Сдвинуть пустоты в конец (уплотнить).
 *  3) Отсортировать оставшиеся стаки по детерминированному компаратору.
 *
 * Работает и для нестакуемых предметов (count=1): они участвуют в свапах и сортировке.
 */
public class Sorter {

    public static void sortCurrent(MinecraftClient client, ScreenHandler sh, List<Integer> slotIds) {
        if (client == null || client.player == null || client.interactionManager == null) return;
        if (slotIds == null || slotIds.size() <= 1) return;

        // 1) Слить одинаковые стаки (merge pass)
        mergeStacks(client, sh, slotIds);

        // 2) Сдвинуть пустоты к концу
        compactNonEmptyToFront(client, sh, slotIds);

        // 3) Отсортировать
        sortByComparator(client, sh, slotIds, STACK_COMPARATOR);
    }

    // ---------- PASS 1: MERGE ----------
    private static void mergeStacks(MinecraftClient client, ScreenHandler sh, List<Integer> ids) {
        var slots = sh.slots;
        for (int iIdx = 0; iIdx < ids.size(); iIdx++) {
            int i = ids.get(iIdx);
            ItemStack dst = slots.get(i).getStack();
            if (dst.isEmpty()) continue;

            // Если стак уже полный — пропускаем
            int max = dst.getMaxCount();
            if (dst.getCount() >= max) continue;

            // Ищем источники j > i для слива в i
            for (int jIdx = iIdx + 1; jIdx < ids.size(); jIdx++) {
                int j = ids.get(jIdx);
                ItemStack src = slots.get(j).getStack();
                if (src.isEmpty()) continue;

                if (canStackTogether(dst, src)) {
                    // Переливаем src -> dst. В ваниле это: PICKUP(src), PICKUP(dst), PICKUP(src) (если остались).
                    clickPickup(client, sh.syncId, j);
                    clickPickup(client, sh.syncId, i);
                    if (!cursorEmpty(client)) clickPickup(client, sh.syncId, j);

                    // Обновим ссылки после кликов
                    dst = slots.get(i).getStack();
                    if (dst.getCount() >= dst.getMaxCount()) break; // стак заполнен — дальше смысла нет
                }
            }
        }
    }

    // ---------- PASS 2: COMPACT ----------
    private static void compactNonEmptyToFront(MinecraftClient client, ScreenHandler sh, List<Integer> ids) {
        var slots = sh.slots;
        int write = 0; // позиция, куда хотим поставить следующий непустой
        for (int readIdx = 0; readIdx < ids.size(); readIdx++) {
            int read = ids.get(readIdx);
            if (!slots.get(read).getStack().isEmpty()) {
                int target = ids.get(write);
                if (read != target) {
                    // свапнуть непустой read на позицию target
                    swapSlots(client, sh.syncId, read, target);
                }
                write++;
            }
        }
        // всё что после write — пустоты, и они уже в конце
    }

    // ---------- PASS 3: SORT ----------
    private static void sortByComparator(MinecraftClient client, ScreenHandler sh, List<Integer> ids, Comparator<ItemStack> cmp) {
        var slots = sh.slots;

        // найдём границу непустых (после compact все пустые уже в конце)
        int nonEmptyCount = 0;
        for (int idx = 0; idx < ids.size(); idx++) {
            if (slots.get(ids.get(idx)).getStack().isEmpty()) break;
            nonEmptyCount++;
        }
        if (nonEmptyCount <= 1) return;

        // Selection sort по непустой части (O(n^2), но n <= 54 — норм)
        for (int pos = 0; pos < nonEmptyCount - 1; pos++) {
            int best = pos;
            ItemStack bestSt = slots.get(ids.get(best)).getStack();
            for (int j = pos + 1; j < nonEmptyCount; j++) {
                ItemStack st = slots.get(ids.get(j)).getStack();
                if (cmp.compare(st, bestSt) < 0) {
                    best = j;
                    bestSt = st;
                }
            }
            if (best != pos) {
                int a = ids.get(pos);
                int b = ids.get(best);
                swapSlots(client, sh.syncId, b, a);
            }
        }
    }

    // ---------- CORE CLICK UTILS ----------
    private static void clickPickup(MinecraftClient c, int syncId, int slotId) {
        c.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, c.player);
    }

    private static void swapSlots(MinecraftClient c, int syncId, int src, int dst) {
        if (src == dst) return;
        clickPickup(c, syncId, src);
        clickPickup(c, syncId, dst);
        if (!cursorEmpty(c)) clickPickup(c, syncId, src); // вернуть остаток если что-то осталось на курсоре
    }

    private static boolean cursorEmpty(MinecraftClient c) {
        return c.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    // ---------- EQUALITY / COMBINE ----------
    private static boolean canStackTogether(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!a.isStackable() || !b.isStackable()) return false;
        if (a.getItem() != b.getItem()) return false;
        // В 1.21.x совпадение компонент/данных
        return Objects.equals(a.getComponents(), b.getComponents());
    }

    // ---------- COMPARATOR ----------
    private static final Comparator<ItemStack> STACK_COMPARATOR = (a, b) -> {
        // пустые всегда в конец
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        // 1) По ID предмета (детерминированно)
        int c = a.getItem().toString().compareTo(b.getItem().toString());
        if (c != 0) return c;

        // 2) По компонентам/данным (чтобы разные варианты не смешивались)
        c = String.valueOf(a.getComponents()).compareTo(String.valueOf(b.getComponents()));
        if (c != 0) return c;

        // 3) Для ломаемых — по износу (меньше damage -> лучше)
        c = Integer.compare(a.getDamage(), b.getDamage());
        if (c != 0) return c;

        // 4) По количеству (больше вперёд)
        return Integer.compare(b.getCount(), a.getCount());
    };
}

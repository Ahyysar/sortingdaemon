package dev.sortingdaemon.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class Sorter {

    public static void sortCurrent(MinecraftClient client, ScreenHandler sh, List<Integer> slotIds) {
        if (slotIds == null || slotIds.size() <= 1) return;

        var slots = sh.slots;

        // Текущие стаки (копии) из выбранных слотов
        List<ItemStack> current = new ArrayList<>(slotIds.size());
        for (int id : slotIds) current.add(slots.get(id).getStack().copy());

        // Упаковали и отсортировали
        List<ItemStack> packed = StackPacker.pack(current);
        packed.sort(Comparator
                .comparing((ItemStack st) -> st.isEmpty() ? "" : st.getItem().toString())
                .thenComparing(st -> st.isEmpty() ? "" : st.getName().getString())
                .thenComparingInt(ItemStack::getDamage));

        // добиваем пустыми до исходного размера
        while (packed.size() < current.size()) packed.add(ItemStack.EMPTY);

        int syncId = sh.syncId;

        // Применяем раскладку через «клики»
        for (int i = 0; i < slotIds.size(); i++) {
            ItemStack want = packed.get(i);
            ItemStack has  = current.get(i);
            if (equalEnough(has, want)) continue;

            int src = findSource(current, want, i);
            if (src < 0) continue;

            int srcSlot = slotIds.get(src);
            int dstSlot = slotIds.get(i);

            clickPickup(client, syncId, srcSlot);
            clickPickup(client, syncId, dstSlot);
            if (!cursorEmpty(client)) clickPickup(client, syncId, srcSlot);

            // локально обновляем картинку
            ItemStack tmp = current.get(src);
            current.set(src, current.get(i));
            current.set(i, tmp);
        }

        // Сброс курсора, если что-то осталось
        if (!cursorEmpty(client)) {
            for (int slot : slotIds) {
                clickPickup(client, syncId, slot);
                if (cursorEmpty(client)) break;
            }
        }
    }

    // ----- helpers -----

    private static boolean canStackTogether(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!a.isStackable() || !b.isStackable()) return false;
        if (a.getItem() != b.getItem()) return false;
        return Objects.equals(a.getComponents(), b.getComponents());
    }

    private static boolean equalEnough(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return canStackTogether(a, b) && a.getCount() == b.getCount();
    }

    private static int findSource(List<ItemStack> cur, ItemStack want, int avoidIdx) {
        for (int i = 0; i < cur.size(); i++) {
            if (i == avoidIdx) continue;
            var st = cur.get(i);
            if (!st.isEmpty() && canStackTogether(st, want)) return i;
        }
        return -1;
    }

    private static void clickPickup(MinecraftClient c, int syncId, int slotId) {
        c.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, c.player);
    }

    private static boolean cursorEmpty(MinecraftClient c) {
        return c.player.currentScreenHandler.getCursorStack().isEmpty();
    }
}

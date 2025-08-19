package dev.sortingdaemon.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class Sorter {
    public static void sortCurrent(MinecraftClient client, ScreenHandler sh, InventoryRangeResolver.Range range) {
        var slots = sh.slots;

        List<Integer> ids = new ArrayList<>();
        for (int i = range.startInclusive(); i < range.endExclusive(); i++) ids.add(i);
        if (ids.size() <= 1) return;

        List<ItemStack> current = new ArrayList<>(ids.size());
        for (int id : ids) current.add(slots.get(id).getStack().copy());

        // Упаковка и сортировка
        List<ItemStack> packed = StackPacker.pack(current);
        packed.sort(Comparator
                .comparing((ItemStack st) -> st.isEmpty() ? "" : st.getItem().toString())
                .thenComparing(st -> st.isEmpty() ? "" : st.getName().getString())
                .thenComparingInt(ItemStack::getDamage));

        while (packed.size() < current.size()) packed.add(ItemStack.EMPTY);

        int syncId = sh.syncId;
        for (int i = 0; i < ids.size(); i++) {
            ItemStack want = packed.get(i);
            ItemStack has  = current.get(i);
            if (equalEnough(has, want)) continue;

            int src = findSource(current, want, i);
            if (src < 0) continue;

            int srcSlot = ids.get(src);
            int dstSlot = ids.get(i);

            clickPickup(client, syncId, srcSlot);
            clickPickup(client, syncId, dstSlot);
            if (!cursorEmpty(client)) clickPickup(client, syncId, srcSlot);

            ItemStack tmp = current.get(src);
            current.set(src, current.get(i));
            current.set(i, tmp);
        }

        if (!cursorEmpty(client)) {
            for (int slot : ids) {
                clickPickup(client, syncId, slot);
                if (cursorEmpty(client)) break;
            }
        }
    }

    // ----- helpers -----

    // Аналог canCombine для 1.21.8
    private static boolean canStackTogether(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!a.isStackable() || !b.isStackable()) return false;
        if (a.getItem() != b.getItem()) return false;
        // Данные/компоненты (NBT/enchants/именованные и т.п.) должны совпадать
        // В 1.21.x у стаков есть data components map:
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

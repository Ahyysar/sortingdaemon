package dev.sortingdaemon.sort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * Merges compatible stacks (same item + same components/NBT) up to maxCount.
 * Produces a compact list without empty slots.
 */
public class StackPacker {
    // Packs a list of stacks into the smallest possible set of merged stacks
    public static List<ItemStack> pack(List<ItemStack> src) {
        Map<String, ItemStack> buckets = new LinkedHashMap<>();

        for (ItemStack st : src) {
            if (st.isEmpty()) continue;

            // Key includes item type and components to preserve differences like enchantments/names
            String key = st.getItem().toString() + "|" + st.getComponents().toString();

            ItemStack acc = buckets.get(key);
            if (acc == null) {
                // First occurrence: add copy as new bucket
                buckets.put(key, st.copy());
                continue;
            }

            // Try to merge into existing bucket
            int room = acc.getMaxCount() - acc.getCount();
            int move = Math.min(room, st.getCount());
            if (move > 0) acc.increment(move);

            // If leftover remains, create a separate bucket with unique key
            int left = st.getCount() - move;
            if (left > 0) {
                ItemStack extra = st.copy();
                extra.setCount(left);
                buckets.put(key + "#" + UUID.randomUUID(), extra);
            }
        }

        return new ArrayList<>(buckets.values());
    }
}

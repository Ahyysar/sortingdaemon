package dev.sortingdaemon.sort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * Сливает совместимые стаки (Item + компоненты/NBT) до maxCount.
 * Возвращает компактный список без пустых ячеек.
 */
public class StackPacker {
    public static List<ItemStack> pack(List<ItemStack> src) {
        Map<String, ItemStack> buckets = new LinkedHashMap<>();

        for (ItemStack st : src) {
            if (st.isEmpty()) continue;

            // Ключ: предмет + компоненты (NBT), чтобы не смешивать разные зачары/имена и т.п.
            String key = st.getItem().toString() + "|" + st.getComponents().toString();

            ItemStack acc = buckets.get(key);
            if (acc == null) {
                buckets.put(key, st.copy());
                continue;
            }

            int room = acc.getMaxCount() - acc.getCount();
            int move = Math.min(room, st.getCount());
            if (move > 0) acc.increment(move);

            int left = st.getCount() - move;
            if (left > 0) {
                ItemStack extra = st.copy();
                extra.setCount(left);
                // создаём отдельную «корзину» для остатка
                buckets.put(key + "#" + UUID.randomUUID(), extra);
            }
        }

        return new ArrayList<>(buckets.values());
    }
}

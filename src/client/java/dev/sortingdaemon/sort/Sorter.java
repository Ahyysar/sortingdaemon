package dev.sortingdaemon.sort;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import net.minecraft.item.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.component.DataComponentTypes;



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
    
    // ----- CATEGORIES -----
    private enum Cat {
        TOOLS,          // кирки, топоры, лопаты, мотыги, ножницы, удочки, кремень/сталь
        WEAPONS,        // мечи, трезубец
        RANGED,         // лук, арбалет, стрелы
        ARMOR,          // шлем/нагрудник/штаны/боты, щит, элитры
        POTIONS,        // зелья (обычные/взрывные/туманные), подозрительная рагу
        FOOD,           // съедобное (яблоки, мясо, хлеб, медовая бутылка и т.п.)
        REDSTONE,       // редстоун‑штуки (поршни, повторители, наблюдатели, фонари, рычаги, кнопки, раздатчики, хопперы и т.п.)
        UTILITY,        // ведра, компасы, часы, карты, подзорные трубы, поводки, ярлыки
        BLOCKS,         // любые BlockItem (кроме явных редстоун‑устройств выше)
        MATERIALS,      // руды/слитки/самоцветы/красители и т.п. (остаточная «материалы»)
        MISC            // всё остальное
    }

    private static Cat categoryOf(ItemStack st) {
        if (st.isEmpty()) return Cat.MISC;
        Item it = st.getItem();

        // --- TOOLS (топоры/кирки/лопаты/мотыги по тегам) ---
        if (st.isIn(ItemTags.PICKAXES) || st.isIn(ItemTags.AXES)
                || st.isIn(ItemTags.SHOVELS) || st.isIn(ItemTags.HOES)) {
            return Cat.TOOLS;
        }
        // ножницы/удочка/кремень-и-сталь как утилитарные инструменты
        if (it == Items.SHEARS || it == Items.FISHING_ROD || it == Items.FLINT_AND_STEEL) return Cat.TOOLS;

        // --- WEAPONS ---
        if (st.isIn(ItemTags.SWORDS) || it == Items.TRIDENT) return Cat.WEAPONS;

        // --- RANGED ---
        // «луковые» по тэгам зачарований + сами стрелы
        if (st.isIn(ItemTags.BOW_ENCHANTABLE) || st.isIn(ItemTags.CROSSBOW_ENCHANTABLE)
                || it == Items.ARROW || it == Items.TIPPED_ARROW || it == Items.SPECTRAL_ARROW) {
            return Cat.RANGED;
        }

        // --- ARMOR ---
        // броня и щит/элитры — явные предметы
        if (st.isIn(ItemTags.ARMOR_ENCHANTABLE) || it == Items.SHIELD || it == Items.ELYTRA) return Cat.ARMOR;

        // --- POTIONS ---
        if (it == Items.POTION || it == Items.SPLASH_POTION || it == Items.LINGERING_POTION || it == Items.SUSPICIOUS_STEW) {
            return Cat.POTIONS;
        }

        // --- FOOD --- (через data component FOOD)
        if (st.getComponents().contains(DataComponentTypes.FOOD) || it == Items.HONEY_BOTTLE || it == Items.MILK_BUCKET) {
            return Cat.FOOD;
        }

        // --- REDSTONE --- (узнаём конкретные блоки редстоуна)
        if (it instanceof BlockItem bi) {
            Block b = bi.getBlock();
            if (b == Blocks.REDSTONE_WIRE || b == Blocks.REDSTONE_TORCH || b == Blocks.REPEATER || b == Blocks.COMPARATOR
                    || b == Blocks.OBSERVER || b == Blocks.PISTON || b == Blocks.STICKY_PISTON
                    || b == Blocks.DISPENSER || b == Blocks.DROPPER || b == Blocks.HOPPER
                    || b == Blocks.NOTE_BLOCK || b == Blocks.DAYLIGHT_DETECTOR
                    || b == Blocks.LEVER || b == Blocks.STONE_BUTTON || b == Blocks.OAK_BUTTON
                    || b == Blocks.STONE_PRESSURE_PLATE || b == Blocks.OAK_PRESSURE_PLATE
                    || b == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE || b == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
                    || b == Blocks.RAIL || b == Blocks.POWERED_RAIL || b == Blocks.DETECTOR_RAIL || b == Blocks.ACTIVATOR_RAIL) {
                return Cat.REDSTONE;
            }
        }

        // --- UTILITY ---
        if (it == Items.BUCKET || it == Items.WATER_BUCKET || it == Items.LAVA_BUCKET || it == Items.POWDER_SNOW_BUCKET) return Cat.UTILITY;
        if (it == Items.COMPASS || it == Items.RECOVERY_COMPASS || it == Items.CLOCK || it == Items.FILLED_MAP || it == Items.SPYGLASS) return Cat.UTILITY;
        if (it == Items.LEAD || it == Items.NAME_TAG) return Cat.UTILITY;

        // --- BLOCKS ---
        if (it instanceof BlockItem) return Cat.BLOCKS;

        // --- MATERIALS / MISC ---
        if (!st.isStackable()) return Cat.MISC;  // нестакуемые, не попавшие выше
        return Cat.MATERIALS;                    // остальное стакуемое — материалы
    }



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

        // 0) Сначала категория
        int c = Integer.compare(categoryOf(a).ordinal(), categoryOf(b).ordinal());
        if (c != 0) return c;

        // 1) По ID предмета
        c = a.getItem().toString().compareTo(b.getItem().toString());
        if (c != 0) return c;

        // 2) По компонентам/данным (NBT/enchants/имя)
        c = String.valueOf(a.getComponents()).compareTo(String.valueOf(b.getComponents()));
        if (c != 0) return c;

        // 3) По износу (меньше damage -> раньше)
        c = Integer.compare(a.getDamage(), b.getDamage());
        if (c != 0) return c;

        // 4) По количеству (больше вперёд)
        return Integer.compare(b.getCount(), a.getCount());
    };

}

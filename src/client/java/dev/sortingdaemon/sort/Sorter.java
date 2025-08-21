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
 * Single-pass sort pipeline:
 *  1) Merge compatible stacks up to maxCount.
 *  2) Compact non-empty stacks to the front.
 *  3) Sort remaining stacks by a deterministic comparator.
 *
 * Handles non-stackable items (count = 1); they participate in swaps and sorting.
 */
public class Sorter {
    
    // ----- CATEGORIES -----
    private enum Cat {
        TOOLS,          // pickaxes, axes, shovels, hoes, shears, fishing rod, flint and steel
        WEAPONS,        // swords, trident
        RANGED,         // bow, crossbow, arrows
        ARMOR,          // armor, shield, elytra
        POTIONS,        // potions (regular/splash/lingering), suspicious stew
        FOOD,           // edible items
        REDSTONE,       // redstone-related blocks and components
        UTILITY,        // buckets, compasses, clocks, maps, spyglass, leads, name tags
        BLOCKS,         // any BlockItem not matched above
        MATERIALS,      // ores/ingots/gems/dyes and other stackables
        MISC            // fallback
    }

    // Classifies an ItemStack into a broad category for sorting
    private static Cat categoryOf(ItemStack st) {
        if (st.isEmpty()) return Cat.MISC;
        Item it = st.getItem();

        // TOOLS: by tags and specific utility tools
        if (st.isIn(ItemTags.PICKAXES) || st.isIn(ItemTags.AXES)
                || st.isIn(ItemTags.SHOVELS) || st.isIn(ItemTags.HOES)) {
            return Cat.TOOLS;
        }

        if (it == Items.SHEARS || it == Items.FISHING_ROD || it == Items.FLINT_AND_STEEL) return Cat.TOOLS;

        // WEAPONS
        if (st.isIn(ItemTags.SWORDS) || it == Items.TRIDENT) return Cat.WEAPONS;

        // RANGED: bow/crossbow enchantable + arrows
        if (st.isIn(ItemTags.BOW_ENCHANTABLE) || st.isIn(ItemTags.CROSSBOW_ENCHANTABLE)
                || it == Items.ARROW || it == Items.TIPPED_ARROW || it == Items.SPECTRAL_ARROW) {
            return Cat.RANGED;
        }

        // ARMOR: armor tag plus shield and elytra
        if (st.isIn(ItemTags.ARMOR_ENCHANTABLE) || it == Items.SHIELD || it == Items.ELYTRA) return Cat.ARMOR;

        // POTIONS
        if (it == Items.POTION || it == Items.SPLASH_POTION || it == Items.LINGERING_POTION || it == Items.SUSPICIOUS_STEW) {
            return Cat.POTIONS;
        }

        // FOOD: data component-based, plus honey bottle and milk bucket
        if (st.getComponents().contains(DataComponentTypes.FOOD) || it == Items.HONEY_BOTTLE || it == Items.MILK_BUCKET) {
            return Cat.FOOD;
        }

        // REDSTONE: explicit redstone-related blocks
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

        // UTILITY
        if (it == Items.BUCKET || it == Items.WATER_BUCKET || it == Items.LAVA_BUCKET || it == Items.POWDER_SNOW_BUCKET) return Cat.UTILITY;
        if (it == Items.COMPASS || it == Items.RECOVERY_COMPASS || it == Items.CLOCK || it == Items.FILLED_MAP || it == Items.SPYGLASS) return Cat.UTILITY;
        if (it == Items.LEAD || it == Items.NAME_TAG) return Cat.UTILITY;

        // BLOCKS
        if (it instanceof BlockItem) return Cat.BLOCKS;

        // MATERIALS / MISC
        if (!st.isStackable()) return Cat.MISC;  // non-stackables not matched above
        return Cat.MATERIALS;                    // remaining stackables as materials
    }


    // Runs the full sort procedure on the given slot subset
    public static void sortCurrent(MinecraftClient client, ScreenHandler sh, List<Integer> slotIds) {
        if (client == null || client.player == null || client.interactionManager == null) return;
        if (slotIds == null || slotIds.size() <= 1) return;

        // 1) Merge compatible stacks
        mergeStacks(client, sh, slotIds);

        // 2) Compact non-empty stacks to the front
        compactNonEmptyToFront(client, sh, slotIds);

        // 3) Sort with deterministic comparator
        sortByComparator(client, sh, slotIds, STACK_COMPARATOR);
    }

    // PASS 1: MERGE
    // Greedily merges later stacks into earlier compatible stacks
    private static void mergeStacks(MinecraftClient client, ScreenHandler sh, List<Integer> ids) {
        var slots = sh.slots;
        for (int iIdx = 0; iIdx < ids.size(); iIdx++) {
            int i = ids.get(iIdx);
            ItemStack dst = slots.get(i).getStack();
            if (dst.isEmpty()) continue;

            // Skip if already full
            int max = dst.getMaxCount();
            if (dst.getCount() >= max) continue;

            // Scan sources j > i to fill slot i
            for (int jIdx = iIdx + 1; jIdx < ids.size(); jIdx++) {
                int j = ids.get(jIdx);
                ItemStack src = slots.get(j).getStack();
                if (src.isEmpty()) continue;

                if (canStackTogether(dst, src)) {
                    // Simulate vanilla merge via cursor: PICKUP(src) -> PICKUP(dst) -> PICKUP(src) if leftover
                    clickPickup(client, sh.syncId, j);
                    clickPickup(client, sh.syncId, i);
                    if (!cursorEmpty(client)) clickPickup(client, sh.syncId, j);

                    // Refresh reference after clicks
                    dst = slots.get(i).getStack();
                    if (dst.getCount() >= dst.getMaxCount()) break; // stop if full
                }
            }
        }
    }

    // PASS 2: COMPACT
    // Moves all non-empty stacks to the front, preserving relative order
    private static void compactNonEmptyToFront(MinecraftClient client, ScreenHandler sh, List<Integer> ids) {
        var slots = sh.slots;
        int write = 0; // next target position for a non-empty stack
        for (int readIdx = 0; readIdx < ids.size(); readIdx++) {
            int read = ids.get(readIdx);
            if (!slots.get(read).getStack().isEmpty()) {
                int target = ids.get(write);
                if (read != target) {
                    // Swap non-empty 'read' into 'target'
                    swapSlots(client, sh.syncId, read, target);
                }
                write++;
            }
        }
        // trailing slots after 'write' are empty
    }

    // PASS 3: SORT
    // Selection sort over the non-empty prefix using the provided comparator
    private static void sortByComparator(MinecraftClient client, ScreenHandler sh, List<Integer> ids, Comparator<ItemStack> cmp) {
        var slots = sh.slots;

        // Determine non-empty length (empties already at the end)
        int nonEmptyCount = 0;
        for (int idx = 0; idx < ids.size(); idx++) {
            if (slots.get(ids.get(idx)).getStack().isEmpty()) break;
            nonEmptyCount++;
        }
        if (nonEmptyCount <= 1) return;

        // O(n^2) selection sort; acceptable for typical container sizes
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

    // CORE CLICK UTILS
    // Left-click pickup on a slot
    private static void clickPickup(MinecraftClient c, int syncId, int slotId) {
        c.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, c.player);
    }

    // Swaps the contents of two slots using cursor logic
    private static void swapSlots(MinecraftClient c, int syncId, int src, int dst) {
        if (src == dst) return;
        clickPickup(c, syncId, src);
        clickPickup(c, syncId, dst);
        if (!cursorEmpty(c)) clickPickup(c, syncId, src); // return leftover to source if any
    }

    // Checks if the cursor stack is empty
    private static boolean cursorEmpty(MinecraftClient c) {
        return c.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    // EQUALITY / COMBINE
    // Returns true if two stacks can be merged (same item and components)
    private static boolean canStackTogether(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!a.isStackable() || !b.isStackable()) return false;
        if (a.getItem() != b.getItem()) return false;
        // 1.21.x: components/NBT must match
        return Objects.equals(a.getComponents(), b.getComponents());
    }

    // COMPARATOR
    // Deterministic ordering: category → item id → components → damage ↑ → count ↓
    private static final Comparator<ItemStack> STACK_COMPARATOR = (a, b) -> {
        // Place empties at the end
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        // 0) Category
        int c = Integer.compare(categoryOf(a).ordinal(), categoryOf(b).ordinal());
        if (c != 0) return c;

        // 1) Item id
        c = a.getItem().toString().compareTo(b.getItem().toString());
        if (c != 0) return c;

        // 2) Components/NBT (enchants, custom name, etc.)
        c = String.valueOf(a.getComponents()).compareTo(String.valueOf(b.getComponents()));
        if (c != 0) return c;

        // 3) Durability (lower damage first)
        c = Integer.compare(a.getDamage(), b.getDamage());
        if (c != 0) return c;

        // 4) Stack size (larger first)
        return Integer.compare(b.getCount(), a.getCount());
    };

}

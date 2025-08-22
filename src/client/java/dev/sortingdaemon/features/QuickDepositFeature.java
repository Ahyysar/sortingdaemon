package dev.sortingdaemon.features;

import dev.sortingdaemon.config.SDConfig;
import dev.sortingdaemon.fav.FavoriteSlots;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Implements Quick Deposit:
 * Automatically moves items from the player inventory into a container
 * if the container already contains at least one stack of that item type.
 */

public final class QuickDepositFeature {
    private QuickDepositFeature() {}

    // Executes quick deposit into the currently open container screen.
    public static int run(MinecraftClient mc, HandledScreen<?> screen) {
        if (mc.interactionManager == null || mc.player == null) return 0;

        ScreenHandler sh = screen.getScreenHandler();

        // Collect item types already present in the container
        ObjectSet<Item> containerItems = new ObjectOpenHashSet<>();
        for (Slot slot : sh.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && slot.hasStack()) {
                containerItems.add(slot.getStack().getItem());
            }
        }
        if (containerItems.isEmpty()) return 0;

        // Iterate over player inventory slots and QUICK_MOVE matching items
        SDConfig.QuickDeposit cfg = SDConfig.get().quickDeposit;
        int moved = 0;

        for (Slot slot : sh.slots) {
            boolean isPlayerInv = slot.inventory instanceof PlayerInventory;
            if (!isPlayerInv || !slot.hasStack()) continue;

            if (!shouldConsiderPlayerSlot(slot, cfg)) continue;

            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            if (!containerItems.contains(st.getItem())) continue;

            mc.interactionManager.clickSlot(
                    sh.syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );
            moved++;
        }
        return moved;
    }

    // Decides whether a given player slot should be included in quick deposit
    private static boolean shouldConsiderPlayerSlot(Slot slot, SDConfig.QuickDeposit cfg) {
        // Exclude only favorite slots in the player's main inventory
        if (FavoriteSlots.isPlayerInventoryFavorite(slot, MinecraftClient.getInstance().player)) {
            return false;
        }

        int i = slot.getIndex();

        // Hotbar (0–8) is always ignored
        if (i >= 0 && i <= PlayerInventory.getHotbarSize() - 1) {
            return false;
        }

        if (i <= 35) return cfg.includeMain;     // main inventory
        if (i <= 39) return cfg.includeArmor;    // armor
        if (i == 40) return cfg.includeOffhand;  // offhand
        return true;
    }


}

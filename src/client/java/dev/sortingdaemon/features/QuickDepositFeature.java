package dev.sortingdaemon.features;

import dev.sortingdaemon.config.SDConfig;
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

public final class QuickDepositFeature {
    private QuickDepositFeature() {}

    /** Возвращает кол-во слотов, по которым стрельнули QUICK_MOVE */
    public static int run(MinecraftClient mc, HandledScreen<?> screen) {
        if (mc.interactionManager == null || mc.player == null) return 0;

        ScreenHandler sh = screen.getScreenHandler();

        // 1) Собираем набор предметов, которые уже есть в КОНТЕЙНЕРЕ (не инвентарь игрока)
        ObjectSet<Item> containerItems = new ObjectOpenHashSet<>();
        for (Slot slot : sh.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && slot.hasStack()) {
                containerItems.add(slot.getStack().getItem());
            }
        }
        if (containerItems.isEmpty()) return 0;

        // 2) Идём по слотам ИГРОКА и шлём QUICK_MOVE для тех, что совпадают по типу
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
                    slot.id, // в твоих маппингах может быть index — если так, замени
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );
            moved++;
        }
        return moved;
    }

    private static boolean shouldConsiderPlayerSlot(Slot slot, SDConfig.QuickDeposit cfg) {
        int i = slot.getIndex();

        // хотбар 0–8
        if (i >= 0 && i <= 8) {
            return false; // <-- теперь хотбар всегда игнорируем
        }
        if (i <= 35) return cfg.includeMain;     // обычный инвентарь
        if (i <= 39) return cfg.includeArmor;    // броня
        if (i == 40) return cfg.includeOffhand;  // вторая рука
        return true;
    }

}

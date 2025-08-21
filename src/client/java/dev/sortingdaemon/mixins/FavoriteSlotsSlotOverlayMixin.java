package dev.sortingdaemon.mixins;

import dev.sortingdaemon.fav.FavoriteSlots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class FavoriteSlotsSlotOverlayMixin {
    @Shadow protected int x; // левый край GUI
    @Shadow protected int y; // верхний край GUI

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void sd$beforeDrawSlot(DrawContext ctx, Slot slot, CallbackInfo ci) {
        if (!sd$isPlayerInventorySlot(slot)) return;

        // Индекс 9..35 — только инвентарь (без хотбара/брони/крафта)
        int idx = slot.getIndex(); // если в твоих маппингах нет getIndex(), замени на slot.id
        if (idx < 9 || idx > 35) return;

        if (!FavoriteSlots.isFavorite(idx)) return;

        // Рисуем только рамку, тускло‑оранжевую, 90% непрозрачности
        int x1 = slot.x - 1;
        int y1 = slot.y - 1;
        ctx.drawBorder(x1, y1, 18, 18, 0xE6FFD08F);
    }

    @Unique
    private boolean sd$isPlayerInventorySlot(Slot s) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        try {
            return s.inventory == mc.player.getInventory();
        } catch (Throwable t) {
            return false;
        }
    }
}

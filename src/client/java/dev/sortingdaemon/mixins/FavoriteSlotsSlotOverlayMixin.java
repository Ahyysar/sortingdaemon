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

/**
 * Renders a subtle border over favorite slots in the player's main inventory (9..35).
 */

@Mixin(HandledScreen.class)
public abstract class FavoriteSlotsSlotOverlayMixin {
    @Shadow protected int x; // GUI left offset
    @Shadow protected int y; // GUI top offset

    // Draws the overlay before the vanilla slot render
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void sd$beforeDrawSlot(DrawContext ctx, Slot slot, CallbackInfo ci) {
        if (!sd$isPlayerInventorySlot(slot)) return;

        // Main inventory only: indices 9..35
        int idx = slot.getIndex(); // use slot.id if mappings differ
        if (idx < 9 || idx > 35) return;

        if (!FavoriteSlots.isFavorite(idx)) return;

        // Border: 18x18 around the slot; ARGB 0xE6FFD08F (~90% opacity, dim orange)
        int x1 = slot.x - 1;
        int y1 = slot.y - 1;
        ctx.drawBorder(x1, y1, 18, 18, 0xE6FFD08F);
    }

    // Checks whether the slot belongs to the player's inventory
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

package dev.sortingdaemon.mixins;

import dev.sortingdaemon.fav.FavoriteSlots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders a subtle border over favorite slots in the player's main inventory (9..35).
 */

@Mixin(HandledScreen.class)
public abstract class FavoriteSlotsSlotOverlayMixin {
    
    // Draws the overlay before the vanilla slot render
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void sd$beforeDrawSlot(DrawContext ctx, Slot slot, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        if (!FavoriteSlots.isPlayerInventoryFavorite(slot, mc.player)) return;

        int x1 = slot.x - 1;
        int y1 = slot.y - 1;
        ctx.drawBorder(x1, y1, 18, 18, 0xE6FFD08F);
    }

}

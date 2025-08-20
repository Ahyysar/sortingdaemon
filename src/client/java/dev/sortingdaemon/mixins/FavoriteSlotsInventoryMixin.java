package dev.sortingdaemon.mixins;

import dev.sortingdaemon.fav.FavoriteSlots;
import dev.sortingdaemon.mixins.access.HandledScreenAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class FavoriteSlotsInventoryMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void sd$renderFavsInventory(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HandledScreen<?> hs = (HandledScreen<?>)(Object) this;
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;

        ScreenHandler sh = acc.getHandler();
        int baseX = acc.getX();
        int baseY = acc.getY();

        for (Slot s : sh.slots) {
            int idx = s.getIndex();        // если нет getIndex(), используй s.id
            if (idx < 9 || idx > 35) continue;
            if (!FavoriteSlots.isFavorite(idx)) continue;

            int x1 = baseX + s.x - 1;
            int y1 = baseY + s.y - 1;

            //ctx.fill(x1, y1, x1 + 18, y1 + 18, 0xA0FFD54F);
            ctx.drawBorder(x1, y1, 18, 18, 0xE6FFD08F);
        }
    }
}

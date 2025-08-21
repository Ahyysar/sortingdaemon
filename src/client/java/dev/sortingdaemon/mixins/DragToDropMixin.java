package dev.sortingdaemon.mixins;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements drag-to-drop behavior:
 * Holding Shift + LMB, then dragging across slots, quickly moves items (Shift+Click equivalent).
 */

@Mixin(HandledScreen.class)
public abstract class DragToDropMixin extends Screen {
    protected DragToDropMixin(Text title) { super(title); }

    @Shadow protected abstract Slot getSlotAt(double x, double y);
    @Shadow protected abstract ScreenHandler getScreenHandler();

    @Unique private boolean sd$dragActive = false;
    @Unique private final IntSet sd$processed = new IntOpenHashSet();

    // Activates drag-to-drop mode on Shift + LMB
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sd$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {

        if (button != 0 || !hasShiftDown()) return;
        sd$dragActive = true;
        sd$processed.clear();

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot != null) sd$quickMove(slot);
    }

    // Processes slots while dragging with Shift + LMB held
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void sd$mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (!sd$dragActive || button != 0) return;
        if (!hasShiftDown()) { sd$stop(); return; }

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot != null) sd$quickMove(slot);
    }

    // Stops drag-to-drop mode when LMB is released
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void sd$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) sd$stop();
    }

    // Clears state and disables drag mode
    @Unique
    private void sd$stop() {
        sd$dragActive = false;
        sd$processed.clear();
    }

    // Performs quick-move (Shift+Click) on a given slot if not already processed
    @Unique
    private void sd$quickMove(Slot slot) {
        if (slot == null) return;
        if (!slot.hasStack()) return; // adjust to slot.hasItem() if mappings differ

        int slotId;
        try {
            slotId = slot.id;         // primary mapping
        } catch (Throwable t) {
            try {
                // fallback for different mappings
                slotId = (int) Slot.class.getField("index").get(slot);
            } catch (Throwable t2) {
                return;
            }
        }

        // Skip if already processed
        if (!sd$processed.add(slotId)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        ScreenHandler sh = getScreenHandler();
        PlayerEntity player = mc.player;

        // Trigger vanilla QUICK_MOVE action (Shift+Click equivalent)
        mc.interactionManager.clickSlot(
                sh.syncId,
                slotId,
                0, // button
                SlotActionType.QUICK_MOVE, 
                player
        );
    }
}

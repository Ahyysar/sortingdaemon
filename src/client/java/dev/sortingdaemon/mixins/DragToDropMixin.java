package dev.sortingdaemon.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Drag-to-drop: Shift + LMB + drag across slots -> QUICK_MOVE (как Shift+Click).
 */
@Mixin(HandledScreen.class)
public abstract class DragToDropMixin extends Screen {
    protected DragToDropMixin(Text title) { super(title); }

    @Shadow protected abstract Slot getSlotAt(double x, double y);
    @Shadow protected abstract ScreenHandler getScreenHandler();

    @Unique private boolean sd$dragActive = false;
    @Unique private final IntSet sd$processed = new IntOpenHashSet();

    // Click: enable drag mode and try to quick-move the slot under cursor
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sd$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !hasShiftDown()) return;

        sd$dragActive = true;
        sd$processed.clear();

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null) return;

        // Consume vanilla only if a transfer actually happened
        if (sd$quickMove(slot)) {
            cir.setReturnValue(true);
        }
        // If no transfer happened, let vanilla handle Shift+Click (useful in Creative)
    }

    // Dragged: while holding Shift + LMB, quick-move slots under cursor
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void sd$mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (!sd$dragActive || button != 0) return;
        if (!hasShiftDown()) { sd$stop(); return; }

        Slot slot = getSlotAt(mouseX, mouseY);
        
        if (slot != null && sd$quickMove(slot)) {
            // Consume vanilla only on successful transfer
            cir.setReturnValue(true);
        }
    }

    // Release: LMB up disables drag mode
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void sd$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0) sd$stop();
    }

    @Unique
    private void sd$stop() {
        sd$dragActive = false;
        sd$processed.clear();
    }

    /**
     * Universal QUICK_MOVE.
     * - Regular screens: uses clickSlot(..., QUICK_MOVE).
     * - Creative: emulates transfers between hotbar and main inventory with CreativeInventoryActionC2SPacket.
     * Returns true if a transfer occurred (safe to consume vanilla).
     */
    @Unique
    private boolean sd$quickMove(Slot slot) {
        if (slot == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.interactionManager == null) return false;

        boolean isCreative = (Object) this instanceof CreativeInventoryScreen;

        if (isCreative) {

            // Ignore Creative catalog slots; operate only on the real player inventory
            if (slot.getClass().getName().equals("net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$CreativeSlot")) {
                return false;
            }

            if (slot.inventory != mc.player.getInventory()) return false;

            // Anti-duplicate key based on player inventory index (not screen slot id)
            int processedKey = 1_000_000 + slot.getIndex();
            if (!sd$processed.add(processedKey)) return false;

            // Try Creative quick-move; if it fails, roll back the processed mark
            if (!sd$creativeQuickMove(mc, slot)) {
                sd$processed.remove(processedKey);
                return false;
            }
            return true;
        }

        // Non-Creative: standard QUICK_MOVE
        if (!slot.hasStack()) return false;

        // Obtain screenHandler slot id (fallback to lookup if field name differs)
        int slotId;
        try {
            slotId = slot.id; // Yarn: id
        } catch (Throwable t) {
            ScreenHandler sh = getScreenHandler();
            slotId = sh.slots.indexOf(slot);
            if (slotId < 0) return false;
        }

        // Anti-duplicate guard
        if (!sd$processed.add(slotId)) return false;

        ScreenHandler sh = getScreenHandler();
        PlayerEntity player = mc.player;
        mc.interactionManager.clickSlot(
                sh.syncId,
                slotId,
                0,
                SlotActionType.QUICK_MOVE,
                player
        );
        return true;
    }

    // Creative utilities

    @Unique
    private static int sd$toCreativeNetSlot(int invIndex) {
        // PlayerInventory: 0..8 (hotbar) -> 36..44; 9..35 (main) -> 9..35
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    /**
     * Creative-mode QUICK_MOVE analogue.
     * - From hotbar (0..8) -> to main (9..35)
     * - From main (9..35) -> to hotbar (0..8)
     * 1) Merge into compatible stacks
     * 2) Otherwise place into the first empty slot
     */
    @Unique
    private boolean sd$creativeQuickMove(MinecraftClient mc, Slot slot) {
        PlayerInventory pinv = mc.player.getInventory();
        int invIdx = slot.getIndex();
        if (invIdx < 0 || invIdx >= pinv.size()) return false;

        ItemStack src = pinv.getStack(invIdx);
        if (src.isEmpty()) return false;

        boolean fromHotbar = invIdx < 9;
        int dstStart = fromHotbar ? 9  : 0;  // hotbar -> main, main -> hotbar
        int dstEnd   = fromHotbar ? 36 : 9;

        int remaining = src.getCount();

        // Merge into compatible stacks
        for (int i = dstStart; i < dstEnd && remaining > 0; i++) {
            ItemStack dst = pinv.getStack(i);
            if (dst.isEmpty()) continue;

            if (sd$canCombine(dst, src)) {
                int max = Math.min(dst.getMaxCount(), pinv.getMaxCountPerStack());
                if (dst.getCount() >= max) continue;

                int canMove = Math.min(remaining, max - dst.getCount());
                if (canMove <= 0) continue;

                ItemStack newDst = dst.copy();
                newDst.setCount(dst.getCount() + canMove);

                ItemStack newSrc = src.copy();
                newSrc.setCount(remaining - canMove);

                int dstNet = sd$toCreativeNetSlot(i);
                int srcNet = sd$toCreativeNetSlot(invIdx);

                var nc = mc.getNetworkHandler();
                if (nc == null) return false;

                nc.sendPacket(new CreativeInventoryActionC2SPacket(dstNet, newDst.copy()));
                pinv.setStack(i, newDst);

                remaining -= canMove;
                src = newSrc;
            }
        }

        // If fully merged, clear the source and exit
        if (remaining <= 0) {
            int srcNet = sd$toCreativeNetSlot(invIdx);
            var nc = mc.getNetworkHandler();
            if (nc == null) return false;

            nc.sendPacket(new CreativeInventoryActionC2SPacket(srcNet, ItemStack.EMPTY));
            pinv.setStack(invIdx, ItemStack.EMPTY);
            return true;
        }

        // Place leftover into the first empty target slot
        int emptyIdx = -1;
        for (int i = dstStart; i < dstEnd; i++) {
            if (pinv.getStack(i).isEmpty()) { emptyIdx = i; break; }
        }
        if (emptyIdx == -1) return false;

        ItemStack toPlace = src.copy();
        toPlace.setCount(remaining);

        int dstNet = sd$toCreativeNetSlot(emptyIdx);
        int srcNet = sd$toCreativeNetSlot(invIdx);

        var nc = mc.getNetworkHandler();
        if (nc == null) return false;

        // Write destination first, then clear source
        nc.sendPacket(new CreativeInventoryActionC2SPacket(dstNet, toPlace.copy()));
        nc.sendPacket(new CreativeInventoryActionC2SPacket(srcNet, ItemStack.EMPTY));

        pinv.setStack(emptyIdx, toPlace);
        pinv.setStack(invIdx, ItemStack.EMPTY);

        return true;
    }

    @Unique
    private static boolean sd$canCombine(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        // Try ItemStack.canCombine(a, b) if available in current mappings
        try {
            var m = ItemStack.class.getDeclaredMethod("canCombine", ItemStack.class, ItemStack.class);
            Object r = m.invoke(null, a, b);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) {}

        // Basic checks
        if (a.getItem() != b.getItem()) return false;

        if (a.isDamageable() || b.isDamageable()) {
            if (a.getDamage() != b.getDamage()) return false;
        }

        // Compare NBT/share tags via reflection for cross-version compatibility
        try {
            var areNbtEqual = ItemStack.class.getDeclaredMethod("areNbtEqual", ItemStack.class, ItemStack.class);
            Object r = areNbtEqual.invoke(null, a, b);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) {
            try {
                var areShareTagsEqual = ItemStack.class.getDeclaredMethod("areShareTagsEqual", ItemStack.class, ItemStack.class);
                Object r = areShareTagsEqual.invoke(null, a, b);
                if (r instanceof Boolean) return (Boolean) r;
            } catch (Throwable ignored2) {
                // Fallback: treat as compatible by item only
                return true;
            }
        }
        return true;
    }
}

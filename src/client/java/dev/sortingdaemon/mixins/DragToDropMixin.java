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
    @Shadow protected int x;    // GUI left offset
    @Shadow protected int y;    // GUI top offset
    
    
    @Unique private boolean sd$dragActive = false;                      // drag mode flag
    @Unique private final IntSet sd$processed = new IntOpenHashSet();   // anti-duplicate set

    // Click: enable drag mode and try to quick-move the slot under cursor
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sd$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        
        if (button != 0 || !hasShiftDown()) return;

        sd$dragActive = true;
        sd$processed.clear();

        Slot slot;
        if ((Object)this instanceof CreativeInventoryScreen) {
            slot = sd$getSlotAtCreative(mouseX, mouseY);
        } else {
            slot = getSlotAt(mouseX, mouseY);
        }

        if (slot == null) return;

        // Intentionally not consuming vanilla here to allow fallback behavior in Creative

    }

    // Dragged: while holding Shift + LMB, quick-move slots under cursor
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void sd$mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        
        if (!sd$dragActive) return;
        if (!hasShiftDown()) { sd$stop(); return; }

        Slot slot;
        if ((Object)this instanceof CreativeInventoryScreen) {
            slot = sd$getSlotAtCreative(mouseX, mouseY);
        } else {
            slot = getSlotAt(mouseX, mouseY);
        }
        
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

            // Only operate on player inventory slots in Creative
            if (!(slot.inventory instanceof PlayerInventory)) return false;

            // Use player inventory index as dedupe key
            int processedKey = 1_000_000 + slot.getIndex();
            if (!sd$processed.add(processedKey)) return false;

            // Try Creative quick-move; if it fails, roll back the processed mark
            if (!sd$creativeQuickMove(mc, slot)) {
                sd$processed.remove(processedKey);
                return false;
            }

            return true;
        }

        // Non-Creative path
        if (!slot.hasStack()) return false;

        // Resolve screen handler slot id
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
     * Creative analogue of QUICK_MOVE.
     * 1) Merge into compatible stacks within the target range.
     * 2) Otherwise place into the first empty slot.
     */

    @Unique
    private boolean sd$creativeQuickMove(MinecraftClient mc, Slot slot) {
        PlayerInventory pinv = mc.player.getInventory();

        // Resolve player inventory index by identity match
        int invIdx = -1;
        for (int i = 0; i < pinv.size(); i++) {
            if (pinv.getStack(i) == slot.getStack()) {
                invIdx = i;
                break;
            }
        }
        if (invIdx == -1) return false;

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

        // Clear source if fully merged
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

        // Prefer ItemStack.canCombine if present
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
                return true; // fallback: item-only match
            }
        }
        return true;
    }

    @Unique
    private Slot sd$getSlotAtCreative(double mouseX, double mouseY) {
        if (!((Object)this instanceof CreativeInventoryScreen creative)) return null;

        // Only active on the inventory tab (selectedTab == 0)
        int selectedTab = 0;
        try {
            var field = CreativeInventoryScreen.class.getDeclaredField("selectedTab");
            field.setAccessible(true);
            selectedTab = field.getInt(creative);
        } catch (Throwable t) {
            selectedTab = 0;
        }
        if (selectedTab != 0) return null;

        // Convert global mouse coords to GUI-relative coords
        int guiLeft = this.x;
        int guiTop = this.y;
        double relX = mouseX - guiLeft;
        double relY = mouseY - guiTop;

        ScreenHandler handler = getScreenHandler();
        
        // Hit-test only player inventory slots
        for (Slot slot : handler.slots) {
        if (!(slot.inventory instanceof PlayerInventory)) continue;
        int x = slot.x;
        int y = slot.y;
        if (relX >= x && relX < x + 16 && relY >= y && relY < y + 16) {
            return slot;
        }
    }
        return null;
    }
}

package dev.sortingdaemon.mixins;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.sortingdaemon.SortingDaemonClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Allows toggling favorite state of player inventory slots with a hotkey.
 * Requires CTRL + bound key while hovering a slot in the main inventory (9..35).
 */

@Mixin(HandledScreen.class)
public abstract class FavoriteSlotsMixin extends Screen {
    @Shadow protected abstract Slot getSlotAt(double x, double y);
    @Shadow protected abstract ScreenHandler getScreenHandler();
    @Shadow protected int x; // GUI background X offset
    @Shadow protected int y; // GUI background Y offset

    protected FavoriteSlotsMixin(Text title) { super(title); }

    @Unique private boolean sd$favWasDown = false;

    // Injects into tick to handle hotkey press detection
    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void sd$favsTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.currentScreen != (Object) this) return;

        long win = mc.getWindow().getHandle();

        // Control modifier must be held
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        // Resolve actual bound favorite toggle key
        var bound = InputUtil.fromTranslationKey(
                SortingDaemonClient.FAVORITE_TOGGLE_KEY.getBoundKeyTranslationKey()
        );

        // Poll key state with category awareness
        boolean keyDown = switch (bound.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(win, bound.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(win, bound.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };

        // Edge detection (CTRL + key pressed now but not before)
        boolean pressed = ctrl && keyDown && !sd$favWasDown;
        sd$favWasDown = keyDown;

        if (!pressed) return;

        // Convert raw mouse position to scaled GUI coordinates
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth()  / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        Slot slot = getSlotAt(mx, my);
        if (!dev.sortingdaemon.fav.FavoriteSlots.isPlayerInventoryMainSlot(slot, mc.player)) return;

        int idx = slot.getIndex(); // Need for toggle
        boolean ok = dev.sortingdaemon.fav.FavoriteSlots.toggleFavorite(idx);
        if (ok) {
            mc.player.sendMessage(Text.translatable(
                dev.sortingdaemon.fav.FavoriteSlots.isFavorite(idx)
                    ? "sortingdaemon.fav.added"
                    : "sortingdaemon.fav.removed"
            ), true);
        }


    }

}

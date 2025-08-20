package dev.sortingdaemon.mixins;

import dev.sortingdaemon.SortingDaemonClient;
import dev.sortingdaemon.fav.FavoriteSlots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class FavoriteSlotsMixin extends Screen {
    @Shadow protected abstract Slot getSlotAt(double x, double y);
    @Shadow protected abstract ScreenHandler getScreenHandler();
    @Shadow protected int x; // левый край фона (background X)
    @Shadow protected int y; // верхний край фона (background Y)
    // (в некоторых версиях называются backgroundX/backgroundY — см. примечание ниже)

    protected FavoriteSlotsMixin(Text title) { super(title); }

    @Unique private boolean sd$favWasDown = false;

    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void sd$favsTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.currentScreen != (Object) this) return;

        long win = mc.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        // читаем реально привязанную клавишу (не обязательно Z)
        var bound = InputUtil.fromTranslationKey(
                SortingDaemonClient.FAVORITE_TOGGLE_KEY.getBoundKeyTranslationKey()
        );

        boolean keyDown = switch (bound.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(win, bound.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(win, bound.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };

        boolean pressed = ctrl && keyDown && !sd$favWasDown; // edge-detect
        sd$favWasDown = keyDown;

        if (!pressed) return;

        // дальше твой код toggle по слоту...
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth()  / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        Slot slot = getSlotAt(mx, my);
        if (slot == null || !sd$isPlayerInventorySlot(slot)) return;

        int idx = slot.getIndex(); // или slot.id в твоих маппингах
        boolean ok = dev.sortingdaemon.fav.FavoriteSlots.toggleFavorite(idx);
        if (ok) {
            mc.player.sendMessage(Text.translatable(
                dev.sortingdaemon.fav.FavoriteSlots.isFavorite(idx)
                        ? "sortingdaemon.fav.added"
                        : "sortingdaemon.fav.removed"
            ), true);
        }
    }


    @Unique
    private boolean sd$isPlayerInventorySlot(Slot s) {
        // Проверяем, что это именно инвентарь игрока и индекс в 9..35
        try {
            boolean isPlayerInv = s.inventory == MinecraftClient.getInstance().player.getInventory();
            int i = s.getIndex();
            return isPlayerInv && i >= 9 && i <= 35;
        } catch (Throwable t) {
            return false;
        }
    }

    // 2.2 Подсветка избранных слотов (контур/заливка поверх слота)
    @Inject(method = "render", at = @At("TAIL"))
    private void sd$renderFavs(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenHandler sh = getScreenHandler();

        for (Slot s : sh.slots) {
            if (!sd$isPlayerInventorySlot(s)) continue;
            if (!FavoriteSlots.isFavorite(s.getIndex())) continue; // если нет getIndex(), возьми s.id

            // Slot.x/y заданы относительно фона — прибавляем смещение экрана
            int rx = this.x + s.x;   // или backgroundX
            int ry = this.y + s.y;   // или backgroundY

            int x1 = rx - 1, y1 = ry - 1;
            int x2 = x1 + 18, y2 = y1 + 18;

            // более заметная альфа
            // ctx.fill(x1, y1, x2, y2, 0xA0FFD54F);  // заливка (жёлтый, ~63% прозрачности)
            ctx.drawBorder(x1, y1, 18, 18, 0xE6FFD08F); // рамка
        }
    }





}

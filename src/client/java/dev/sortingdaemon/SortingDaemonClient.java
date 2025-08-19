package dev.sortingdaemon;

import dev.sortingdaemon.sort.InventoryRangeResolver;
import dev.sortingdaemon.sort.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortingDaemonClient implements ClientModInitializer {
    public static final String MODID = "sortingdaemon";
    private static final Logger LOG = LoggerFactory.getLogger(MODID);

    private static KeyBinding sortKeyPrimary; // по умолчанию СКМ
    private static KeyBinding sortKeyAlt;     // по умолчанию G

    // Состояние для edge-detect’а
    private static boolean primaryWasDown = false;
    private static boolean altWasDown = false;

    @Override
    public void onInitializeClient() {
        sortKeyPrimary = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sortingdaemon.sort.primary",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                "key.categories.sortingdaemon"
        ));

        sortKeyAlt = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sortingdaemon.sort.alt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.sortingdaemon"
        ));

        LOG.info("Primary key registered: {}", sortKeyPrimary.getBoundKeyTranslationKey());
        LOG.info("Alt key registered: {}", sortKeyAlt.getBoundKeyTranslationKey());
        LOG.info("SortingDaemon loaded; keybinds registered.");

        // Используем START_CLIENT_TICK и опрашиваем GLFW напрямую по типу bound key
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client == null) return;

            final long win = client.getWindow().getHandle();

            // читаем фактически назначенные клавиши
            final InputUtil.Key pKey = InputUtil.fromTranslationKey(sortKeyPrimary.getBoundKeyTranslationKey());
            final InputUtil.Key aKey = InputUtil.fromTranslationKey(sortKeyAlt.getBoundKeyTranslationKey());


            // текущее «нажато/нет» с учётом типа (мышь/клава)
            boolean primaryDownNow = isKeyCurrentlyDown(win, pKey);
            boolean altDownNow     = isKeyCurrentlyDown(win, aKey);

            // триггеры нажатий (up -> down)
            boolean primaryPressed = primaryDownNow && !primaryWasDown;
            boolean altPressed     = altDownNow && !altWasDown;

            primaryWasDown = primaryDownNow;
            altWasDown     = altDownNow;

            // работать только когда открыт контейнер
            if (client.currentScreen instanceof HandledScreen<?> screen) {
                if (primaryPressed || altPressed) {
                    var handler = screen.getScreenHandler();
                    var range   = InventoryRangeResolver.resolve(handler);

                    LOG.info("[SortingDaemon] Triggered in {} slots={} range=[{}..{})",
                            screen.getClass().getSimpleName(),
                            handler.slots.size(),
                            range.startInclusive(),
                            range.endExclusive());

                    Sorter.sortCurrent(client, handler, range);

                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[SortingDaemon] Sorting…"), true);
                    }
                }
            } else {
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Sort pressed but no HandledScreen open");
                }
            }
        });
    }

    // Правильный опрос: мышь -> glfwGetMouseButton, клавиатура -> glfwGetKey
    private static boolean isKeyCurrentlyDown(long windowHandle, InputUtil.Key key) {
        switch (key.getCategory()) {
            case MOUSE -> {
                // код мыши — это GLFW_MOUSE_BUTTON_*
                return GLFW.glfwGetMouseButton(windowHandle, key.getCode()) == GLFW.GLFW_PRESS;
            }
            case KEYSYM, SCANCODE -> {
                // код клавиши — это GLFW_KEY_*
                return GLFW.glfwGetKey(windowHandle, key.getCode()) == GLFW.GLFW_PRESS;
            }
            default -> {
                return false;
            }
        }
    }
}

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

import java.util.List;

public class SortingDaemonClient implements ClientModInitializer {
    public static final String MODID = "sortingdaemon";
    private static final Logger LOG = LoggerFactory.getLogger(MODID);

    private static KeyBinding sortKeyPrimary; // по умолчанию СКМ
    private static KeyBinding sortKeyAlt;     // по умолчанию G

    // edge-detect
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
        LOG.info("Alt  key registered: {}", sortKeyAlt.getBoundKeyTranslationKey());
        LOG.info("SortingDaemon loaded; keybinds registered.");

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client == null) return;

            final long win = client.getWindow().getHandle();

            // читаем назначенные клавиши (через translation key)
            final InputUtil.Key pKey = InputUtil.fromTranslationKey(sortKeyPrimary.getBoundKeyTranslationKey());
            final InputUtil.Key aKey = InputUtil.fromTranslationKey(sortKeyAlt.getBoundKeyTranslationKey());

            boolean primaryDownNow = isKeyDown(win, pKey);
            boolean altDownNow     = isKeyDown(win, aKey);

            boolean primaryPressed = primaryDownNow && !primaryWasDown;
            boolean altPressed     = altDownNow && !altWasDown;

            primaryWasDown = primaryDownNow;
            altWasDown     = altDownNow;

            if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Sort pressed but no HandledScreen open");
                }
                return;
            }

            // Блокируем креативный «каталог всех предметов»
            String screenClass = screen.getClass().getName();
            if (screenClass.contains("CreativeInventoryScreen")) {
                // В креативе не трогаем, иначе ломается виртуальный список предметов
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Ignored on CreativeInventoryScreen");
                }
                return;
            }

            if (primaryPressed || altPressed) {
                var handler = screen.getScreenHandler();
                List<Integer> ids = InventoryRangeResolver.resolveSlotIndices(handler);

                LOG.info("[SortingDaemon] Triggered in {} slots={} picked={}",
                        screen.getClass().getSimpleName(), handler.slots.size(), ids.size());

                if (!ids.isEmpty()) {
                    Sorter.sortCurrent(client, handler, ids);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[SortingDaemon] Sorting…"), true);
                    }
                }
            }
        });
    }

    private static boolean isKeyDown(long win, InputUtil.Key key) {
        return switch (key.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(win, key.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(win, key.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}

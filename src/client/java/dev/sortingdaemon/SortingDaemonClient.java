package dev.sortingdaemon;

import dev.sortingdaemon.sort.InventoryRangeResolver;
import dev.sortingdaemon.sort.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;


import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.sortingdaemon.config.SDConfig;
import dev.sortingdaemon.features.QuickDepositFeature;

import java.util.List;

public class SortingDaemonClient implements ClientModInitializer {
    public static final String MODID = "sortingdaemon";
    private static final Logger LOG = LoggerFactory.getLogger(MODID);

    private static KeyBinding sortKeyPrimary; // по умолчанию СКМ
    private static KeyBinding sortKeyAlt;     // по умолчанию G
    private static KeyBinding QUICK_DEPOSIT_KEY; // по умолчанию K


    // edge-detect
    private static boolean primaryWasDown = false;
    private static boolean altWasDown = false;
    private static boolean quickDepositWasDown = false;


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
            if (screen instanceof CreativeInventoryScreen) {
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Ignored on CreativeInventoryScreen");
                }
                return; // сортировку в креативе блокируем
            }

            if (primaryPressed || altPressed) {
                var handler = screen.getScreenHandler();
                List<Integer> ids = InventoryRangeResolver.resolveSlotIndices(handler);

                LOG.info("[SortingDaemon] Triggered in {} slots={} picked={}",
                        screen.getClass().getSimpleName(), handler.slots.size(), ids.size());

                if (!ids.isEmpty()) {
                    Sorter.sortCurrent(client, handler, ids);
                    if (client.player != null) {
                        client.player.sendMessage(Text.translatable("sortingdaemon.sorting.start"), true);
                    }

                }
            }
        

        });
        
        // создаём/загружаем конфиг при старте клиента
        SDConfig.get();
        
        // Регаем хоткей (K по умолчанию)
        QUICK_DEPOSIT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sortingdaemon.quick_deposit",
            SDConfig.get().quickDeposit.defaultKey != 0 ? SDConfig.get().quickDeposit.defaultKey : GLFW.GLFW_KEY_K,
            "key.categories.sortingdaemon"
            
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // читаем привязанную клавишу K (или что выбрано в настройках)
            final long win = client.getWindow().getHandle();
            var qKey = InputUtil.fromTranslationKey(QUICK_DEPOSIT_KEY.getBoundKeyTranslationKey());
            boolean down = isKeyDown(win, qKey);
            boolean pressed = down && !quickDepositWasDown; // edge-detect
            quickDepositWasDown = down;

            if (!pressed) return;

            Screen s = client.currentScreen;
            if (s instanceof HandledScreen<?> hs) {
                if (SDConfig.get().quickDeposit.enabled) {
                    int moved = QuickDepositFeature.run(client, hs);
                    if (moved > 0) {
                        client.player.sendMessage(Text.translatable("sortingdaemon.quick_deposit.start"), true);
                    } else {
                        client.player.sendMessage(Text.translatable("sortingdaemon.quick_deposit.none"), true);
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

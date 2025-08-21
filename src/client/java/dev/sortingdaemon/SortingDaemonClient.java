package dev.sortingdaemon;

import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.sortingdaemon.config.SDConfig;
import dev.sortingdaemon.fav.FavoriteSlots;
import dev.sortingdaemon.features.QuickDepositFeature;
import dev.sortingdaemon.sort.InventoryRangeResolver;
import dev.sortingdaemon.sort.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class SortingDaemonClient implements ClientModInitializer {
    // Mod identifier and logger
    public static final String MODID = "sortingdaemon";
    private static final Logger LOG = LoggerFactory.getLogger(MODID);

    // Sorting keybinds (defaults: MMB and G)
    private static KeyBinding sortKeyPrimary;
    private static KeyBinding sortKeyAlt;

    // Quick deposit keybind (default: K)
    private static KeyBinding QUICK_DEPOSIT_KEY;


    // Edge-detection flags for key press events
    private static boolean primaryWasDown = false;
    private static boolean altWasDown = false;
    private static boolean quickDepositWasDown = false;

    // Favorites toggle keybind (default: Z)
    public static KeyBinding FAVORITE_TOGGLE_KEY;


    @Override
    public void onInitializeClient() {
        // Register primary sort key (mouse)
        sortKeyPrimary = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sortingdaemon.sort.primary",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                "key.categories.sortingdaemon"
        ));

        // Register alternate sort key (keyboard)
        sortKeyAlt = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sortingdaemon.sort.alt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.sortingdaemon"
        ));

        LOG.info("SortingDaemon loaded; keybinds registered.");

        // Load favorite slot configuration on startup
        FavoriteSlots.load();

        // Handle sort key presses at the start of each client tick
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client == null) return;

            final long win = client.getWindow().getHandle();

            // Resolve bound keys via translation keys to respect user remaps
            final InputUtil.Key pKey = InputUtil.fromTranslationKey(sortKeyPrimary.getBoundKeyTranslationKey());
            final InputUtil.Key aKey = InputUtil.fromTranslationKey(sortKeyAlt.getBoundKeyTranslationKey());
            // Edge-detect press events
            boolean primaryDownNow = isKeyDown(win, pKey);
            boolean altDownNow     = isKeyDown(win, aKey);

            boolean primaryPressed = primaryDownNow && !primaryWasDown;
            boolean altPressed     = altDownNow && !altWasDown;

            primaryWasDown = primaryDownNow;
            altWasDown     = altDownNow;

            // Require a handled screen (container) to be open
            if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Sort pressed but no HandledScreen open");
                }
                return;
            }

            // Ignore creative catalog to prevent unintended sorting
            if (screen instanceof CreativeInventoryScreen) {
                if (primaryPressed || altPressed) {
                    LOG.info("[SortingDaemon] Ignored on CreativeInventoryScreen");
                }
                return;
            }

            // Trigger sorting on press
            if (primaryPressed || altPressed) {
                
                var handler = screen.getScreenHandler();

                // Resolve inventory slots and exclude favorites
                List<Integer> ids = InventoryRangeResolver.resolveSlotIndices(handler);
                ids.removeIf(FavoriteSlots::isFavorite);

                LOG.info("[SortingDaemon] Triggered in {} slots={} picked={}",
                        screen.getClass().getSimpleName(), handler.slots.size(), ids.size());
                
                // Execute sort and show actionbar feedback
                if (!ids.isEmpty()) {
                    Sorter.sortCurrent(client, handler, ids);
                    if (client.player != null) {
                        client.player.sendMessage(Text.translatable("sortingdaemon.sorting.start"), true);
                    }

                }
            }
        

        });
        
        // Ensure config is loaded early
        SDConfig.get();
        
        // Register quick deposit key (default K or config override)
        QUICK_DEPOSIT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sortingdaemon.quick_deposit",
            SDConfig.get().quickDeposit.defaultKey != 0 ? SDConfig.get().quickDeposit.defaultKey : GLFW.GLFW_KEY_K,
            "key.categories.sortingdaemon"
            
        ));

        // Handle quick deposit at the end of each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null) return;
            final long win = client.getWindow().getHandle();

            // Edge-detect quick deposit press
            var qKey = InputUtil.fromTranslationKey(QUICK_DEPOSIT_KEY.getBoundKeyTranslationKey());
            boolean down = isKeyDown(win, qKey);
            boolean pressed = down && !quickDepositWasDown;
            quickDepositWasDown = down;

            if (!pressed) return;

            // Run quick deposit only on handled screens
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

        // Register favorites toggle keybind
        FAVORITE_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sortingdaemon.fav_toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.sortingdaemon"
        ));

    }

    // Polls GLFW for the current state of a mapped key or mouse button
    private static boolean isKeyDown(long win, InputUtil.Key key) {
        return switch (key.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(win, key.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(win, key.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}

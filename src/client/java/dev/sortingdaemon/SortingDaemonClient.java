package dev.sortingdaemon;

import dev.sortingdaemon.sort.InventoryRangeResolver;
import dev.sortingdaemon.sort.Sorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortingDaemonClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("sortingdaemon");

    // Альтернативный хоткей на клавиатуре (работает в GUI)
    private static KeyBinding keySortAltG;

    // Для опроса СКМ (edge detect)
    private static boolean mmbWasDown = false;
    private static boolean gWasDown = false;

    @Override
    public void onInitializeClient() {
        System.out.println("[SortingDaemon] onInitializeClient fired");

        keySortAltG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sortingdaemon.sort_alt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.sortingdaemon"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;

            // один раз получаем дескриптор окна
            long win = client.getWindow().getHandle();

            // --- G через GLFW (edge detect)
            boolean gDownNow = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
            if (client.currentScreen instanceof HandledScreen<?> screen) {
                if (gDownNow && !gWasDown) {
                    var handler = screen.getScreenHandler();
                    var range = InventoryRangeResolver.resolve(handler);
                    System.out.println("[SortingDaemon] G (GLFW) → sorting");
                    Sorter.sortCurrent(client, handler, range);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[SortingDaemon] Sorting…"), true);
                    }
                }
            } else {
                if (gDownNow && !gWasDown) {
                    System.out.println("[SortingDaemon] G pressed but no HandledScreen open");
                }
            }
            gWasDown = gDownNow;

            // --- СКМ через GLFW (edge detect)
            if (client.currentScreen instanceof HandledScreen<?> screen) {
                boolean mmbDownNow = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
                if (mmbDownNow && !mmbWasDown) {
                    var handler = screen.getScreenHandler();
                    var range = InventoryRangeResolver.resolve(handler);

                    System.out.println("[SortingDaemon] MMB in " + screen.getClass().getSimpleName()
                            + " slots=" + handler.slots.size()
                            + " range=[" + range.startInclusive() + ".." + range.endExclusive() + ")");

                    Sorter.sortCurrent(client, handler, range);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("[SortingDaemon] Sorting…"), true);
                    }
                }
                mmbWasDown = mmbDownNow;
            } else {
                mmbWasDown = false;
            }
        });


        LOG.info("SortingDaemon loaded; keybinds registered.");
    }
}

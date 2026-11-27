package fr.coquelicot.snacklab;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens the Snack Lab with a key (L by default) or the client command /snacklab. */
public final class SnackLabClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SnackLab");
    private static KeyMapping openKey;
    /** Set by /snacklab: the chat screen closes right after a command, so the lab opens on the next tick. */
    private static boolean openNextTick;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.snacklab.open", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L, "key.categories.snacklab"));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommandManager.literal("snacklab").executes(c -> {
                    openNextTick = true;
                    return 1;
                })));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean open = openNextTick;
            openNextTick = false;
            while (openKey.consumeClick()) open = true;
            if (open && mc.player != null && (mc.screen == null || openNextTickAllowed(mc))) mc.setScreen(new SnackLabScreen());
        });
    }

    private static boolean openNextTickAllowed(net.minecraft.client.Minecraft mc) {
        return mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;
    }
}

package fr.coquelicot.snacklab;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puts a built stack in the player's inventory. The server decides: in creative mode the stack goes through the
 * creative inventory packet (any server accepts it from a creative player), otherwise a /give command is sent, which
 * only works with the permission to use it.
 */
public final class SnackGiver {
    /** Longest command the client may send (ServerboundChatCommandPacket). */
    private static final int MAX_COMMAND = 256;

    public enum Outcome { CREATIVE, COMMAND, INVENTORY_FULL, NO_PERMISSION, TOO_LONG }

    private SnackGiver() {}

    public static Outcome give(Minecraft mc, ItemStack built, int quantity) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return Outcome.NO_PERMISSION;
        if (mc.gameMode.hasInfiniteItems()) {
            int remaining = quantity;
            boolean placed = false;
            while (remaining > 0) {
                int slot = player.getInventory().getFreeSlot();
                if (slot < 0) break;
                int count = Math.min(remaining, built.getMaxStackSize());
                ItemStack stack = built.copyWithCount(count);
                player.getInventory().setItem(slot, stack);
                // inventory menu slots: 36..44 for the hotbar, 9..35 for the main inventory
                mc.gameMode.handleCreativeModeItemAdd(stack.copy(), slot < 9 ? 36 + slot : slot);
                remaining -= count;
                placed = true;
            }
            return placed ? Outcome.CREATIVE : Outcome.INVENTORY_FULL;
        }
        if (!player.hasPermissions(2)) return Outcome.NO_PERMISSION;
        String command = command(built, quantity, true, player.registryAccess());
        if (command.length() > MAX_COMMAND) command = command(built, quantity, false, player.registryAccess());
        if (command.length() > MAX_COMMAND) return Outcome.TOO_LONG;
        player.connection.sendCommand(command);
        return Outcome.COMMAND;
    }

    /**
     * "give @s item[component=snbt,...] count", written from the stack's own components so that the server rebuilds
     * the very same stack. Without cosmetics, only the bait effects are kept (colour and ingredient list only change
     * how the snack looks).
     */
    static String command(ItemStack stack, int count, boolean cosmetics, HolderLookup.Provider registries) {
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            if (id == null) continue;
            if (!cosmetics && !id.getPath().equals("bait_effects")) continue;
            Tag tag = encode(entry.getKey(), entry.getValue().get(), ops);
            if (tag != null) parts.add(id + "=" + tag);
        }
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return "give @s " + item + (parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]") + " " + count;
    }

    @SuppressWarnings("unchecked")
    private static <T> Tag encode(DataComponentType<T> type, Object value, DynamicOps<Tag> ops) {
        Codec<T> codec = type.codec();
        return codec == null ? null : codec.encodeStart(ops, (T) value).result().orElse(null);
    }
}

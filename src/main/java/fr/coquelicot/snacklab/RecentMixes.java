package fr.coquelicot.snacklab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The last mixes created, kept in config/snacklab.json so they can be made again in one click. */
public final class RecentMixes {
    public static final int MAX = 8;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("snacklab.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Mix(SnackRecipes.Output output, List<String> items, int quantity) {
        public List<Item> resolve() {
            List<Item> list = new ArrayList<>();
            for (String id : items) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Optional<Item> item = rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
                item.ifPresent(list::add);
            }
            return list;
        }
    }

    private static List<Mix> mixes;

    private RecentMixes() {}

    public static List<Mix> get() {
        if (mixes == null) mixes = load();
        return mixes;
    }

    public static void remember(SnackRecipes.Output output, List<Item> items, int quantity) {
        List<String> ids = items.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).toList();
        Mix mix = new Mix(output, ids, quantity);
        List<Mix> list = get();
        list.removeIf(m -> m.output() == output && m.items().equals(ids));
        list.add(0, mix);
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(list);
    }

    private static List<Mix> load() {
        List<Mix> list = new ArrayList<>();
        if (!Files.exists(FILE)) return list;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray recent = root == null ? null : root.getAsJsonArray("recent");
            if (recent == null) return list;
            for (JsonElement e : recent) {
                JsonObject o = e.getAsJsonObject();
                SnackRecipes.Output output = "bait".equals(o.get("output").getAsString()) ? SnackRecipes.Output.BAIT : SnackRecipes.Output.SNACK;
                List<String> items = new ArrayList<>();
                for (JsonElement i : o.getAsJsonArray("items")) items.add(i.getAsString());
                list.add(new Mix(output, items, o.has("quantity") ? o.get("quantity").getAsInt() : 16));
            }
        } catch (IOException | RuntimeException e) {
            SnackLabClient.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
        }
        return list;
    }

    private static void save(List<Mix> list) {
        JsonArray recent = new JsonArray();
        for (Mix m : list) {
            JsonObject o = new JsonObject();
            o.addProperty("output", m.output() == SnackRecipes.Output.BAIT ? "bait" : "snack");
            JsonArray items = new JsonArray();
            m.items().forEach(items::add);
            o.add("items", items);
            o.addProperty("quantity", m.quantity());
            recent.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("recent", recent);
        try {
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SnackLabClient.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
        }
    }
}

package fr.coquelicot.snacklab;

import com.cobblemon.mod.common.api.cooking.Seasonings;
import com.cobblemon.mod.common.api.fishing.SpawnBait;
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects;
import com.cobblemon.mod.common.client.tooltips.SeasoningTooltipHelperKt;
import com.cobblemon.mod.common.item.crafting.SeasoningProcessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds Poké Snacks and Poké Baits the way Cobblemon's cooking pot does: the same accepted seasonings (the recipe's
 * seasoning tag), at most three of them, and Cobblemon's own seasoning processors applied in the recipe's order.
 */
public final class SnackRecipes {
    /** Seasoning tag of both cooking pot recipes (data/cobblemon/recipe/campfire_pot/poke_snack.json and poke_bait.json). */
    public static final TagKey<Item> BAIT_SEASONING = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("cobblemon", "recipe_filters/bait_seasoning"));
    public static final int MAX_SEASONINGS = 3; // CampfireBlockEntity.SEASONING_SLOTS = 10..12

    public enum Output {
        // seasoningProcessors of each cooking pot recipe, in the recipe's order
        SNACK("poke_snack", List.of("food_colour", "ingredient", "spawn_bait")),
        BAIT("poke_bait", List.of("spawn_bait", "food_colour"));

        public final String id;
        public final List<String> processors;

        Output(String id, List<String> processors) { this.id = id; this.processors = processors; }

        public Item item() { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", id)); }

        public Output next() { return this == SNACK ? BAIT : SNACK; }
    }

    public enum Category {
        ALL, TYPING, EGG_GROUP, RARE, STATS, OTHER;

        public Category next() { return values()[(ordinal() + 1) % values().length]; }

        public Component label() { return Component.translatable("snacklab.filter." + name().toLowerCase(Locale.ROOT)); }

        static Category of(String effectType) {
            return switch (effectType) {
                case "typing" -> TYPING;
                case "egg_group" -> EGG_GROUP;
                case "shiny_reroll", "rarity_bucket", "ha_chance", "alpha_chance", "mark_chance" -> RARE;
                case "ev", "iv", "nature" -> STATS;
                default -> OTHER;
            };
        }
    }

    /** One seasoning the pot accepts, with what the lab needs to show and filter it. */
    public record Candidate(Item item, ItemStack stack, List<Component> effectLines, Set<Category> categories, String searchText) {}

    private SnackRecipes() {}

    /** The result of the cooking pot for these seasonings, built by Cobblemon's own processors. */
    public static ItemStack build(Output output, List<Item> seasonings) {
        ItemStack result = new ItemStack(output.item());
        List<ItemStack> stacks = seasonings.stream().map(ItemStack::new).toList();
        for (String type : output.processors) {
            SeasoningProcessor processor = SeasoningProcessor.Companion.getProcessors().get(type);
            if (processor != null) processor.apply(result, stacks);
        }
        return result;
    }

    /** Cobblemon's own detailed bait effect lines (what it shows with Shift held) for a built stack. */
    public static List<Component> effectLines(ItemStack built) {
        try {
            return SeasoningTooltipHelperKt.generateAdditionalBaitEffectTooltip(built);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Every item the cooking pot would accept as a seasoning for these recipes, sorted by name. */
    public static List<Candidate> candidates() {
        List<SeasoningProcessor> processors = Output.SNACK.processors.stream()
                .map(t -> SeasoningProcessor.Companion.getProcessors().get(t)).filter(p -> p != null).toList();
        List<Candidate> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.is(BAIT_SEASONING)) continue;
            if (processors.stream().noneMatch(p -> p.consumesItem(stack))) continue;
            List<Component> lines = effectLines(build(Output.SNACK, List.of(item)));
            Set<Category> categories = new LinkedHashSet<>();
            for (SpawnBait.Effect effect : effectsOf(item, stack)) categories.add(Category.of(effect.getType().getPath()));
            StringBuilder text = new StringBuilder(stack.getHoverName().getString()).append(' ')
                    .append(BuiltInRegistries.ITEM.getKey(item).getPath());
            for (Component line : lines) text.append(' ').append(line.getString());
            list.add(new Candidate(item, stack, lines, categories, normalize(text.toString())));
        }
        list.sort(Comparator.comparing((Candidate c) -> c.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The bait effects a seasoning brings, as the pot's spawn_bait processor gathers them. */
    private static List<SpawnBait.Effect> effectsOf(Item item, ItemStack stack) {
        List<SpawnBait.Effect> effects = new ArrayList<>(Seasonings.INSTANCE.getBaitEffectsFromItemStack(stack));
        for (ResourceLocation id : SpawnBaitEffects.getBaitIdentifiersFromItem(item.builtInRegistryHolder())) {
            SpawnBait bait = SpawnBaitEffects.getFromIdentifier(id);
            if (bait != null) effects.addAll(bait.getEffects());
        }
        return effects;
    }

    /** Lower case without accents, so that "electrik" finds "Électrik". */
    public static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}

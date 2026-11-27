package fr.coquelicot.snacklab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The lab: pick up to three seasonings (search, filter by effect), see the effects of the mix as Cobblemon describes
 * them, choose snack or bait and a quantity, create. Recent mixes are one click away.
 */
public final class SnackLabScreen extends Screen {
    private static final int SLOT = 18;
    private static final int PAD = 6;
    private static final int[] QUANTITIES = {1, 16, 64};

    // remembered while the game runs, so reopening the lab shows the last mix
    private static SnackRecipes.Output output = SnackRecipes.Output.SNACK;
    private static int quantity = 16;
    private static SnackRecipes.Category category = SnackRecipes.Category.ALL;
    private static final List<Item> MIX = new ArrayList<>();

    private List<SnackRecipes.Candidate> all = List.of();
    private List<SnackRecipes.Candidate> shown = List.of();
    private ItemStack preview = ItemStack.EMPTY;
    private List<Component> previewLines = List.of();

    private EditBox search;
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridCols, gridRows, scroll;
    private int rightX, rightW, mixY, effectsY, recentY, bottomY;

    private Component status;
    private int statusColor;
    private long statusUntil;

    public SnackLabScreen() {
        super(Component.translatable("snacklab.title"));
    }

    @Override
    protected void init() {
        all = SnackRecipes.candidates();
        panelW = Math.min(width - 12, 470);
        panelH = Math.min(height - 12, 270);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int leftW = (int) (panelW * 0.56);
        int top = panelY + PAD + 12;
        bottomY = panelY + panelH - PAD - 9;

        // left: search, filter, grid, recent mixes
        int filterW = Math.min(96, leftW / 3);
        search = new EditBox(font, panelX + PAD, top, leftW - PAD - filterW - 4, 16, Component.translatable("snacklab.search"));
        search.setHint(Component.translatable("snacklab.search"));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(filterLabel(), b -> {
            category = category.next();
            b.setMessage(filterLabel());
            refilter();
        }).bounds(panelX + leftW - filterW, top - 1, filterW, 18).build());
        gridX = panelX + PAD;
        gridY = top + 21;
        gridCols = Math.max(1, (leftW - PAD) / SLOT);
        recentY = bottomY - 4 - 11 - SLOT;
        gridRows = Math.max(1, (recentY - 2 - gridY) / SLOT);

        // right: output, mix, quantity and create, effects
        rightX = panelX + leftW + PAD;
        rightW = panelX + panelW - PAD - rightX;
        addRenderableWidget(Button.builder(outputLabel(), b -> {
            output = output.next();
            b.setMessage(outputLabel());
            updatePreview();
        }).bounds(rightX, top - 1, rightW, 18).build());
        mixY = top + 21 + 11;
        addRenderableWidget(Button.builder(Component.translatable("snacklab.clear"), b -> {
            MIX.clear();
            updatePreview();
        }).bounds(rightX + rightW - 40, mixY, 40, SLOT).build());
        int half = (rightW - 4) / 2;
        int rowY = mixY + SLOT + 5;
        addRenderableWidget(Button.builder(quantityLabel(), b -> {
            int i = 0;
            while (i < QUANTITIES.length && QUANTITIES[i] != quantity) i++;
            quantity = QUANTITIES[(i + 1) % QUANTITIES.length];
            b.setMessage(quantityLabel());
        }).bounds(rightX, rowY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("snacklab.create"), b -> create())
                .bounds(rightX + half + 4, rowY, rightW - half - 4, 20).build());
        effectsY = rowY + 25;

        setInitialFocus(search);
        refilter();
        updatePreview();
    }

    private Component filterLabel() { return Component.translatable("snacklab.filter", category.label()); }

    private Component outputLabel() { return Component.translatable("snacklab.output", output.item().getDescription()); }

    private Component quantityLabel() { return Component.translatable("snacklab.quantity", String.valueOf(quantity)); }

    private void refilter() {
        String[] words = SnackRecipes.normalize(search == null ? "" : search.getValue().trim()).split("\\s+");
        shown = all.stream()
                .filter(c -> category == SnackRecipes.Category.ALL || c.categories().contains(category))
                .filter(c -> Arrays.stream(words).allMatch(w -> w.isEmpty() || c.searchText().contains(w)))
                .toList();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + gridCols - 1) / gridCols - gridRows);
    }

    private void updatePreview() {
        preview = SnackRecipes.build(output, MIX);
        previewLines = SnackRecipes.effectLines(preview);
    }

    private void add(Item item, boolean fill) {
        if (MIX.size() >= SnackRecipes.MAX_SEASONINGS) {
            setStatus(Component.translatable("snacklab.status.full"), 0xFFFF7070);
            return;
        }
        do MIX.add(item); while (fill && MIX.size() < SnackRecipes.MAX_SEASONINGS);
        updatePreview();
    }

    private void create() {
        if (all.isEmpty()) {
            setStatus(Component.translatable("snacklab.status.unavailable"), 0xFFFF7070);
            return;
        }
        ItemStack built = SnackRecipes.build(output, MIX);
        SnackGiver.Outcome outcome = SnackGiver.give(Minecraft.getInstance(), built, quantity);
        switch (outcome) {
            case CREATIVE -> setStatus(Component.translatable("snacklab.status.created", built.getHoverName(), String.valueOf(quantity)), 0xFF70FF70);
            case COMMAND -> setStatus(Component.translatable("snacklab.status.command"), 0xFF70FF70);
            case INVENTORY_FULL -> setStatus(Component.translatable("snacklab.status.inventory_full"), 0xFFFF7070);
            case TOO_LONG -> setStatus(Component.translatable("snacklab.status.too_long"), 0xFFFF7070);
            case NO_PERMISSION -> setStatus(Component.translatable("snacklab.status.no_permission"), 0xFFFF7070);
        }
        if (outcome == SnackGiver.Outcome.CREATIVE || outcome == SnackGiver.Outcome.COMMAND) {
            RecentMixes.remember(output, MIX, quantity);
        }
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    // ------------------------------------------------------------------ hit tests

    private int candidateAt(double mx, double my) {
        if (mx < gridX || my < gridY) return -1;
        int col = (int) ((mx - gridX) / SLOT), row = (int) ((my - gridY) / SLOT);
        if (col >= gridCols || row >= gridRows) return -1;
        int index = (scroll + row) * gridCols + col;
        return index < shown.size() ? index : -1;
    }

    private int mixSlotAt(double mx, double my) {
        if (my < mixY || my >= mixY + SLOT) return -1;
        for (int i = 0; i < SnackRecipes.MAX_SEASONINGS; i++) {
            int x = rightX + i * (SLOT + 2);
            if (mx >= x && mx < x + SLOT) return i;
        }
        return -1;
    }

    private boolean overResult(double mx, double my) {
        int x = resultX();
        return mx >= x && mx < x + SLOT && my >= mixY && my < mixY + SLOT;
    }

    private int resultX() { return rightX + SnackRecipes.MAX_SEASONINGS * (SLOT + 2) + 14; }

    private int recentAt(double mx, double my) {
        if (my < recentY + 11 || my >= recentY + 11 + SLOT) return -1;
        int entryW = SnackRecipes.MAX_SEASONINGS * SLOT + 6;
        int i = (int) ((mx - gridX) / entryW);
        if (mx < gridX || i >= RecentMixes.get().size() || i >= recentCount()) return -1;
        return (mx - gridX) - i * entryW < SnackRecipes.MAX_SEASONINGS * SLOT ? i : -1;
    }

    private int recentCount() {
        int entryW = SnackRecipes.MAX_SEASONINGS * SLOT + 6;
        return Math.max(0, (rightX - PAD - gridX + 6) / entryW);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int c = candidateAt(mx, my);
        if (c >= 0) {
            add(shown.get(c).item(), hasShiftDown());
            return true;
        }
        int s = mixSlotAt(mx, my);
        if (s >= 0) {
            if (s < MIX.size()) {
                MIX.remove(s);
                updatePreview();
            }
            return true;
        }
        int r = recentAt(mx, my);
        if (r >= 0) {
            RecentMixes.Mix mix = RecentMixes.get().get(r);
            MIX.clear();
            MIX.addAll(mix.resolve().subList(0, Math.min(SnackRecipes.MAX_SEASONINGS, mix.resolve().size())));
            output = mix.output();
            quantity = mix.quantity();
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= gridX && mx < gridX + gridCols * SLOT && my >= gridY && my < gridY + gridRows * SLOT) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            create();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE8101018);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF5A5A72);
        g.fill(rightX - 4, panelY + PAD + 12, rightX - 3, bottomY - 4, 0xFF34344A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawString(font, title, panelX + PAD, panelY + PAD, 0xFFFFD27A);

        // seasoning grid
        for (int i = 0; i < gridCols * gridRows; i++) {
            int index = scroll * gridCols + i;
            if (index >= shown.size()) break;
            int x = gridX + (i % gridCols) * SLOT, y = gridY + (i / gridCols) * SLOT;
            slot(g, x, y, candidateAt(mx, my) == index);
            g.renderItem(shown.get(index).stack(), x + 1, y + 1);
        }
        if (maxScroll() > 0) {
            int trackX = gridX + gridCols * SLOT + 1, trackH = gridRows * SLOT;
            int barH = Math.max(8, trackH * gridRows / ((shown.size() + gridCols - 1) / gridCols));
            int barY = gridY + (trackH - barH) * scroll / maxScroll();
            g.fill(trackX, gridY, trackX + 2, gridY + trackH, 0xFF2A2A38);
            g.fill(trackX, barY, trackX + 2, barY + barH, 0xFFA0A0C0);
        }

        // mix slots, arrow and result
        g.drawString(font, Component.translatable("snacklab.mix"), rightX, mixY - 10, 0xFFB0B0C8);
        for (int i = 0; i < SnackRecipes.MAX_SEASONINGS; i++) {
            int x = rightX + i * (SLOT + 2);
            slot(g, x, mixY, mixSlotAt(mx, my) == i);
            if (i < MIX.size()) g.renderItem(new ItemStack(MIX.get(i)), x + 1, mixY + 1);
        }
        int rx = resultX();
        g.drawString(font, "→", rx - 11, mixY + 5, 0xFFD0D0E0);
        slot(g, rx, mixY, overResult(mx, my));
        g.renderItem(preview, rx + 1, mixY + 1);

        // effects of the mix, in Cobblemon's own words
        g.drawString(font, Component.translatable("snacklab.effects"), rightX, effectsY, 0xFFB0B0C8);
        int y = effectsY + 11;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (previewLines.isEmpty()) lines.addAll(font.split(Component.translatable("snacklab.no_effects"), rightW));
        for (Component line : previewLines) lines.addAll(font.split(line, rightW));
        for (FormattedCharSequence line : lines) {
            if (y + 9 > bottomY - 3) break;
            g.drawString(font, line, rightX, y, 0xFFE0E0E0);
            y += 10;
        }

        // recent mixes
        List<RecentMixes.Mix> recent = RecentMixes.get();
        if (!recent.isEmpty()) {
            g.drawString(font, Component.translatable("snacklab.recent"), gridX, recentY, 0xFFB0B0C8);
            int entryW = SnackRecipes.MAX_SEASONINGS * SLOT + 6;
            int hovered = recentAt(mx, my);
            for (int i = 0; i < Math.min(recent.size(), recentCount()); i++) {
                List<Item> items = recent.get(i).resolve();
                int x0 = gridX + i * entryW;
                g.fill(x0, recentY + 11, x0 + SnackRecipes.MAX_SEASONINGS * SLOT, recentY + 11 + SLOT, hovered == i ? 0xFF4A4A66 : 0xFF24242F);
                for (int k = 0; k < Math.min(items.size(), SnackRecipes.MAX_SEASONINGS); k++) {
                    g.renderItem(new ItemStack(items.get(k)), x0 + k * SLOT + 1, recentY + 12);
                }
            }
        }

        // status, or the help line
        boolean showStatus = status != null && System.currentTimeMillis() < statusUntil;
        Component bottom = showStatus ? status : Component.translatable("snacklab.help");
        int color = showStatus ? statusColor : 0xFF8080A0;
        List<FormattedCharSequence> bottomLines = font.split(bottom, panelW - 2 * PAD);
        if (!bottomLines.isEmpty()) g.drawString(font, bottomLines.get(0), panelX + PAD, bottomY, color);

        // tooltips last
        int c = candidateAt(mx, my);
        if (c >= 0) {
            SnackRecipes.Candidate cand = shown.get(c);
            List<Component> tip = new ArrayList<>();
            tip.add(cand.stack().getHoverName());
            tip.addAll(cand.effectLines());
            g.renderComponentTooltip(font, tip, mx, my);
        } else if (mixSlotAt(mx, my) >= 0 && mixSlotAt(mx, my) < MIX.size()) {
            g.renderTooltip(font, new ItemStack(MIX.get(mixSlotAt(mx, my))), mx, my);
        } else if (overResult(mx, my)) {
            g.renderTooltip(font, preview, mx, my);
        } else if (recentAt(mx, my) >= 0) {
            RecentMixes.Mix mix = recent.get(recentAt(mx, my));
            List<Component> tip = new ArrayList<>();
            tip.add(mix.output() == SnackRecipes.Output.BAIT ? SnackRecipes.Output.BAIT.item().getDescription() : SnackRecipes.Output.SNACK.item().getDescription());
            for (Item item : mix.resolve()) tip.add(Component.literal("• ").append(item.getDescription()));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private static void slot(GuiGraphics g, int x, int y, boolean hovered) {
        g.fill(x, y, x + SLOT, y + SLOT, 0xFF1C1C26);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hovered ? 0xFF5A5A7A : 0xFF33334A);
    }
}

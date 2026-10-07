package dev.lyricsoverlay.worldlyrics.client.gui;

import dev.lyricsoverlay.worldlyrics.Lang;
import dev.lyricsoverlay.worldlyrics.bridge.BridgeClient;
import dev.lyricsoverlay.worldlyrics.bridge.Snapshot;
import dev.lyricsoverlay.worldlyrics.client.ColorUtil;
import dev.lyricsoverlay.worldlyrics.client.Keybinds;
import dev.lyricsoverlay.worldlyrics.client.LyricsController;
import dev.lyricsoverlay.worldlyrics.config.Config;
import dev.lyricsoverlay.worldlyrics.config.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Экран настроек: вкладки с «желейным» индикатором, плавная прокрутка, ползунки,
 * переключатели, выбор из списка, цвета с образцом (клик по образцу — перебор палитры).
 * Все изменения применяются сразу, мир за экраном не ставится на паузу — видно результат.
 */
public final class SettingsScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int WIDGET_W = 150;
    private static final String[] PALETTE = {
            "FFFFFF", "C8C8C8", "8E8E8E", "4A4A4A", "000000",
            "FF5A7A", "FFB347", "FFE66D", "7CFF9B", "7CF3FF", "8B6CFF", "FF7CF0"};
    private static Config.Cat lastTab = Config.Cat.GENERAL;

    private final Screen parent;
    private Config.Cat tab = lastTab;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private int panelL;
    private int panelR;
    private int top;
    private int bottom;
    private int contentH;
    private double scroll;
    private double scrollTarget;
    private float slide;
    private float slideV;
    private float indX;
    private float indV;
    private float indW;
    private long lastNanos;
    private boolean dirty;

    private static final class Row {
        final Entry entry;
        final AbstractWidget widget;
        final int baseY;
        final int widgetX;

        Row(Entry entry, AbstractWidget widget, int baseY, int widgetX) {
            this.entry = entry;
            this.widget = widget;
            this.baseY = baseY;
            this.widgetX = widgetX;
        }
    }

    public SettingsScreen(Screen parent) {
        super(Component.literal("World Lyrics"));
        this.parent = parent;
    }

    // ================================================================ разметка

    @Override
    protected void init() {
        int panelW = Math.min(width - 24, 470);
        panelL = (width - panelW) / 2;
        panelR = panelL + panelW;
        top = 72;
        bottom = height - 36;

        tabButtons.clear();
        Config.Cat[] cats = Config.Cat.values();
        int tw = panelW / cats.length;
        for (int i = 0; i < cats.length; i++) {
            Config.Cat cat = cats[i];
            Button b = Button.builder(Component.literal(tabName(cat)), btn -> switchTab(cat))
                    .bounds(panelL + i * tw, 40, tw - 2, 18).build();
            tabButtons.add(addRenderableWidget(b));
        }
        int bw = Math.min(140, panelW / 3);
        addRenderableWidget(Button.builder(Component.literal(Lang.t("Сбросить вкладку", "Reset tab")), b -> resetTab())
                .bounds(panelL, height - 28, bw, 20)
                .tooltip(Tooltip.create(Component.literal(Lang.t("Вернуть значения по умолчанию на этой вкладке",
                        "Restore defaults on this tab")))).build());
        addRenderableWidget(Button.builder(Component.literal(Lang.t("Клавиши…", "Controls…")), b -> openControls())
                .bounds(panelL + panelW / 2 - bw / 2, height - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(Lang.t("Готово", "Done")), b -> onClose())
                .bounds(panelR - bw, height - 28, bw, 20).build());

        buildRows();
        Button sel = tabButtons.get(tab.ordinal());
        indX = sel.getX();
        indW = sel.getWidth();
        indV = 0;
        lastNanos = 0;
        styleTabs();
    }

    private static String tabName(Config.Cat c) {
        return switch (c) {
            case GENERAL -> Lang.t("Общее", "General");
            case TEXT -> Lang.t("Текст", "Text");
            case PLACEMENT -> Lang.t("Место", "Placement");
            case ANIMATION -> Lang.t("Анимация", "Animation");
            case EFFECTS -> Lang.t("Эффекты", "Effects");
            case CONNECTION -> Lang.t("Связь", "Bridge");
        };
    }

    private void styleTabs() {
        Config.Cat[] cats = Config.Cat.values();
        for (int i = 0; i < tabButtons.size(); i++) {
            Component name = Component.literal(tabName(cats[i]));
            tabButtons.get(i).setMessage(cats[i] == tab ? name.copy().withStyle(ChatFormatting.BOLD) : name);
        }
    }

    private void switchTab(Config.Cat cat) {
        if (cat == tab) {
            return;
        }
        int dir = cat.ordinal() > tab.ordinal() ? 1 : -1;
        tab = cat;
        lastTab = cat;
        buildRows();
        slide = 34f * dir;
        slideV = 0f;
        indV += dir * 260f;   // толчок для «желе» индикатора
        styleTabs();
    }

    private void buildRows() {
        for (Row r : rows) {
            removeWidget(r.widget);
        }
        rows.clear();
        int y = 0;
        int wx = panelR - 10 - WIDGET_W;
        for (Entry e : Config.ENTRIES) {
            if (e.cat != tab) {
                continue;
            }
            AbstractWidget w = makeWidget(e, wx);
            if (!e.tooltip().isEmpty()) {
                w.setTooltip(Tooltip.create(Component.literal(e.tooltip())));
            }
            addRenderableWidget(w);
            rows.add(new Row(e, w, y, e.type == Entry.Type.COLOR ? wx + 22 : wx));
            y += ROW_H;
        }
        contentH = y;
        scroll = scrollTarget = 0;
        layout();
    }

    @SuppressWarnings("unchecked")
    private AbstractWidget makeWidget(Entry e, int x) {
        Component label = Component.literal(e.label());
        switch (e.type) {
            case BOOL -> {
                return CycleButton.onOffBuilder((Boolean) e.value.get()).displayOnlyValue()
                        .create(x, 0, WIDGET_W, 20, label, (btn, v) -> {
                            e.setRaw(v);
                            changed(e);
                        });
            }
            case ENUM -> {
                Enum<?> cur = (Enum<?>) e.value.get();
                List<Integer> idx = IntStream.range(0, e.constants.length).boxed().toList();
                return CycleButton.<Integer>builder(i -> Component.literal(e.enumLabel(i)))
                        .withValues(idx).withInitialValue(cur.ordinal()).displayOnlyValue()
                        .create(x, 0, WIDGET_W, 20, label, (btn, v) -> {
                            e.setRaw(e.constants[v]);
                            changed(e);
                        });
            }
            case INT, DOUBLE -> {
                return new ValueSlider(x, 0, WIDGET_W, 20, e, this::changed);
            }
            case COLOR -> {
                EditBox box = new EditBox(font, x + 22, 0, WIDGET_W - 22, 20, label);
                box.setMaxLength(9);
                box.setValue((String) e.value.get());
                box.setResponder(s -> {
                    Integer c = ColorUtil.parseOrNull(s);
                    if (c != null) {
                        box.setTextColor(0xE0E0E0);
                        String norm = ColorUtil.format(c);
                        if (!norm.equals(e.value.get())) {
                            e.setRaw(norm);
                            changed(e);
                        }
                    } else {
                        box.setTextColor(0xFF6A6A);
                    }
                });
                return box;
            }
            default -> {
                EditBox box = new EditBox(font, x, 0, WIDGET_W, 20, label);
                box.setMaxLength(128);
                box.setValue(String.valueOf(e.value.get()));
                box.setResponder(s -> {
                    e.setRaw(s.trim());
                    changed(e);
                });
                return box;
            }
        }
    }

    private void changed(Entry e) {
        dirty = true;
        if (e.value == Config.MODE) {
            LyricsController.INSTANCE.onModeChanged();
        }
    }

    private void resetTab() {
        for (Entry e : Config.ENTRIES) {
            if (e.cat == tab) {
                e.reset();
                if (e.value == Config.MODE) {
                    LyricsController.INSTANCE.onModeChanged();
                }
            }
        }
        dirty = true;
        buildRows();
        slide = -16f;
    }

    private void openControls() {
        if (minecraft != null) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.controls.KeyBindsScreen(this, minecraft.options));
        }
    }

    // ================================================================ анимация

    private void animate() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (float) Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        int steps = Math.max(1, (int) (dt / 0.004f) + 1);
        float h = dt / steps;
        Button sel = tabButtons.isEmpty() ? null : tabButtons.get(tab.ordinal());
        float tx = sel == null ? 0 : sel.getX();
        float tw = sel == null ? 0 : sel.getWidth();
        for (int i = 0; i < steps; i++) {
            // контент: пружина с лёгким перелётом
            float a = -260f * slide - 2f * 0.55f * (float) Math.sqrt(260f) * slideV;
            slideV += a * h;
            slide += slideV * h;
            // индикатор вкладки: мягкое желе
            float ai = -300f * (indX - tx) - 2f * 0.4f * (float) Math.sqrt(300f) * indV;
            indV += ai * h;
            indX += indV * h;
        }
        indW += (tw - indW) * (1f - (float) Math.exp(-dt * 18));
        scroll += (scrollTarget - scroll) * (1.0 - Math.exp(-dt * 16));
        if (Math.abs(slide) < 0.05f && Math.abs(slideV) < 0.05f) {
            slide = 0;
            slideV = 0;
        }
    }

    private void layout() {
        int off = Math.round(slide);
        for (Row r : rows) {
            int y = top + r.baseY - (int) Math.round(scroll);
            r.widget.setX(r.widgetX + off);
            r.widget.setY(y + 2);
            r.widget.visible = y >= top - 1 && y + ROW_H <= bottom + 1;
        }
    }

    private int maxScroll() {
        return Math.max(0, contentH - (bottom - top));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= top && mouseY <= bottom) {
            scrollTarget = Mth.clamp(scrollTarget - delta * ROW_H * 1.5, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // клик по образцу цвета — следующий цвет палитры (прозрачность сохраняется)
        for (Row r : rows) {
            if (r.entry.type != Entry.Type.COLOR || !r.widget.visible) {
                continue;
            }
            int sx = r.widget.getX() - 22;
            int sy = r.widget.getY();
            if (mx >= sx && mx < sx + 18 && my >= sy + 1 && my < sy + 19) {
                int cur = ColorUtil.parse((String) r.entry.value.get(), 0xFFFFFFFF);
                String rgb = String.format("%06X", cur & 0xFFFFFF);
                int i = 0;
                for (int k = 0; k < PALETTE.length; k++) {
                    if (PALETTE[k].equals(rgb)) {
                        i = k + 1;
                        break;
                    }
                }
                if (button == 1) {
                    i = (i - 2 + PALETTE.length * 2) % PALETTE.length;
                }
                int next = (cur & 0xFF000000) | Integer.parseInt(PALETTE[i % PALETTE.length], 16);
                if ((next >>> 24) == 0) {
                    next |= 0xFF000000;
                }
                ((EditBox) r.widget).setValue(ColorUtil.format(next));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ================================================================ отрисовка

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        animate();
        layout();
        if (minecraft != null && minecraft.level != null) {
            g.fillGradient(0, 0, width, height, 0x90000000, 0xB0000000);
        } else {
            renderBackground(g);
        }
        int pl = panelL - 8;
        int pr = panelR + 8;
        g.fill(pl, 6, pr, height - 6, 0xC80C0C0C);
        g.fill(pl, 6, pr, 7, 0x30FFFFFF);
        g.fill(pl, height - 7, pr, height - 6, 0x18FFFFFF);
        g.fill(pl, 6, pl + 1, height - 6, 0x18FFFFFF);
        g.fill(pr - 1, 6, pr, height - 6, 0x18FFFFFF);

        // заголовок и статус
        g.drawString(font, Component.literal("WORLD LYRICS").withStyle(ChatFormatting.BOLD), panelL, 18, 0xFFFFFFFF, false);
        String status = statusLine();
        int sw = font.width(status);
        int maxSw = panelR - panelL - 110;
        if (sw > maxSw) {
            status = font.plainSubstrByWidth(status, maxSw - 6) + "…";
            sw = font.width(status);
        }
        g.drawString(font, status, panelR - sw, 18, statusColor(), false);

        // индикатор вкладки
        g.fill(Math.round(indX), 60, Math.round(indX + indW) - 2, 62, 0xFFFFFFFF);
        g.fill(panelL, 64, panelR, 65, 0x20FFFFFF);

        // строки
        g.enableScissor(panelL - 6, top, panelR + 6, bottom);
        int off = Math.round(slide);
        Row hovered = null;
        for (Row r : rows) {
            int y = top + r.baseY - (int) Math.round(scroll);
            if (y + ROW_H < top || y > bottom) {
                continue;
            }
            boolean hover = mx >= panelL && mx < panelR && my >= y && my < y + ROW_H && my >= top && my < bottom;
            if (hover) {
                g.fill(panelL - 4, y, panelR + 4, y + ROW_H, 0x12FFFFFF);
                hovered = r;
            }
            String label = r.entry.label();
            int maxLw = r.widget.getX() - (r.entry.type == Entry.Type.COLOR ? 22 : 0) - panelL - 14 - off;
            if (font.width(label) > maxLw) {
                label = font.plainSubstrByWidth(label, maxLw - 6) + "…";
            }
            g.drawString(font, label, panelL + 4 + off, y + 8, 0xFFE8E8E8, false);
            g.fill(panelL + 4, y + ROW_H - 1, panelR - 4, y + ROW_H, 0x10FFFFFF);
            if (r.entry.type == Entry.Type.COLOR) {
                int c = ColorUtil.parse((String) r.entry.value.get(), 0xFFFFFFFF);
                int sx = r.widget.getX() - 22;
                int sy = y + 3;
                for (int cy = 0; cy < 18; cy += 3) {   // «шахматка» под прозрачным цветом
                    for (int cx = 0; cx < 18; cx += 3) {
                        g.fill(sx + cx, sy + cy, sx + cx + 3, sy + cy + 3, ((cx + cy) / 3) % 2 == 0 ? 0xFF9A9A9A : 0xFF5A5A5A);
                    }
                }
                g.fill(sx, sy, sx + 18, sy + 18, c);
                g.fill(sx, sy, sx + 18, sy + 1, 0x60FFFFFF);
                g.fill(sx, sy + 17, sx + 18, sy + 18, 0x60000000);
            }
        }
        g.disableScissor();

        // плавные края области прокрутки
        if (scroll > 1) {
            g.fillGradient(panelL - 6, top, panelR + 6, top + 10, 0xC80C0C0C, 0x000C0C0C);
        }
        if (scroll < maxScroll() - 1) {
            g.fillGradient(panelL - 6, bottom - 10, panelR + 6, bottom, 0x000C0C0C, 0xC80C0C0C);
        }
        // полоса прокрутки
        int ms = maxScroll();
        if (ms > 0) {
            int trackH = bottom - top;
            int thumb = Math.max(18, trackH * trackH / contentH);
            int ty = top + (int) ((trackH - thumb) * (scroll / ms));
            g.fill(panelR + 3, ty, panelR + 5, ty + thumb, 0x80FFFFFF);
        }

        super.render(g, mx, my, pt);

        if (hovered != null && !hovered.entry.tooltip().isEmpty() && mx < hovered.widget.getX() - 24) {
            g.renderTooltip(font, font.split(Component.literal(hovered.entry.tooltip()), 220), mx, my);
        }
    }

    private String statusLine() {
        BridgeClient br = BridgeClient.INSTANCE;
        Snapshot s = br.snapshot();
        return switch (br.status()) {
            case OK -> s.hasTrack()
                    ? "● " + s.title() + (s.artist().isEmpty() ? "" : " — " + s.artist())
                    : Lang.t("● Подключено · ничего не играет", "● Connected · nothing playing");
            case NO_APP -> Lang.t("○ Нет связи с Lyrics Overlay (порт ", "○ No connection to Lyrics Overlay (port ")
                    + Config.PORT.get() + ")";
            case ERROR -> "○ " + br.error();
            case DISABLED -> Lang.t("○ Мод выключен (клавиша ", "○ Mod disabled (key ")
                    + Keybinds.TOGGLE.getTranslatedKeyMessage().getString() + ")";
            case CONNECTING -> Lang.t("… подключение", "… connecting");
        };
    }

    private int statusColor() {
        return switch (BridgeClient.INSTANCE.status()) {
            case OK -> 0xFFDADADA;
            case CONNECTING, DISABLED -> 0xFF9A9A9A;
            default -> 0xFFFF8A8A;
        };
    }

    // ================================================================ закрытие

    @Override
    public void onClose() {
        save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        save();
        super.removed();
    }

    private void save() {
        if (dirty) {
            Config.save();
            dirty = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

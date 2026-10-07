package dev.lyricsoverlay.worldlyrics.client.gui;

import dev.lyricsoverlay.worldlyrics.config.Entry;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Ползунок для числовой настройки с шагом, единицами и живым применением. */
final class ValueSlider extends AbstractSliderButton {
    private final Entry entry;
    private final Consumer<Entry> onChange;

    ValueSlider(int x, int y, int w, int h, Entry entry, Consumer<Entry> onChange) {
        super(x, y, w, h, Component.empty(), norm(entry, entry.numeric()));
        this.entry = entry;
        this.onChange = onChange;
        updateMessage();
    }

    private static double norm(Entry e, double v) {
        double range = e.max - e.min;
        return range <= 0 ? 0 : Math.max(0, Math.min(1, (v - e.min) / range));
    }

    private double snapped() {
        double raw = entry.min + value * (entry.max - entry.min);
        if (entry.step > 0) {
            raw = entry.min + Math.round((raw - entry.min) / entry.step) * entry.step;
        }
        return Math.max(entry.min, Math.min(entry.max, raw));
    }

    @Override
    protected void updateMessage() {
        if (entry != null) {
            setMessage(Component.literal(entry.format(snapped())));
        }
    }

    @Override
    protected void applyValue() {
        entry.setNumeric(snapped());
        onChange.accept(entry);
    }

    void refresh() {
        value = norm(entry, entry.numeric());
        updateMessage();
    }
}

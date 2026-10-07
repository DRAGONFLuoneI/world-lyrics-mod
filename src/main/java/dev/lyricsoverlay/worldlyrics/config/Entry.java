package dev.lyricsoverlay.worldlyrics.config;

import dev.lyricsoverlay.worldlyrics.Lang;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;

/** Описание одной настройки для экрана настроек: тип, подписи, диапазон, формат. */
public final class Entry {
    public enum Type { BOOL, INT, DOUBLE, ENUM, COLOR, STRING }

    public final Config.Cat cat;
    public final Type type;
    public final ForgeConfigSpec.ConfigValue<?> value;
    private final String ru;
    private final String en;
    private final String tipRu;
    private final String tipEn;

    public double min;
    public double max;
    public double step = 1;
    public double mul = 1;
    public int decimals;
    public String suffixRu = "";
    public String suffixEn = "";
    public String[] enumRu;
    public String[] enumEn;
    public Enum<?>[] constants;

    Entry(Config.Cat cat, Type type, ForgeConfigSpec.ConfigValue<?> value, String ru, String en, String tipRu, String tipEn) {
        this.cat = cat;
        this.type = type;
        this.value = value;
        this.ru = ru;
        this.en = en;
        this.tipRu = tipRu;
        this.tipEn = tipEn;
    }

    public String label() {
        return Lang.t(ru, en);
    }

    public String tooltip() {
        return Lang.t(tipRu, tipEn);
    }

    public String enumLabel(int i) {
        String[] arr = Lang.ru() ? enumRu : enumEn;
        return arr != null && i >= 0 && i < arr.length ? arr[i] : String.valueOf(constants[i]);
    }

    public double numeric() {
        Object v = value.get();
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    public String format(double v) {
        double shown = v * mul;
        String num = decimals <= 0 ? String.valueOf(Math.round(shown))
                : String.format(Locale.ROOT, "%." + decimals + "f", shown);
        return num + (Lang.ru() ? suffixRu : suffixEn);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setNumeric(double v) {
        double q = step > 0 ? min + Math.round((v - min) / step) * step : v;
        q = Math.max(min, Math.min(max, q));
        if (type == Type.INT) {
            ((ForgeConfigSpec.ConfigValue) value).set((int) Math.round(q));
        } else {
            ((ForgeConfigSpec.ConfigValue) value).set(Math.round(q * 1e6) / 1e6);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setRaw(Object v) {
        ((ForgeConfigSpec.ConfigValue) value).set(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void reset() {
        ForgeConfigSpec.ConfigValue raw = value;
        raw.set(raw.getDefault());
    }
}

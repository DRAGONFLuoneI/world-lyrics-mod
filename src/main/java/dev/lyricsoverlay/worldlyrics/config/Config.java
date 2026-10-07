package dev.lyricsoverlay.worldlyrics.config;

import dev.lyricsoverlay.worldlyrics.client.ColorUtil;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Все настройки мода (файл config/worldlyrics-client.toml).
 * Каждая настройка одновременно попадает в {@link #ENTRIES} — из этого списка строится экран настроек.
 */
public final class Config {
    private Config() {
    }

    public enum Cat { GENERAL, TEXT, PLACEMENT, ANIMATION, EFFECTS, CONNECTION }

    public enum Mode { FOLLOW, SCATTER, ANCHORED, HUD }

    public enum Karaoke { FILL, LINE, NONE }

    public enum Appear { FADE, POP, WAVE, TYPEWRITER, RISE }

    public enum Disappear { FADE, FLOAT_UP, SHRINK, DISSOLVE }

    public enum Particles { NONE, NOTE, END_ROD, ENCHANT, GLOW, CHERRY, SPARK, WAX, SNOW, SOUL, HAPPY, FIREWORK }

    public enum HudPos { BOTTOM, TOP }

    private static final List<Entry> ALL = new ArrayList<>();
    public static final List<Entry> ENTRIES = Collections.unmodifiableList(ALL);
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static Cat cat = Cat.GENERAL;
    private static boolean firstSection = true;

    // ------------------------------------------------------------ общее
    public static final BooleanValue ENABLED;
    public static final EnumValue<Mode> MODE;
    public static final BooleanValue SHOW_HUD;
    public static final BooleanValue HIDE_WHEN_PAUSED;
    public static final IntValue PAUSED_HIDE_DELAY;
    public static final BooleanValue HIDE_WITH_F1;
    public static final BooleanValue HIDE_IN_MENUS;
    public static final BooleanValue SHOW_STATUS;
    public static final DoubleValue MAX_DISTANCE;

    // ------------------------------------------------------------ текст
    public static final DoubleValue SCALE;
    public static final IntValue LINE_COUNT;
    public static final DoubleValue LINE_SPACING;
    public static final DoubleValue INACTIVE_SCALE;
    public static final DoubleValue INACTIVE_ALPHA;
    public static final IntValue MAX_WIDTH;
    public static final BooleanValue UPPERCASE;
    public static final BooleanValue SHOW_NOTE;
    public static final EnumValue<Karaoke> KARAOKE;
    public static final ConfigValue<String> COLOR_UNSUNG;
    public static final ConfigValue<String> COLOR_SUNG;
    public static final ConfigValue<String> COLOR_INACTIVE;
    public static final BooleanValue RAINBOW;
    public static final DoubleValue RAINBOW_SPEED;
    public static final BooleanValue SHADOW;
    public static final BooleanValue OUTLINE;
    public static final ConfigValue<String> OUTLINE_COLOR;
    public static final BooleanValue GLOW;
    public static final ConfigValue<String> GLOW_COLOR;
    public static final DoubleValue GLOW_SIZE;
    public static final BooleanValue BACKGROUND;
    public static final ConfigValue<String> BG_COLOR;
    public static final IntValue BG_PADDING;
    public static final BooleanValue SEE_THROUGH;
    public static final BooleanValue FULL_BRIGHT;

    // ------------------------------------------------------------ размещение
    public static final DoubleValue FOLLOW_DISTANCE;
    public static final DoubleValue FOLLOW_HEIGHT;
    public static final DoubleValue FOLLOW_SIDE;
    public static final DoubleValue FOLLOW_STIFFNESS;
    public static final DoubleValue FOLLOW_BOUNCE;
    public static final BooleanValue FOLLOW_PITCH;
    public static final DoubleValue SCATTER_MIN;
    public static final DoubleValue SCATTER_MAX;
    public static final IntValue SCATTER_SPREAD;
    public static final DoubleValue SCATTER_H_MIN;
    public static final DoubleValue SCATTER_H_MAX;
    public static final BooleanValue SCATTER_GROUND;
    public static final DoubleValue SCATTER_GROUND_HEIGHT;
    public static final BooleanValue SCATTER_VISIBLE;
    public static final IntValue SCATTER_KEEP;
    public static final IntValue SCATTER_LIFETIME;
    public static final BooleanValue BILLBOARD;
    public static final BooleanValue SCREEN_SIZE;
    public static final DoubleValue ANCHOR_HEIGHT;
    public static final EnumValue<HudPos> HUD_POS;
    public static final DoubleValue HUD_SCALE;
    public static final IntValue HUD_LINES;
    public static final IntValue HUD_MARGIN;

    // ------------------------------------------------------------ анимация
    public static final EnumValue<Appear> APPEAR;
    public static final DoubleValue APPEAR_TIME;
    public static final EnumValue<Disappear> DISAPPEAR;
    public static final DoubleValue DISAPPEAR_TIME;
    public static final DoubleValue WAVE_STAGGER;
    public static final DoubleValue POP_BOUNCE;
    public static final DoubleValue LINE_STIFFNESS;
    public static final DoubleValue LINE_BOUNCE;
    public static final BooleanValue BOB;
    public static final DoubleValue BOB_AMOUNT;
    public static final DoubleValue BOB_SPEED;
    public static final BooleanValue SWAY;
    public static final DoubleValue SWAY_DEG;
    public static final BooleanValue BEAT_PULSE;
    public static final DoubleValue BEAT_PULSE_AMOUNT;

    // ------------------------------------------------------------ эффекты
    public static final EnumValue<Particles> PARTICLES;
    public static final IntValue PARTICLE_COUNT;
    public static final BooleanValue TRAIL;
    public static final DoubleValue TRAIL_RATE;
    public static final BooleanValue TITLE_CARD;
    public static final DoubleValue TITLE_TIME;
    public static final BooleanValue TITLE_IN_HUD;
    public static final DoubleValue FOV_PULSE;
    public static final BooleanValue VIGNETTE;
    public static final ConfigValue<String> VIGNETTE_COLOR;
    public static final DoubleValue VIGNETTE_STRENGTH;
    public static final BooleanValue CAMERA_SHAKE;
    public static final DoubleValue CAMERA_SHAKE_AMOUNT;

    // ------------------------------------------------------------ связь
    public static final ConfigValue<String> HOST;
    public static final IntValue PORT;
    public static final IntValue POLL_MS;
    public static final IntValue OFFSET_MS;

    public static final ForgeConfigSpec SPEC;

    static {
        // ============================================================ GENERAL
        section(Cat.GENERAL, "general");
        ENABLED = bool("enabled", true, "Показывать тексты", "Show lyrics",
                "Главный выключатель мода. Клавиша по умолчанию — K.", "Master switch. Default key: K.");
        MODE = enm("mode", Mode.FOLLOW, Mode.class, "Режим", "Mode",
                new String[]{"Перед камерой", "Разбросано в мире", "Закреплено на месте", "Только HUD"},
                new String[]{"In front of camera", "Scattered in world", "Anchored in place", "HUD only"},
                "Где появляется текст. «Разбросано» — каждая строка возникает в новом месте в поле зрения.",
                "Where lyrics appear. Scattered: each line pops up at a new spot in your view.");
        SHOW_HUD = bool("showHud", false, "Дублировать в HUD", "Also show in HUD",
                "Дополнительно показывать строки внизу или вверху экрана.", "Also draw lines on the screen HUD.");
        HIDE_WHEN_PAUSED = bool("hideWhenPaused", true, "Скрывать на паузе", "Hide when paused",
                "Плавно убирать текст, когда музыка на паузе.", "Fade lyrics out while music is paused.");
        PAUSED_HIDE_DELAY = integer("pausedHideDelay", 3, 0, 60, 1, " с", " s", "Задержка скрытия", "Hide delay",
                "Через сколько секунд паузы скрывать текст.", "Seconds of pause before hiding.");
        HIDE_WITH_F1 = bool("hideWithF1", true, "Скрывать по F1", "Hide with F1",
                "Прятать текст вместе с интерфейсом (F1).", "Hide together with the GUI (F1).");
        HIDE_IN_MENUS = bool("hideInMenus", false, "Скрывать в меню", "Hide in menus",
                "Не показывать текст, когда открыт инвентарь или другое меню (кроме настроек мода).",
                "Hide while inventory or other menus are open (except this mod's settings).");
        SHOW_STATUS = bool("showStatus", true, "Сообщения о связи", "Connection messages",
                "Короткие подсказки «Подключено», «Нет связи с Lyrics Overlay» и т.п.",
                "Short toasts like 'Connected' or 'No connection to Lyrics Overlay'.");
        MAX_DISTANCE = dbl("maxDistance", 64, 8, 256, 1, 1, 0, " бл.", " bl.", "Дальность прорисовки", "Render distance",
                "Строки дальше этого расстояния от камеры не рисуются.", "Lines farther than this are not drawn.");

        // ============================================================ TEXT
        section(Cat.TEXT, "text");
        SCALE = dbl("scale", 1.0, 0.2, 4.0, 0.05, 100, 0, "%", "%", "Размер текста", "Text size",
                "Общий масштаб текста в мире.", "Overall size of the text in the world.");
        LINE_COUNT = integer("lineCount", 3, 1, 7, 1, "", "", "Строк в панели", "Lines in panel",
                "Сколько строк видно в режимах «Перед камерой» и «Закреплено» (текущая + соседние).",
                "How many lines the panel shows (current + neighbours).");
        LINE_SPACING = dbl("lineSpacing", 1.25, 0.8, 3.0, 0.05, 1, 2, "×", "×", "Межстрочный интервал", "Line spacing",
                "Расстояние между строками.", "Distance between lines.");
        INACTIVE_SCALE = dbl("inactiveScale", 0.75, 0.3, 1.0, 0.01, 100, 0, "%", "%", "Размер соседних строк", "Neighbour size",
                "Насколько меньше соседние строки относительно текущей.", "Size of neighbour lines relative to the current one.");
        INACTIVE_ALPHA = dbl("inactiveAlpha", 0.5, 0.05, 1.0, 0.01, 100, 0, "%", "%", "Яркость соседних строк", "Neighbour opacity",
                "Прозрачность прошлых и будущих строк.", "Opacity of previous and next lines.");
        MAX_WIDTH = integer("maxWidth", 240, 80, 800, 10, " px", " px", "Максимальная ширина", "Max width",
                "Длинные строки переносятся на несколько рядов.", "Long lines wrap into several rows.");
        UPPERCASE = bool("uppercase", false, "ЗАГЛАВНЫЕ БУКВЫ", "UPPERCASE", "Писать всё заглавными.", "Render in upper case.");
        SHOW_NOTE = bool("showNote", true, "♪ в проигрышах", "♪ in breaks",
                "Показывать ноту, когда в песне нет слов.", "Show a note during instrumental breaks.");
        KARAOKE = enm("karaoke", Karaoke.FILL, Karaoke.class, "Подсветка пения", "Karaoke highlight",
                new String[]{"Заливка по буквам", "Вся строка", "Без подсветки"},
                new String[]{"Letter fill", "Whole line", "No highlight"},
                "Как подсвечивается пение: буквы заливаются цветом в такт песне.",
                "How singing is highlighted: letters fill with colour in time.");
        COLOR_UNSUNG = color("colorUnsung", "#FF8E8E8E", "Цвет: ещё не спето", "Colour: not sung yet",
                "Цвет букв, которые ещё не спеты.", "Colour of letters not sung yet.");
        COLOR_SUNG = color("colorSung", "#FFFFFFFF", "Цвет: спето", "Colour: sung",
                "Цвет спетых букв.", "Colour of sung letters.");
        COLOR_INACTIVE = color("colorInactive", "#FFC8C8C8", "Цвет соседних строк", "Neighbour colour",
                "Цвет прошлых и будущих строк.", "Colour of previous and next lines.");
        RAINBOW = bool("rainbow", false, "Радужная заливка", "Rainbow fill",
                "Спетые буквы переливаются цветами.", "Sung letters cycle through colours.");
        RAINBOW_SPEED = dbl("rainbowSpeed", 0.25, 0.02, 2.0, 0.01, 1, 2, "", "", "Скорость радуги", "Rainbow speed",
                "Сколько оборотов цветового круга в секунду.", "Hue cycles per second.");
        SHADOW = bool("shadow", true, "Тень", "Shadow", "Классическая тень Minecraft под буквами.", "Vanilla drop shadow.");
        OUTLINE = bool("outline", true, "Обводка", "Outline", "Контур вокруг букв — читается на любом фоне.",
                "Outline around letters — readable on any background.");
        OUTLINE_COLOR = color("outlineColor", "#C0000000", "Цвет обводки", "Outline colour", "", "");
        GLOW = bool("glow", true, "Свечение", "Glow", "Мягкое свечение вокруг текущей строки.",
                "Soft glow around the current line.");
        GLOW_COLOR = color("glowColor", "#50FFFFFF", "Цвет свечения", "Glow colour", "", "");
        GLOW_SIZE = dbl("glowSize", 1.5, 0.5, 4.0, 0.1, 1, 1, " px", " px", "Размер свечения", "Glow size", "", "");
        BACKGROUND = bool("background", false, "Подложка", "Background plate",
                "Полупрозрачный прямоугольник за текстом.", "Semi-transparent plate behind the text.");
        BG_COLOR = color("bgColor", "#80000000", "Цвет подложки", "Plate colour", "", "");
        BG_PADDING = integer("bgPadding", 4, 0, 20, 1, " px", " px", "Отступ подложки", "Plate padding", "", "");
        SEE_THROUGH = bool("seeThrough", false, "Видно сквозь блоки", "Visible through blocks",
                "Текст не прячется за стенами и горами.", "Text is not hidden behind blocks.");
        FULL_BRIGHT = bool("fullBright", true, "Всегда ярко", "Always bright",
                "Не зависеть от освещения мира (видно ночью и в пещерах).", "Ignore world lighting (visible at night).");

        // ============================================================ PLACEMENT
        section(Cat.PLACEMENT, "placement");
        FOLLOW_DISTANCE = dbl("followDistance", 4.0, 1.0, 16.0, 0.1, 1, 1, " бл.", " bl.", "Перед камерой: расстояние",
                "Front: distance", "Как далеко перед глазами висит панель.", "How far in front of the eyes the panel floats.");
        FOLLOW_HEIGHT = dbl("followHeight", -0.9, -4.0, 4.0, 0.05, 1, 2, " бл.", " bl.", "Перед камерой: высота",
                "Front: height", "Смещение вверх/вниз от центра взгляда.", "Up/down offset from the view centre.");
        FOLLOW_SIDE = dbl("followSide", 0.0, -6.0, 6.0, 0.05, 1, 2, " бл.", " bl.", "Перед камерой: сбоку",
                "Front: sideways", "Смещение влево/вправо.", "Left/right offset.");
        FOLLOW_STIFFNESS = dbl("followStiffness", 40, 4, 300, 1, 1, 0, "", "", "Инерция следования", "Follow stiffness",
                "Меньше — панель плавно догоняет взгляд, больше — держится жёстко.",
                "Lower — the panel lazily catches up with your view, higher — sticks tightly.");
        FOLLOW_BOUNCE = dbl("followBounce", 0.35, 0.0, 1.0, 0.01, 100, 0, "%", "%", "Упругость следования", "Follow bounce",
                "Желейный отскок панели, когда вы резко поворачиваетесь.", "Jelly overshoot when you turn quickly.");
        FOLLOW_PITCH = bool("followPitch", true, "Следовать за наклоном", "Follow pitch",
                "Панель поднимается и опускается вместе со взглядом.", "Panel moves up/down with your look.");
        SCATTER_MIN = dbl("scatterMin", 6, 2, 48, 0.5, 1, 1, " бл.", " bl.", "В мире: мин. расстояние", "World: min distance",
                "Ближайшее расстояние, на котором появится строка.", "Nearest spawn distance.");
        SCATTER_MAX = dbl("scatterMax", 14, 2, 64, 0.5, 1, 1, " бл.", " bl.", "В мире: макс. расстояние", "World: max distance",
                "Самое дальнее расстояние для новой строки.", "Farthest spawn distance.");
        SCATTER_SPREAD = integer("scatterSpread", 70, 0, 170, 5, "°", "°", "В мире: угол разброса", "World: spread angle",
                "Насколько далеко от центра взгляда могут появляться строки.", "How far from the view centre lines may appear.");
        SCATTER_H_MIN = dbl("scatterHeightMin", -0.5, -8, 8, 0.25, 1, 2, " бл.", " bl.", "В мире: высота от", "World: height from",
                "Высота относительно глаз (или земли, если включено «На земле»).", "Height relative to eyes (or ground).");
        SCATTER_H_MAX = dbl("scatterHeightMax", 2.5, -8, 12, 0.25, 1, 2, " бл.", " bl.", "В мире: высота до", "World: height to", "", "");
        SCATTER_GROUND = bool("scatterGround", true, "В мире: опирать на землю", "World: snap to ground",
                "Строки появляются над поверхностью, а не висят в пустоте.", "Lines appear above the terrain.");
        SCATTER_GROUND_HEIGHT = dbl("scatterGroundHeight", 1.4, 0.2, 8, 0.1, 1, 1, " бл.", " bl.", "Высота над землёй",
                "Height above ground", "", "");
        SCATTER_VISIBLE = bool("scatterVisibleOnly", true, "Только в прямой видимости", "Line of sight only",
                "Не ставить строку за стеной или горой.", "Never place a line behind walls.");
        SCATTER_KEEP = integer("scatterKeep", 3, 0, 12, 1, "", "", "Сколько прошлых строк оставлять", "Previous lines kept",
                "Старые строки остаются висеть в мире и постепенно исчезают.", "Old lines stay in the world and fade later.");
        SCATTER_LIFETIME = integer("scatterLifetime", 14, 2, 120, 1, " с", " s", "Время жизни строки", "Line lifetime",
                "Через сколько секунд строка в мире исчезает в любом случае.", "Max time a world line stays.");
        BILLBOARD = bool("billboard", true, "Всегда лицом к камере", "Always face camera",
                "Иначе строка остаётся повёрнутой туда, откуда вы смотрели в момент появления.",
                "Otherwise a line keeps the orientation it spawned with.");
        SCREEN_SIZE = bool("constantScreenSize", false, "Одинаковый размер на экране", "Constant screen size",
                "Дальние строки увеличиваются, чтобы читаться так же, как ближние.", "Far lines are scaled up to stay readable.");
        ANCHOR_HEIGHT = dbl("anchorHeight", 2.0, -2, 12, 0.1, 1, 1, " бл.", " bl.", "Закреплено: высота", "Anchored: height",
                "Высота панели над точкой закрепления. Закрепить — клавиша H.", "Height above the anchor. Re-anchor: key H.");
        HUD_POS = enm("hudPosition", HudPos.BOTTOM, HudPos.class, "HUD: положение", "HUD: position", new String[]{"Снизу", "Сверху"},
                new String[]{"Bottom", "Top"}, "Где на экране HUD-строки.", "Where HUD lines are drawn.");
        HUD_SCALE = dbl("hudScale", 1.5, 0.5, 4.0, 0.05, 100, 0, "%", "%", "HUD: размер", "HUD: size", "", "");
        HUD_LINES = integer("hudLines", 2, 1, 5, 1, "", "", "HUD: строк", "HUD: lines", "", "");
        HUD_MARGIN = integer("hudMargin", 56, 0, 200, 2, " px", " px", "HUD: отступ от края", "HUD: margin",
                "Чтобы не перекрывать хотбар.", "Keep clear of the hotbar.");

        // ============================================================ ANIMATION
        section(Cat.ANIMATION, "animation");
        APPEAR = enm("appear", Appear.WAVE, Appear.class, "Появление строки", "Line appear",
                new String[]{"Проявление", "Пружинка", "Волна по буквам", "Печатная машинка", "Подъём снизу"},
                new String[]{"Fade", "Pop", "Letter wave", "Typewriter", "Rise"},
                "Как появляется новая строка.", "How a new line appears.");
        APPEAR_TIME = dbl("appearTime", 0.45, 0.05, 2.0, 0.05, 1, 2, " с", " s", "Длительность появления", "Appear time", "", "");
        DISAPPEAR = enm("disappear", Disappear.FLOAT_UP, Disappear.class, "Исчезновение строки", "Line disappear",
                new String[]{"Растворение", "Улетает вверх", "Сжатие", "Рассыпается"},
                new String[]{"Fade", "Float up", "Shrink", "Dissolve"},
                "Как исчезают строки в мире.", "How world lines vanish.");
        DISAPPEAR_TIME = dbl("disappearTime", 0.9, 0.05, 3.0, 0.05, 1, 2, " с", " s", "Длительность исчезновения",
                "Disappear time", "", "");
        WAVE_STAGGER = dbl("waveStagger", 0.03, 0.0, 0.15, 0.005, 1000, 0, " мс", " ms", "Волна: задержка между буквами",
                "Wave: letter delay", "", "");
        POP_BOUNCE = dbl("popBounce", 1.2, 0.0, 3.0, 0.05, 1, 2, "", "", "Пружинка: упругость", "Pop: bounce",
                "Насколько сильно строка «выпрыгивает».", "How strongly the line overshoots.");
        LINE_STIFFNESS = dbl("lineStiffness", 140, 20, 500, 5, 1, 0, "", "", "Прокрутка строк: жёсткость", "Scroll stiffness",
                "Скорость прокрутки панели к следующей строке.", "How fast the panel scrolls to the next line.");
        LINE_BOUNCE = dbl("lineBounce", 0.5, 0.0, 1.2, 0.01, 1, 2, "", "", "Прокрутка строк: желе", "Scroll jelly",
                "0 — без отскока, 1 — заметно пружинит.", "0 — no overshoot, 1 — noticeably springy.");
        BOB = bool("bob", true, "Покачивание", "Bobbing", "Строки мягко парят вверх-вниз.", "Lines gently float up and down.");
        BOB_AMOUNT = dbl("bobAmount", 0.08, 0.0, 0.6, 0.01, 1, 2, " бл.", " bl.", "Амплитуда покачивания", "Bob amount", "", "");
        BOB_SPEED = dbl("bobSpeed", 1.2, 0.1, 5.0, 0.05, 1, 2, "", "", "Скорость покачивания", "Bob speed", "", "");
        SWAY = bool("sway", false, "Наклон из стороны в сторону", "Sway", "Лёгкое покачивание строк по кругу.",
                "Slight rotational sway.");
        SWAY_DEG = dbl("swayDegrees", 3, 0, 20, 0.5, 1, 1, "°", "°", "Угол наклона", "Sway angle", "", "");
        BEAT_PULSE = bool("linePulse", true, "Пульс на новой строке", "Pulse on new line",
                "Текущая строка коротко «вздрагивает», когда начинается.", "The current line briefly pulses when it starts.");
        BEAT_PULSE_AMOUNT = dbl("linePulseAmount", 0.12, 0.0, 0.5, 0.01, 100, 0, "%", "%", "Сила пульса", "Pulse amount", "", "");

        // ============================================================ EFFECTS
        section(Cat.EFFECTS, "effects");
        PARTICLES = enm("particles", Particles.NOTE, Particles.class, "Частицы", "Particles",
                new String[]{"Нет", "Ноты", "Энд-стержень", "Руны чар", "Светящиеся", "Лепестки вишни", "Искры",
                        "Воск", "Снежинки", "Огни душ", "Звёздочки", "Фейерверк"},
                new String[]{"None", "Notes", "End rod", "Enchant runes", "Glow", "Cherry petals", "Sparks",
                        "Wax", "Snowflakes", "Soul flames", "Happy stars", "Firework"},
                "Частицы, которые разлетаются, когда появляется новая строка.", "Particles burst when a new line appears.");
        PARTICLE_COUNT = integer("particleCount", 10, 0, 80, 1, "", "", "Количество частиц", "Particle count", "", "");
        TRAIL = bool("trail", false, "Частицы вокруг текущей строки", "Particles around current line",
                "Пока строка поётся, вокруг неё понемногу появляются частицы.", "Particles slowly drift around the sung line.");
        TRAIL_RATE = dbl("trailRate", 3, 0.5, 30, 0.5, 1, 1, "/с", "/s", "Частота частиц", "Particle rate", "", "");
        TITLE_CARD = bool("titleCard", true, "Титр при смене трека", "Title card on track change",
                "Название и исполнитель крупно появляются, когда начинается новая песня.",
                "Big title and artist appear when a new song starts.");
        TITLE_TIME = dbl("titleTime", 4.0, 1.0, 15.0, 0.5, 1, 1, " с", " s", "Длительность титра", "Title duration", "", "");
        TITLE_IN_HUD = bool("titleInHud", false, "Титр на экране", "Title on screen",
                "Показывать титр в интерфейсе, а не в мире.", "Show the title on the HUD instead of in the world.");
        FOV_PULSE = dbl("fovPulse", 0.015, 0.0, 0.08, 0.001, 100, 1, "%", "%", "Пульс поля зрения", "FOV pulse",
                "Камера чуть «вдыхает» на каждой новой строке. 0 — выключено.", "Camera FOV breathes on each new line. 0 — off.");
        VIGNETTE = bool("vignette", true, "Вспышка по краям экрана", "Edge flash",
                "Мягкая вспышка по краям экрана на новой строке.", "Soft flash at screen edges on a new line.");
        VIGNETTE_COLOR = color("vignetteColor", "#FFFFFFFF", "Цвет вспышки", "Flash colour", "", "");
        VIGNETTE_STRENGTH = dbl("vignetteStrength", 0.18, 0.0, 1.0, 0.01, 100, 0, "%", "%", "Сила вспышки", "Flash strength", "", "");
        CAMERA_SHAKE = bool("cameraShake", false, "Лёгкая тряска камеры", "Camera shake",
                "Едва заметный толчок камеры на новой строке.", "A subtle camera nudge on each new line.");
        CAMERA_SHAKE_AMOUNT = dbl("cameraShakeAmount", 0.6, 0.0, 4.0, 0.05, 1, 2, "°", "°", "Сила тряски", "Shake amount", "", "");

        // ============================================================ CONNECTION
        section(Cat.CONNECTION, "connection");
        HOST = string("host", "127.0.0.1", "Адрес Lyrics Overlay", "Lyrics Overlay host",
                "Обычно 127.0.0.1 — программа на этом же компьютере.", "Usually 127.0.0.1 — the app on this PC.");
        PORT = integer("port", 47811, 1024, 65535, 1, "", "", "Порт", "Port",
                "Обычно не нужно трогать: если порт занят, программа берёт другой, и мод находит его сам.",
                "Usually leave as is: if busy, the app picks another port and the mod finds it automatically.");
        POLL_MS = integer("pollMs", 250, 100, 2000, 50, " мс", " ms", "Частота опроса", "Poll interval",
                "Как часто спрашивать программу о треке. Время между опросами мод досчитывает сам.",
                "How often to ask the app. The mod interpolates time in between.");
        OFFSET_MS = integer("offsetMs", 0, -5000, 5000, 50, " мс", " ms", "Сдвиг в игре", "In-game offset",
                "Дополнительный сдвиг только для Minecraft. Плюс — текст раньше.", "Extra offset for Minecraft only. Plus — earlier.");
        B.pop();

        SPEC = B.build();
    }

    // ------------------------------------------------------------ помощники
    private static void section(Cat c, String path) {
        if (!firstSection) {
            B.pop();
        }
        firstSection = false;
        cat = c;
        B.push(path);
    }

    private static Entry add(Entry e) {
        ALL.add(e);
        return e;
    }

    private static BooleanValue bool(String key, boolean def, String ru, String en, String tipRu, String tipEn) {
        BooleanValue v = B.comment(tipEn.isEmpty() ? en : tipEn).define(key, def);
        add(new Entry(cat, Entry.Type.BOOL, v, ru, en, tipRu, tipEn));
        return v;
    }

    private static IntValue integer(String key, int def, int min, int max, int step, String sufRu, String sufEn,
                                    String ru, String en, String tipRu, String tipEn) {
        IntValue v = B.comment(tipEn.isEmpty() ? en : tipEn).defineInRange(key, def, min, max);
        Entry e = add(new Entry(cat, Entry.Type.INT, v, ru, en, tipRu, tipEn));
        e.min = min;
        e.max = max;
        e.step = step;
        e.suffixRu = sufRu;
        e.suffixEn = sufEn;
        return v;
    }

    private static DoubleValue dbl(String key, double def, double min, double max, double step, double mul, int decimals,
                                   String sufRu, String sufEn, String ru, String en, String tipRu, String tipEn) {
        DoubleValue v = B.comment(tipEn.isEmpty() ? en : tipEn).defineInRange(key, def, min, max);
        Entry e = add(new Entry(cat, Entry.Type.DOUBLE, v, ru, en, tipRu, tipEn));
        e.min = min;
        e.max = max;
        e.step = step;
        e.mul = mul;
        e.decimals = decimals;
        e.suffixRu = sufRu;
        e.suffixEn = sufEn;
        return v;
    }

    private static <E extends Enum<E>> EnumValue<E> enm(String key, E def, Class<E> cls, String ru, String en,
                                                         String[] labelsRu, String[] labelsEn, String tipRu, String tipEn) {
        EnumValue<E> v = B.comment(tipEn).defineEnum(key, def);
        Entry e = add(new Entry(cat, Entry.Type.ENUM, v, ru, en, tipRu, tipEn));
        e.constants = cls.getEnumConstants();
        e.enumRu = labelsRu;
        e.enumEn = labelsEn;
        return v;
    }

    private static ConfigValue<String> color(String key, String def, String ru, String en, String tipRu, String tipEn) {
        ConfigValue<String> v = B.comment((tipEn.isEmpty() ? en : tipEn) + " (#AARRGGBB)")
                .define(key, def, o -> o instanceof String s && ColorUtil.isValid(s));
        add(new Entry(cat, Entry.Type.COLOR, v, ru, en,
                tipRu.isEmpty() ? "Формат #AARRGGBB или #RRGGBB" : tipRu,
                tipEn.isEmpty() ? "Format #AARRGGBB or #RRGGBB" : tipEn));
        return v;
    }

    private static ConfigValue<String> string(String key, String def, String ru, String en, String tipRu, String tipEn) {
        ConfigValue<String> v = B.comment(tipEn).define(key, def, o -> o instanceof String);
        add(new Entry(cat, Entry.Type.STRING, v, ru, en, tipRu, tipEn));
        return v;
    }

    public static void save() {
        SPEC.save();
    }
}

package dev.lyricsoverlay.worldlyrics.client;

import net.minecraft.world.phys.Vec3;

/** Строка (или титр), живущая в конкретной точке мира. */
final class WorldLine {
    final String text;
    final String subText;      // вторая строка титра (исполнитель)
    final int index;           // номер строки в тексте, -1 для титра
    final boolean title;
    final Vec3 pos;
    final float yaw;           // поворот, если не «лицом к камере»
    final long born;
    final float seed;
    long die = -1;
    boolean current;
    double particleAcc;

    WorldLine(String text, String subText, int index, boolean title, Vec3 pos, float yaw, long born, float seed) {
        this.text = text;
        this.subText = subText;
        this.index = index;
        this.title = title;
        this.pos = pos;
        this.yaw = yaw;
        this.born = born;
        this.seed = seed;
    }

    double age(long now) {
        return (now - born) / 1e9;
    }

    /** Секунды с момента начала исчезновения, или -1 если строка ещё живёт. */
    double dying(long now) {
        return die < 0 ? -1.0 : Math.max(0.0, (now - die) / 1e9);
    }

    void kill(long now) {
        if (die < 0) {
            die = now;
        }
    }
}

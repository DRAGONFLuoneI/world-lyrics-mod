package dev.lyricsoverlay.worldlyrics.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lyricsoverlay.worldlyrics.WorldLyrics;
import dev.lyricsoverlay.worldlyrics.config.Config;

import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Опрашивает локальный мост Lyrics Overlay (GET /now) в фоновом потоке и досчитывает
 * позицию трека между опросами, чтобы караоке шло плавно на любом FPS.
 */
public final class BridgeClient {
    public static final BridgeClient INSTANCE = new BridgeClient();

    public enum Status { CONNECTING, OK, NO_APP, ERROR, DISABLED }

    /** Часы воспроизведения: позиция на момент baseNanos. Заменяется целиком — без гонок. */
    private record Clock(double basePos, long baseNanos, boolean playing) {
        double at(long now) {
            return basePos + (playing ? (now - baseNanos) / 1e9 : 0.0);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private ScheduledExecutorService exec;

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile Clock clock = new Clock(0, System.nanoTime(), false);
    private volatile Status status = Status.CONNECTING;
    private volatile String error = "";
    private volatile long lastPlayingNanos = System.nanoTime();

    private BridgeClient() {
    }

    public synchronized void start() {
        if (exec != null) {
            return;
        }
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WorldLyrics-Bridge");
            t.setDaemon(true);
            return t;
        });
        exec.schedule(this::loop, 1, TimeUnit.SECONDS);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public Status status() {
        return status;
    }

    public String error() {
        return error;
    }

    public boolean playing() {
        return clock.playing();
    }

    /** Секунды паузы (0, если играет). */
    public double pausedSeconds() {
        return clock.playing() ? 0.0 : (System.nanoTime() - lastPlayingNanos) / 1e9;
    }

    /** Позиция трека в секундах с учётом всех сдвигов (программы и мода). */
    public double position() {
        return clock.at(System.nanoTime()) + Config.OFFSET_MS.get() / 1000.0;
    }

    /** Отправить действие программе (сдвиг, перезагрузка текста). Асинхронно. */
    public void action(String name) {
        ScheduledExecutorService e = exec;
        if (e == null) {
            return;
        }
        e.execute(() -> {
            try {
                String url = base() + "/action?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ex) {
                WorldLyrics.LOG.debug("Bridge action failed: {}", ex.toString());
            }
        });
    }

    private String base() {
        String host = Config.HOST.get().trim();
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + Config.PORT.get();
    }

    private void loop() {
        long delay = 1000;
        try {
            if (!Config.ENABLED.get()) {
                status = Status.DISABLED;
                delay = 1000;
            } else {
                poll();
                delay = Config.POLL_MS.get();
            }
        } catch (ConnectException | HttpConnectTimeoutException e) {
            fail(Status.NO_APP, "");
            delay = 2000;
        } catch (Throwable t) {
            fail(Status.ERROR, t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
            delay = 2000;
        } finally {
            ScheduledExecutorService e = exec;
            if (e != null && !e.isShutdown()) {
                e.schedule(this::loop, Math.max(50, delay), TimeUnit.MILLISECONDS);
            }
        }
    }

    private void fail(Status s, String msg) {
        status = s;
        error = msg;
        Clock c = clock;
        if (c.playing()) {
            clock = new Clock(c.at(System.nanoTime()), System.nanoTime(), false);
        }
    }

    private void poll() throws Exception {
        Snapshot prev = snapshot;
        URI uri = URI.create(base() + "/now?rev=" + prev.rev());
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long t1 = System.nanoTime();
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
        int rev = intOf(o, "rev", -1);
        List<Snapshot.Line> lines;
        if (o.has("lines") && o.get("lines").isJsonArray()) {
            lines = parseLines(o.getAsJsonArray("lines"));
        } else if (rev == prev.rev()) {
            lines = prev.lines();
        } else {
            lines = List.of();
        }
        Snapshot next = new Snapshot(str(o, "title"), str(o, "artist"), str(o, "player"), dbl(o, "duration", 0),
                bool(o, "synced"), bool(o, "approx"), str(o, "status"), rev, str(o, "key"),
                intOf(o, "offset_ms", 0), lines);

        boolean playing = bool(o, "playing");
        double latency = (t1 - t0) / 2e9;
        double reported = dbl(o, "position", 0) + (playing ? latency : 0.0);
        Clock c = clock;
        boolean trackChanged = !next.key().equals(prev.key());
        if (trackChanged || playing != c.playing() || Math.abs(reported - c.at(t1)) > 0.3) {
            clock = new Clock(reported, t1, playing);
        } else if (playing) {
            // мягкая подстройка без рывков
            double drift = reported - c.at(t1);
            clock = new Clock(c.at(t1) + drift * 0.15, t1, true);
        }
        if (playing) {
            lastPlayingNanos = t1;
        }
        snapshot = next;
        status = Status.OK;
        error = "";
    }

    private static List<Snapshot.Line> parseLines(JsonArray arr) {
        List<Snapshot.Line> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject l = el.getAsJsonObject();
            out.add(new Snapshot.Line(dbl(l, "t", 0), str(l, "text")));
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double dbl(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsDouble();
    }

    private static int intOf(JsonObject o, String k, int def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}

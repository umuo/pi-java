package works.earendil.pi.codingagent.core;

import java.io.PrintStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Timings {
    private final boolean enabled;
    private final Clock clock;
    private final Map<String, TimingNamespace> namespaces = new LinkedHashMap<>();

    public Timings(boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    public Timings(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    public static Timings fromEnvironment() {
        return new Timings("1".equals(System.getenv("PI_TIMING")));
    }

    public void resetTimings() {
        resetTimings("main");
    }

    public void resetTimings(String namespace) {
        if (!enabled) {
            return;
        }
        namespaces.put(namespace, new TimingNamespace(new ArrayList<>(), clock.millis()));
    }

    public void time(String label) {
        time(label, "main");
    }

    public void time(String label, String namespace) {
        if (!enabled) {
            return;
        }
        namespaces.computeIfAbsent(namespace, ignored -> new TimingNamespace(new ArrayList<>(), clock.millis()));
        TimingNamespace timingNamespace = namespaces.get(namespace);
        long now = clock.millis();
        timingNamespace.timings().add(new Timing(label, now - timingNamespace.lastTime()));
        timingNamespace.setLastTime(now);
    }

    public void printTimings(PrintStream out) {
        if (!enabled) {
            return;
        }
        for (Map.Entry<String, TimingNamespace> entry : namespaces.entrySet()) {
            printTimingGroup(out, "Startup Timings: " + entry.getKey(), entry.getValue().timings());
        }
    }

    public List<Timing> timings(String namespace) {
        TimingNamespace timingNamespace = namespaces.get(namespace);
        return timingNamespace == null ? List.of() : List.copyOf(timingNamespace.timings());
    }

    private static void printTimingGroup(PrintStream out, String title, List<Timing> timings) {
        List<Timing> printableTimings = timings.stream().filter(timing -> timing.ms() >= 0).toList();
        if (printableTimings.isEmpty()) {
            return;
        }
        out.println();
        out.println("--- " + title + " ---");
        long total = 0;
        for (Timing timing : printableTimings) {
            total += timing.ms();
            out.println("  " + timing.label() + ": " + timing.ms() + "ms");
        }
        out.println("  TOTAL: " + total + "ms");
        out.println("-".repeat(title.length() + 8));
        out.println();
    }

    public record Timing(String label, long ms) {
    }

    private static final class TimingNamespace {
        private final List<Timing> timings;
        private long lastTime;

        private TimingNamespace(List<Timing> timings, long lastTime) {
            this.timings = timings;
            this.lastTime = lastTime;
        }

        private List<Timing> timings() {
            return timings;
        }

        private long lastTime() {
            return lastTime;
        }

        private void setLastTime(long lastTime) {
            this.lastTime = lastTime;
        }
    }
}

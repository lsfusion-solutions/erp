package equ.clt.handler;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

//scales that don't accept connections: they are not connected to until the next attempt time (growing up to maxBackoff),
//so that every transaction and stop-list doesn't wait for the connection timeout (10-60 sec) of the same dead scales
public class ScalesAvailability {

    private static final int failuresBeforeBackoff = 2;
    //ms, can be changed with -Dequ.scales.minBackoff / -Dequ.scales.maxBackoff
    private static final long minBackoff = Long.getLong("equ.scales.minBackoff", 60000); //1 minute
    private static final long maxBackoff = Long.getLong("equ.scales.maxBackoff", 15 * 60000); //15 minutes

    private static class State {
        private final int failures;
        private final long nextAttemptNanos; //System.nanoTime: not affected by changes of the system clock

        private State(int failures, long nextAttemptNanos) {
            this.failures = failures;
            this.nextAttemptNanos = nextAttemptNanos;
        }
    }

    private static final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    //connections open now: scales accepting only one connection refuse the second one (e.g. stop-list during transaction), it isn't a failure
    private static final ConcurrentHashMap<String, Integer> openConnections = new ConcurrentHashMap<>();

    //null if the scales can be connected to, otherwise the error to report instead of connecting
    public static String getUnavailableError(String ip) {
        State state = ip != null ? states.get(ip) : null;
        long remaining = state != null ? state.nextAttemptNanos - System.nanoTime() : 0;
        if (remaining <= 0)
            return null;
        LocalTime nextAttempt = LocalTime.now().plusNanos(remaining).truncatedTo(ChronoUnit.SECONDS);
        return String.format("IP %s is unavailable (%s failed connections in a row), next attempt after %s", ip, state.failures, nextAttempt);
    }

    //connection is open, connectionClosed must be called after it is closed
    public static void connectionOpened(String ip) {
        if (ip != null) {
            states.remove(ip);
            openConnections.merge(ip, 1, Integer::sum);
        }
    }

    public static void connectionClosed(String ip) {
        if (ip != null)
            openConnections.computeIfPresent(ip, (key, count) -> count > 1 ? count - 1 : null);
    }

    //exchange succeeded without an own connection object (e.g. inside a library)
    public static void succeeded(String ip) {
        if (ip != null)
            states.remove(ip);
    }

    //a refused connection, which another one gets at the same moment, can still be counted: it's reset by the next successful connection
    public static void connectionFailed(String ip) {
        if (ip != null && !openConnections.containsKey(ip))
            states.compute(ip, (key, state) -> {
                int failures = state == null ? 1 : state.failures + 1;
                long backoff = failures < failuresBeforeBackoff ? 0 : Math.min(minBackoff << Math.min(failures - failuresBeforeBackoff, 20), maxBackoff);
                return new State(failures, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(backoff));
            });
    }
}

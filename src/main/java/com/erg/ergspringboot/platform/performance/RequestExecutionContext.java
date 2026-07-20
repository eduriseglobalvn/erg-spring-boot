package com.erg.ergspringboot.platform.performance;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Request-thread counters used for diagnostics and test-enforced query budgets. */
public final class RequestExecutionContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private RequestExecutionContext() {
    }

    public static Scope open(String route) {
        State previous = CURRENT.get();
        CURRENT.set(new State(route, Instant.now(), 0));
        return new Scope(previous);
    }

    public static Carrier capture() {
        return new Carrier(CURRENT.get());
    }

    public static Scope attach(Carrier carrier) {
        State previous = CURRENT.get();
        if (carrier == null || carrier.state == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(carrier.state);
        }
        return new Scope(previous);
    }

    public static void incrementStatementCount() {
        State state = CURRENT.get();
        if (state != null) {
            state.statementCount.incrementAndGet();
        }
    }

    public static Snapshot currentSnapshot() {
        State state = CURRENT.get();
        return state == null ? Snapshot.empty() : new Snapshot(state.route, state.startedAt, state.statementCount.get());
    }

    public record Snapshot(String route, Instant startedAt, long statementCount) {
        private static Snapshot empty() {
            return new Snapshot(null, null, 0);
        }
    }

    public static final class Carrier {
        private final State state;

        private Carrier(State state) {
            this.state = state;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private boolean closed;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private static final class State {
        private final String route;
        private final Instant startedAt;
        private final AtomicLong statementCount;

        private State(String route, Instant startedAt, long statementCount) {
            this.route = route;
            this.startedAt = startedAt;
            this.statementCount = new AtomicLong(statementCount);
        }
    }
}

package ah.runtime.risk;

import java.util.Objects;

public final class RiskSignal {
    private final RiskSignalId id;
    private final SignalState state;
    private final int score;

    RiskSignal(RiskSignalId id, SignalState state, int score) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = Objects.requireNonNull(state, "state");
        if (score < 0 || score > 100 || (state != SignalState.DETECTED && score != 0)) {
            throw new IllegalArgumentException("AAH-RUNTIME-RISK-SIGNAL");
        }
        this.score = score;
    }

    public RiskSignalId id() {
        return id;
    }

    public SignalState state() {
        return state;
    }

    public boolean hit() {
        return state == SignalState.DETECTED;
    }

    public int score() {
        return score;
    }
}

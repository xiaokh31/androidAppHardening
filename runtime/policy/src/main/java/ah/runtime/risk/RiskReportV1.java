package ah.runtime.risk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public final class RiskReportV1 {
    public static final int VERSION = 1;

    private final List<RiskSignal> signals;
    private final int totalScore;
    private final RiskLevel level;
    private final RiskAction action;

    RiskReportV1(List<RiskSignal> signals) {
        Objects.requireNonNull(signals, "signals");
        ArrayList<RiskSignal> copy = new ArrayList<>(signals.size());
        EnumSet<RiskSignalId> seen = EnumSet.noneOf(RiskSignalId.class);
        int total = 0;
        for (RiskSignal signal : signals) {
            Objects.requireNonNull(signal, "signal");
            if (!seen.add(signal.id())) {
                throw new IllegalArgumentException("AAH-RUNTIME-RISK-DUPLICATE");
            }
            copy.add(signal);
            total = Math.min(100, total + signal.score());
        }
        this.signals = Collections.unmodifiableList(copy);
        this.totalScore = total;
        this.level = total < 40 ? RiskLevel.LOW : total < 80 ? RiskLevel.MEDIUM : RiskLevel.HIGH;
        this.action = level == RiskLevel.LOW ? RiskAction.ALLOW : RiskAction.DEGRADE;
    }

    public int version() {
        return VERSION;
    }

    public List<RiskSignal> signals() {
        return signals;
    }

    public int totalScore() {
        return totalScore;
    }

    public RiskLevel level() {
        return level;
    }

    public RiskAction action() {
        return action;
    }
}

package service;

import exception.TimeCalculationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Időforrás, amely fix Clock-kal jól tesztelhető. */
public final class TimeTracker {
    private final Clock clock;

    public TimeTracker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "A Clock nem lehet null.");
    }

    public Instant now() {
        return clock.instant();
    }

    public long elapsedSeconds(Instant startedAt, Instant stoppedAt) throws TimeCalculationException {
        long seconds = Duration.between(startedAt, stoppedAt).getSeconds();
        if (seconds < 0) {
            throw new TimeCalculationException(exception.ErrorCode.TIME_CLOCK_REGRESSION,
                    "Az óra visszaállt; negatív idő nem számolható el.");
        }
        return seconds;
    }
}

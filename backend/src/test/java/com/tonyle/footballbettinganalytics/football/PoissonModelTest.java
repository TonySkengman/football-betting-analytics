package com.tonyle.footballbettinganalytics.football;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

class PoissonModelTest {
    private final ApiTeam home = new ApiTeam(1, "Home", "HOM", null);
    private final ApiTeam away = new ApiTeam(2, "Away", "AWY", null);
    private final Competition competition = new Competition("PL", "Premier League");
    private final Season season = new Season("2026-08-01");

    private SourceMatch match(long id, String date, ApiScore score) {
        return new SourceMatch(id, date, "FINISHED", competition, season, home, away, score);
    }
    private SourceMatch selected() { return match(99, "2026-10-21T15:00:00Z", null); }
    private List<SourceMatch> fixtures(int count, int h, int a) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> match(id, "2026-10-01T15:00:00Z",
                new ApiScore(new ApiScoreline(h, a), null, "REGULAR"))).toList();
    }

    @Test
    void knownSymmetricPoissonExampleAndComplements() {
        var p = PoissonModel.probabilities(1, 1);
        assertNotNull(p);
        assertEquals(0.308508322553671, p.draw(), 1e-11);
        assertEquals(0.3457458387231645, p.homeWin(), 1e-11);
        assertEquals(p.homeWin(), p.awayWin(), 1e-12);
        assertEquals(5 * Math.exp(-2), p.under25(), 1e-12);
        assertEquals(Math.pow(1 - Math.exp(-1), 2), p.bttsYes(), 1e-12);
        assertEquals(1, p.homeWin() + p.draw() + p.awayWin(), 1e-12);
        assertEquals(1, p.over25() + p.under25(), 1e-12);
        assertEquals(1, p.bttsYes() + p.bttsNo(), 1e-12);
    }

    @Test
    void zeroGoalRatesAndInvalidRatesAreHandledExplicitly() {
        var p = PoissonModel.probabilities(0, 0);
        assertEquals(1, p.draw());
        assertEquals(1, p.under25());
        assertEquals(0, p.over25());
        assertEquals(0, p.bttsYes());
        assertEquals(0, p.omittedMass());
        var oneSided = PoissonModel.probabilities(0, 2);
        assertEquals(0, oneSided.homeWin());
        assertEquals(Math.exp(-2), oneSided.draw(), 1e-12);
        assertNull(PoissonModel.probabilities(Double.NaN, 1));
        assertNull(PoissonModel.probabilities(-1, 1));
        assertNull(PoissonModel.probabilities(21, 1));
    }

    @Test
    void adaptiveGridReportsSmallTailAndNormalizes() {
        for (double mean : new double[]{0.1, 2, 8, 20}) {
            var p = PoissonModel.probabilities(mean, mean / 2);
            assertNotNull(p);
            assertTrue(p.omittedMass() >= 0 && p.omittedMass() <= 3e-12);
            assertEquals(1, p.homeWin() + p.draw() + p.awayWin(), 1e-12);
            assertTrue(p.homeWin() >= 0 && p.awayWin() <= 1);
        }
    }





    @Test
    void regulationSemanticsNeverUseShootoutTotalsOrUnknownDuration() {
        var total = new ApiScoreline(5, 4);
        var regular = new ApiScoreline(1, 1);
        assertEquals(regular, PoissonModel.regulationScore(new ApiScore(total, regular, "PENALTY_SHOOTOUT")));
        assertNull(PoissonModel.regulationScore(new ApiScore(total, null, "PENALTY_SHOOTOUT")));
        assertNull(PoissonModel.regulationScore(new ApiScore(total, null, "EXTRA_TIME")));
        assertNull(PoissonModel.regulationScore(new ApiScore(total)));
        assertNull(PoissonModel.regulationScore(null));
        assertEquals(total, PoissonModel.regulationScore(new ApiScore(total, null, "REGULAR")));
        assertNull(PoissonModel.regulationScore(new ApiScore(new ApiScoreline(1, null), null, "REGULAR")));

    }




}

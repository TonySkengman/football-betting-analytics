package com.tonyle.footballbettinganalytics.football;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

class BacktestTest {
    private SourceMatch fixture(long id, String date, Integer h, Integer a) {
        return new SourceMatch(id, date, "FINISHED", new Competition("PL", "Premier League"), new Season("2025-08-01"),
                new ApiTeam(1, "Home", "HOM", null), new ApiTeam(2, "Away", "AWY", null),
                new ApiScore(new ApiScoreline(h, a), null, "REGULAR"));
    }
    private SourceMatch selected() { return fixture(999, "2026-01-20T15:00:00Z", 2, 1); }
    private List<SourceMatch> history() {
        var matches = new ArrayList<SourceMatch>();
        for (int i = 1; i <= 30; i++) matches.add(fixture(i, "2025-12-%02dT12:00:00Z".formatted(i), 2, 1));
        return matches;
    }
    private Map<Long, TeamHistory.Loaded> loaded(List<SourceMatch> matches) {
        return Map.of(1L, new TeamHistory.Loaded(matches, null), 2L, new TeamHistory.Loaded(matches, null));
    }
    private BacktestRunner.Config config(int budget) {
        return new BacktestRunner.Config("PL", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), 20, budget, 6500);
    }

    @Test void fixedBlendPreservesProbabilitySumsComplementsAndZeros() {
        assertArrayEquals(new double[]{0.5, 0.25, 0.25}, BacktestEvaluation.blend(new double[]{1, 0, 0}, new double[]{0, 0.5, 0.5}));
        assertArrayEquals(new double[]{0.6, 0.4}, BacktestEvaluation.blend(new double[]{0.8, 0.2}, new double[]{0.4, 0.6}), 1e-12);
        assertArrayEquals(new double[]{0, 1}, BacktestEvaluation.blend(new double[]{0, 1}, new double[]{0, 1}));
        assertThrows(IllegalArgumentException.class, () -> BacktestEvaluation.blend(new double[]{1, 0}, new double[]{1, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> BacktestEvaluation.blend(new double[]{Double.NaN, 0}, new double[]{1, 0}));
    }

    @Test void candidateIsScoredPerFixtureAndCasesAllowOfflineReevaluation() {
        var fixtures = history();
        var report = BacktestEvaluation.evaluate(List.of(selected()), fixtures, loaded(fixtures), null);
        assertEquals(BacktestEvaluation.CANDIDATE_VERSION, report.candidateVersion());
        assertEquals(1, report.cases().size());
        var c = report.cases().getFirst();
        assertEquals(selected().id(), c.matchId());
        var expected = BacktestMetrics.score(BacktestEvaluation.blend(c.model().result(), c.baseline().result()), c.actual().result());
        assertEquals(expected.brier(), report.markets().get("1X2").candidate().brier(), 1e-12);
        assertEquals(expected.logLoss(), report.markets().get("1X2").candidate().logLoss(), 1e-12);
        var failed = BacktestEvaluation.evaluate(List.of(selected()), fixtures, loaded(fixtures), "incomplete");
        assertTrue(failed.cases().isEmpty());
        assertNull(failed.markets().get("1X2").candidate().brier());
    }

    @Test void knownScoresAndZeroOneSafeguards() {
        var multi = BacktestMetrics.score(new double[]{0.7, 0.2, 0.1}, 0);
        assertEquals(0.14, multi.brier(), 1e-12);
        assertEquals(-Math.log(0.7), multi.logLoss(), 1e-12);
        var binary = BacktestMetrics.score(new double[]{0.8, 0.2}, 1);
        assertEquals(0.64, binary.brier(), 1e-12);
        assertEquals(-Math.log(0.2), binary.logLoss(), 1e-12);
        assertEquals(0, BacktestMetrics.score(new double[]{1, 0}, 0).logLoss());
        assertEquals(-Math.log(1e-15), BacktestMetrics.score(new double[]{1, 0}, 1).logLoss());
        assertEquals(1, BacktestMetrics.score(new double[]{1, 0}, 1).brier());
        assertThrows(IllegalArgumentException.class, () -> BacktestMetrics.score(new double[]{0.8, 0.8}, 0));
    }

    @Test void regulationOutcomeMappingCoversAllMarkets() {
        assertEquals(new BacktestEvaluation.Outcomes(0, 0, 0), BacktestEvaluation.outcomes(selected()));
        assertEquals(new BacktestEvaluation.Outcomes(1, 1, 1), BacktestEvaluation.outcomes(fixture(1, "2025-12-01T12:00:00Z", 0, 0)));
        assertEquals(new BacktestEvaluation.Outcomes(2, 0, 1), BacktestEvaluation.outcomes(fixture(1, "2025-12-01T12:00:00Z", 0, 3)));
        assertEquals(0, BacktestEvaluation.outcomes(fixture(1, "2025-12-01T12:00:00Z", 2, 0)).result());
        var m = selected();
        var penalties = new SourceMatch(m.id(), m.utcDate(), m.status(), m.competition(), m.season(), m.homeTeam(), m.awayTeam(), new ApiScore(new ApiScoreline(5, 4), new ApiScoreline(1, 1), "PENALTY_SHOOTOUT"));
        assertEquals(new BacktestEvaluation.Outcomes(1, 1, 0), BacktestEvaluation.outcomes(penalties));
        var unknown = new SourceMatch(m.id(), m.utcDate(), m.status(), m.competition(), m.season(), m.homeTeam(), m.awayTeam(), new ApiScore(new ApiScoreline(5, 4), null, "PENALTY_SHOOTOUT"));
        assertNull(BacktestEvaluation.outcomes(unknown));
        assertNull(BacktestEvaluation.outcomes(fixture(1, m.utcDate(), null, 0)));
    }

    @Test void exactHistoricalCutoffAppliesToBothModelAndBaseline() {
        var past = history();
        var contaminated = new ArrayList<>(past);
        contaminated.add(selected());
        contaminated.add(fixture(998, selected().utcDate(), 9, 9));
        contaminated.add(fixture(997, "2026-01-20T15:00:01Z", 9, 9));
        contaminated.add(fixture(996, "2024-01-01T12:00:00Z", 9, 9));
        var validSameDay = fixture(995, "2026-01-20T14:59:59Z", 2, 1);
        contaminated.add(validSameDay);
        assertEquals(31, BacktestEvaluation.eligibleBaseline(selected(), contaminated).size());
        var p = PoissonModel.estimate(selected(), new TeamHistory.Loaded(past, null), new TeamHistory.Loaded(past, null));
        var q = PoissonModel.estimate(selected(), new TeamHistory.Loaded(contaminated, null), new TeamHistory.Loaded(contaminated, null));
        assertEquals(p.probabilities(), q.probabilities());
        var baseline = BacktestEvaluation.baseline(selected(), contaminated);
        assertArrayEquals(new double[]{1, 0, 0}, baseline.result());
        assertArrayEquals(new double[]{1, 0}, baseline.goals());
    }

    @Test void pairedSamplesAndSkipCountsStayIdentical() {
        var past = history();
        var missingScore = fixture(998, "2026-01-21T15:00:00Z", null, 0);
        var report = BacktestEvaluation.evaluate(List.of(selected(), missingScore), past, loaded(past), null);
        assertEquals(1, report.compared());
        assertEquals(List.of(999L), report.comparedMatchIds());
        assertEquals(1, report.skipped().get("unconfirmed_regulation_score"));
        report.markets().values().forEach(m -> { assertEquals(1, m.model().matches()); assertEquals(1, m.baseline().matches()); assertEquals(1, m.candidate().matches()); });
        var noBaseline = BacktestEvaluation.evaluate(List.of(selected()), past.subList(0, 20), loaded(past), null);
        assertEquals(1, noBaseline.modelAvailable());
        assertEquals(0, noBaseline.compared());
        noBaseline.markets().values().forEach(m -> { assertNull(m.candidate().brier()); assertNull(m.model().brier()); assertEquals(0, m.baseline().matches()); });
        assertEquals(1, noBaseline.skipped().get("baseline_insufficient_data"));
    }

    @Test void partialHistoriesAndInsufficientInputsAreNotScored() {
        var past = history();
        var failed = BacktestEvaluation.evaluate(List.of(selected()), past, Map.of(1L, new TeamHistory.Loaded(past, null)), null);
        assertEquals(1, failed.skipped().get("history_unavailable"));
        var small = BacktestEvaluation.evaluate(List.of(selected()), past, loaded(past.subList(0, 2)), null);
        assertEquals(1, small.skipped().get("insufficient_data"));
        var baselineFailed = BacktestEvaluation.evaluate(List.of(selected()), past, loaded(past), "Subscription restriction");
        assertEquals(1, baselineFailed.skipped().get("baseline_unavailable"));
        assertEquals(0, baselineFailed.compared());
    }

    @Test void calibrationBoundariesAndCountsIncludeZeroAndOne() {
        var metrics = new BacktestMetrics();
        metrics.add(new double[]{0, 1}, 1);
        metrics.add(new double[]{0.1, 0.9}, 0);
        metrics.add(new double[]{1, 0}, 0);
        var bins = metrics.summary().calibration();
        assertEquals(1, bins.get(0).count());
        assertEquals(0, bins.get(0).actualFrequency());
        assertEquals(1, bins.get(1).count());
        assertEquals(1, bins.get(9).count());
        assertEquals(1, bins.get(9).actualFrequency());
        assertNull(bins.get(2).actualFrequency());
        assertTrue(bins.get(9).sparse());
        assertEquals(3, bins.stream().mapToInt(BacktestMetrics.Bin::count).sum());
        var multi = new BacktestMetrics();
        multi.add(new double[]{0.5, 0.3, 0.2}, 0);
        assertEquals(3, multi.summary().calibration().stream().mapToInt(BacktestMetrics.Bin::count).sum());
    }

    @Test void selectionIsUtcBoundedAndChronological() {
        var list = List.of(fixture(3, "2026-02-01T00:00:00Z", 0, 0), selected(), fixture(2, "2026-01-01T00:00:00Z", 1, 0), fixture(1, "2025-12-31T23:59:59Z", 0, 0));
        assertEquals(List.of(2L, 999L), BacktestRunner.select(list, config(1)).stream().map(SourceMatch::id).toList());
    }

    @Test void partialRunProducesReportWithoutScoringMissingHistories() throws Exception {
        var service = new FootballService(RestClient.create(), "test") {
            @Override <T> T fetch(String path, Class<T> type) { return type.cast(new SourceMatches(List.of(selected()))); }
        };
        var report = BacktestRunner.execute(service, config(1));
        assertTrue(report.partial());
        assertEquals(1, report.requests());
        assertEquals(1, report.evaluation().selected());
        assertEquals(0, report.evaluation().compared());
        assertFalse(report.retrievalIssues().isEmpty());
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(report);
        assertTrue(json.contains("cross-competition-poisson-v2"));
        assertTrue(json.contains("history_unavailable"));
    }

    @Test void budgetAndTruncationPreventFalseCompleteHistories() throws Exception {
        var service = new FootballService(RestClient.create(), "test") {
            @Override <T> T fetch(String path, Class<T> type) { return type.cast(new SourceMatches(history())); }
        };
        var requests = new BacktestRunner.Requests(service, config(1));
        assertNull(requests.load("/first", true).error());
        assertNotNull(requests.load("/second", true).error());
        assertEquals(1, requests.count);
        var capped = new FootballService(RestClient.create(), "test") {
            @Override <T> T fetch(String path, Class<T> type) { return type.cast(new SourceMatches(Collections.nCopies(500, selected()))); }
        };
        assertNotNull(new BacktestRunner.Requests(capped, config(1)).load("/capped", true).error());
        var unavailable = new FootballService(RestClient.create(), "test") {
            @Override <T> T fetch(String path, Class<T> type) { throw new FootballDataException(HttpStatus.TOO_MANY_REQUESTS, "Rate limited"); }
        };
        var stopped = new BacktestRunner.Requests(unavailable, config(2));
        assertNotNull(stopped.load("/first", true).error());
        assertNotNull(stopped.load("/second", true).error());
        assertEquals(1, stopped.count);
    }
}

package com.tonyle.footballbettinganalytics.football;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

@Component
@Profile("backtest")
public class BacktestRunner implements ApplicationRunner {
    private final FootballService service;
    private final Environment environment;

    BacktestRunner(FootballService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }

    record Config(String competition, LocalDate from, LocalDate to, int maxMatches, int budget, long intervalMillis) {
        Config {
            if (!Set.of("PL", "CL").contains(competition)) throw new IllegalArgumentException("Use competition PL or CL.");
            if (from.isAfter(to) || from.plusDays(366).isBefore(to) || to.isAfter(LocalDate.now(ZoneOffset.UTC)))
                throw new IllegalArgumentException("Use a past UTC date range of at most 366 days.");
            if (maxMatches < 1 || maxMatches > 100 || budget < 1 || budget > 100 || intervalMillis < 6500)
                throw new IllegalArgumentException("Use 1–100 matches/requests and an interval of at least 6500ms.");
        }
    }
    record FetchResult(List<SourceMatch> matches, String error) {}
    record RunReport(String generatedAt, Config configuration, int requests, boolean partial,
                     List<String> retrievalIssues, int candidatesInRange, int notSelectedByLimit,
                     List<Long> comparedMatchIds, BacktestEvaluation.Report evaluation, String coverage) {}

    static final class Requests {
        private final FootballService service;
        private final Config config;
        int count;
        boolean stopped;
        long lastRequestNanos;
        final List<String> issues = new ArrayList<>();
        Requests(FootballService service, Config config) { this.service = service; this.config = config; }
        FetchResult load(String path, boolean teamHistory) throws InterruptedException {
            if (stopped || count >= config.budget()) {
                String reason = stopped ? "Requests stopped after provider failure." : "Request budget exhausted.";
                issues.add(path + ": " + reason);
                return new FetchResult(List.of(), reason);
            }
            if (count > 0) {
                long remaining = config.intervalMillis() - (System.nanoTime() - lastRequestNanos) / 1_000_000;
                if (remaining > 0) Thread.sleep(remaining);
            }
            count++;
            lastRequestNanos = System.nanoTime();
            try {
                var response = service.fetch(path, SourceMatches.class);
                if (response == null || response.matches() == null || response.matches().stream().anyMatch(Objects::isNull))
                    throw new IllegalStateException("Empty or malformed provider response.");
                if (teamHistory && response.matches().size() >= TeamHistory.REQUEST_LIMIT)
                    throw new IllegalStateException("500-match cap reached; history may be truncated.");
                return new FetchResult(List.copyOf(response.matches()), null);
            } catch (FootballDataException exception) {
                // Stop on failures instead of retrying or using an incomplete slice as complete history.
                stopped = true;
                issues.add(path + ": " + exception.getMessage());
                return new FetchResult(List.of(), exception.getMessage());
            } catch (IllegalStateException exception) {
                issues.add(path + ": " + exception.getMessage());
                return new FetchResult(List.of(), exception.getMessage());
            }
        }
    }

    static List<SourceMatch> select(List<SourceMatch> matches, Config config) {
        Instant from = config.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = config.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return matches.stream().filter(m -> m.competition() != null && config.competition().equals(m.competition().code()))
                .filter(m -> "FINISHED".equals(m.status()) && timestamp(m.utcDate()) != null)
                .filter(m -> !timestamp(m.utcDate()).isBefore(from) && timestamp(m.utcDate()).isBefore(end))
                .sorted(Comparator.comparing((SourceMatch m) -> timestamp(m.utcDate())).thenComparingLong(SourceMatch::id)).toList();
    }

    private static int seasonYear(LocalDate date) {
        return date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;
    }

    static RunReport execute(FootballService service, Config config) throws InterruptedException {
        var requests = new Requests(service, config);
        var fixtures = new LinkedHashMap<Long, SourceMatch>();
        String baselineError = null;
        // PL/CL seasons start in the start-date year and can overlap calendar years.
        for (int season = seasonYear(config.to()); season >= seasonYear(config.from().minusDays(365)); season--) {
            var response = requests.load("/competitions/" + config.competition() + "/matches?season=" + season + "&status=FINISHED", false);
            if (response.error() != null) baselineError = "Competition history is incomplete.";
            for (var match : response.matches()) fixtures.putIfAbsent(match.id(), match);
        }
        var candidates = select(List.copyOf(fixtures.values()), config);
        var selected = candidates.stream().limit(config.maxMatches()).toList();
        var histories = new LinkedHashMap<Long, TeamHistory.Loaded>();
        for (var match : selected) {
            if (BacktestEvaluation.outcomes(match) == null || match.homeTeam() == null || match.awayTeam() == null) continue;
            for (long id : new long[]{match.homeTeam().id(), match.awayTeam().id()}) {
                if (histories.containsKey(id)) continue;
                String path = "/teams/" + id + "/matches?dateFrom=" + config.from().minusDays(365)
                        + "&dateTo=" + config.to().plusDays(1) + "&status=FINISHED&limit=500";
                var response = requests.load(path, true);
                histories.put(id, new TeamHistory.Loaded(response.matches(), response.error()));
            }
        }
        var evaluation = BacktestEvaluation.evaluate(selected, List.copyOf(fixtures.values()), histories, baselineError);
        return new RunReport(Instant.now().toString(), config, requests.count, !requests.issues.isEmpty(), List.copyOf(requests.issues),
                candidates.size(), Math.max(0, candidates.size() - selected.size()), evaluation.comparedMatchIds(), evaluation,
                "Subscription-visible results only; not every competition/season is covered. Current provider records are not archived as-of snapshots. Retrospective evaluation, not proof of future profitability.");
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        var config = new Config(environment.getProperty("backtest.competition", "PL"),
                LocalDate.parse(environment.getRequiredProperty("backtest.from")), LocalDate.parse(environment.getRequiredProperty("backtest.to")),
                environment.getProperty("backtest.max-matches", Integer.class, 20), environment.getProperty("backtest.request-budget", Integer.class, 25),
                environment.getProperty("backtest.interval-ms", Long.class, 6500L));
        var report = execute(service, config);
        print(report);
        String output = environment.getProperty("backtest.output", "");
        if (!output.isBlank()) {
            Path path = Path.of(output).toAbsolutePath().normalize();
            if (!path.startsWith(Path.of("backtest-reports").toAbsolutePath().normalize()))
                throw new IllegalArgumentException("Save generated reports inside backtest-reports/ (ignored by Git).");
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
            System.out.println("JSON report: " + path);
        }
    }

    private static void print(RunReport report) {
        var evaluation = report.evaluation();
        System.out.printf("%nBacktest %s | %s %s to %s | %s%n", evaluation.modelVersion(), report.configuration().competition(),
                report.configuration().from(), report.configuration().to(), report.partial() ? "PARTIAL RUN" : "Retrieval completed (subscription coverage only)");
        System.out.printf("Requests %d/%d | candidates %d | outside match limit %d | selected %d | model available %d | compared %d%n",
                report.requests(), report.configuration().budget(), report.candidatesInRange(), report.notSelectedByLimit(), evaluation.selected(), evaluation.modelAvailable(), evaluation.compared());
        System.out.println("Skipped from paired comparison: " + evaluation.skipped());
        report.retrievalIssues().forEach(System.out::println);
        for (var entry : evaluation.markets().entrySet()) {
            System.out.println("\n" + entry.getKey() + " (lower Brier/log loss is better)");
            for (var metric : Map.of("Model", entry.getValue().model(), "Baseline", entry.getValue().baseline(), "Candidate (50/50 blend)", entry.getValue().candidate()).entrySet()) {
                System.out.printf("%s n=%d Brier=%s logLoss=%s%n", metric.getKey(), metric.getValue().matches(), metric.getValue().brier(), metric.getValue().logLoss());
                System.out.println("Calibration: range | events | mean probability | observed frequency | sparse (<20 events)");
                for (var bin : metric.getValue().calibration()) System.out.printf("[%.1f, %.1f%s %d | %s | %s | %s%n",
                        bin.from(), bin.to(), bin.to() == 1 ? "]" : ")", bin.count(), bin.meanProbability(), bin.actualFrequency(), bin.sparse());
            }
        }
        System.out.println("Candidate: " + evaluation.candidateVersion() + " | fixed 50% model + 50% baseline; experimental, not deployed to match details.");
        System.out.println(report.coverage());
    }
}

package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

public final class FootballMapper {
    private FootballMapper() {}

    private static Team team(ApiTeam team) {
        if (team == null) return new Team(0, "To be confirmed", "TBC", null);
        String name = team.name() == null ? "Unknown team" : team.name();
        String code = team.tla() == null || team.tla().isBlank() ? "—" : team.tla();
        return new Team(team.id(), name, code, team.crest());
    }

    public static List<Match> matches(MatchesResponse response) {
        if (response == null || response.matches() == null) return List.of();
        return response.matches().stream()
                .map(m -> new Match(m.id(), m.utcDate(), m.status(), m.matchday(),
                        team(m.homeTeam()), team(m.awayTeam())))
                .toList();
    }

    public static List<Standing> standings(StandingsResponse response) {
        if (response == null || response.standings() == null) return List.of();
        return response.standings().stream()
                .filter(group -> "TOTAL".equals(group.type()))
                .filter(group -> group.table() != null)
                .flatMap(group -> group.table().stream())
                .map(row -> new Standing(row.position(), team(row.team()), row.playedGames(),
                        row.points(), row.goalsFor(), row.goalsAgainst(), row.goalDifference(), row.form()))
                .toList();
    }

    public static MatchDetails details(ApiMatchDetails response) {
        var score = response.score() == null ? null : response.score().fullTime();
        var stats = new java.util.ArrayList<Statistic>();
        addStatistic(stats, response, "ball_possession", "Possession", "%");
        addStatistic(stats, response, "shots", "Shots", "");
        addStatistic(stats, response, "shots_on_goal", "Shots on target", "");
        addStatistic(stats, response, "corner_kicks", "Corners", "");
        addStatistic(stats, response, "fouls", "Fouls", "");
        addStatistic(stats, response, "yellow_cards", "Yellow cards", "");
        addStatistic(stats, response, "red_cards", "Red cards", "");
        return new MatchDetails(response.id(), response.utcDate(), response.status(), response.matchday(),
                response.competition() == null ? null : response.competition().name(), response.venue(),
                detailTeam(response.homeTeam()), detailTeam(response.awayTeam()),
                score == null ? null : score.home(), score == null ? null : score.away(), List.copyOf(stats));
    }

    private static Team detailTeam(ApiDetailTeam value) {
        return team(value == null ? null : new ApiTeam(value.id(), value.name(), value.tla(), value.crest()));
    }

    private static void addStatistic(List<Statistic> stats, ApiMatchDetails match,
                                     String key, String label, String unit) {
        Integer home = match.homeTeam() == null || match.homeTeam().statistics() == null
                ? null : match.homeTeam().statistics().get(key);
        Integer away = match.awayTeam() == null || match.awayTeam().statistics() == null
                ? null : match.awayTeam().statistics().get(key);
        if (home != null || away != null) stats.add(new Statistic(label, home, away, unit));
    }
}

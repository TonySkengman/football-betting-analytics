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
}

package com.tonyle.footballbettinganalytics.football;

import java.util.List;

public final class FootballDtos {
    private FootballDtos() {}

    public record Team(long id, String name, String code, String crest) {}
    public record Match(long id, String utcDate, String status, Integer matchday,
                        Team home, Team away) {}
    public record Standing(int position, Team team, int played, int points,
                           int scored, int conceded, int goalDifference, String form) {}

    // Only fields used by this application are read from the upstream response.
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchesResponse(List<ApiMatch> matches) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiMatch(long id, String utcDate, String status, Integer matchday,
                           ApiTeam homeTeam, ApiTeam awayTeam) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiTeam(long id, String name, String tla, String crest) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsResponse(List<ApiStandingGroup> standings) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStandingGroup(String type, List<ApiStanding> table) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStanding(int position, ApiTeam team, int playedGames, int points,
                              int goalsFor, int goalsAgainst, int goalDifference, String form) {}
}

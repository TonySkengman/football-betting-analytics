package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

class FootballMapperTest {
    @Test
    void mapsMatchesAndMissingTeams() {
        var home = new ApiTeam(1, "Arsenal", "ARS", "https://crests.football-data.org/57.png");
        var response = new MatchesResponse(List.of(new ApiMatch(7, "2026-10-24T17:30:00Z", "TIMED", 9, home, null)));
        var match = FootballMapper.matches(response).getFirst();
        assertEquals("ARS", match.home().code());
        assertEquals("https://crests.football-data.org/57.png", match.home().crest());
        assertNull(match.away().crest());
        assertEquals("To be confirmed", match.away().name());
        assertEquals(9, match.matchday());
    }

    @Test
    void mapsOnlyTotalStandingsAndPreservesNullForm() {
        var row = new ApiStanding(1, new ApiTeam(1, "Arsenal", "ARS", "https://crests.football-data.org/57.png"), 8, 20, 19, 6, 13, null);
        var response = new StandingsResponse(List.of(new ApiStandingGroup("HOME", List.of(row)), new ApiStandingGroup("TOTAL", List.of(row))));
        var result = FootballMapper.standings(response);
        assertEquals(1, result.size());
        assertEquals(19, result.getFirst().scored());
        assertNull(result.getFirst().form());
    }

    @Test
    void returnsEmptyListsForMissingResponses() {
        assertTrue(FootballMapper.matches(null).isEmpty());
        assertTrue(FootballMapper.matches(new MatchesResponse(null)).isEmpty());
        assertTrue(FootballMapper.standings(null).isEmpty());
    }

    @Test
    void missingKeyProducesSafeConfigurationError() {
        var service = new FootballService("https://api.football-data.org/v4", "");
        var error = assertThrows(FootballDataException.class, service::matches);
        assertEquals(503, error.status().value());
        assertTrue(error.getMessage().contains("FOOTBALL_DATA_API_KEY"));
    }

    @Test
    void mapsDetailsWithoutInventingMissingStatisticsOrScores() {
        var home = new ApiDetailTeam(1, "Home", "HOM", null, java.util.Map.of("shots", 0));
        var away = new ApiDetailTeam(2, "Away", "AWY", null, null);
        var source = new ApiMatchDetails(7, null, "SCHEDULED", 9, new ApiCompetition("Premier League"), null, home, away, null);
        var result = FootballMapper.details(source);
        assertNull(result.homeScore());
        assertNull(result.awayScore());
        assertEquals(1, result.statistics().size());
        assertEquals(0, result.statistics().getFirst().home());
        assertNull(result.statistics().getFirst().away());
        var finished = new ApiMatchDetails(7, null, "FINISHED", 9, null, null, home, away,
                new ApiScore(new ApiScoreline(0, 2)));
        assertEquals(0, FootballMapper.details(finished).homeScore());
        assertEquals(2, FootballMapper.details(finished).awayScore());
    }
}

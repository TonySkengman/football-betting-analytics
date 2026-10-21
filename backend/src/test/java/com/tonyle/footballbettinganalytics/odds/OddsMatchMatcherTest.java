package com.tonyle.footballbettinganalytics.odds;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.tonyle.footballbettinganalytics.football.RecentPerformance.*;
import com.tonyle.footballbettinganalytics.football.FootballDtos.ApiTeam;

class OddsMatchMatcherTest {
    private SourceMatch match(String home, String away) {
        return new SourceMatch(1, "2026-10-24T14:00:00Z", "TIMED", new Competition("PL", "Premier League"), null,
                new ApiTeam(1, home, null, null), new ApiTeam(2, away, null, null), null);
    }
    private OddsService.Fixture event(String home, String away, String kickoff) {
        return new OddsService.Fixture("id", kickoff, home, away);
    }
    @Test void matchesKnownNameFormattingAndExplicitAliases() {
        var fixture = event("Brighton and Hove Albion", "Wolves", "2026-10-24T14:00:00Z");
        assertEquals(fixture, OddsMatchMatcher.find(match("Brighton & Hove Albion FC", "Wolverhampton Wanderers FC"), List.of(fixture)).orElseThrow());
    }
    @Test void refusesReversedTeamsDifferentKickoffAndUnknownName() {
        var selected = match("Arsenal FC", "Everton FC");
        assertTrue(OddsMatchMatcher.find(selected, List.of(event("Everton", "Arsenal", selected.utcDate()))).isEmpty());
        assertTrue(OddsMatchMatcher.find(selected, List.of(event("Arsenal", "Everton", "2026-10-24T14:01:00Z"))).isEmpty());
        assertTrue(OddsMatchMatcher.find(selected, List.of(event("Arsenal Women", "Everton", selected.utcDate()))).isEmpty());
        assertTrue(OddsMatchMatcher.find(selected, List.of(event("Arsenal", "Everton", null))).isEmpty());
    }
    @Test void refusesAmbiguousMatches() {
        var selected = match("Arsenal", "Everton");
        var fixture = event("Arsenal", "Everton", selected.utcDate());
        assertTrue(OddsMatchMatcher.find(selected, List.of(fixture, fixture)).isEmpty());
    }
}

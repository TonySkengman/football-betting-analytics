package com.tonyle.footballbettinganalytics.odds;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import com.tonyle.footballbettinganalytics.football.*;
import com.tonyle.footballbettinganalytics.football.RecentPerformance.*;
import com.tonyle.footballbettinganalytics.football.FootballDtos.ApiTeam;
import static org.junit.jupiter.api.Assertions.*;

class OddsControllerTest {
    private SourceMatch selected() {
        return new SourceMatch(42, "2099-01-01T15:00:00Z", "TIMED", new Competition("PL", "Premier League"), null,
                new ApiTeam(1, "Arsenal FC", null, null), new ApiTeam(2, "Everton FC", null, null), null);
    }
    private FootballService football() {
        return new FootballService("https://example.test", "test") {
            @Override public SourceMatch matchSource(long id) { assertEquals(42, id); return selected(); }
        };
    }
    @Test void usesVerifiedProviderEventIdForPaidLookup() {
        var odds = new OddsService(RestClient.create(), "test") {
            @Override public List<Fixture> events(String competition) {
                assertEquals("PL", competition);
                return List.of(new Fixture("verified", selected().utcDate(), "Arsenal", "Everton"));
            }
            @Override public AvailableOdds odds(String competition, String eventId) {
                assertEquals("PL", competition);
                assertEquals("verified", eventId);
                return new AvailableOdds(null, null, null, List.of(), List.of());
            }
        };
        assertNotNull(new OddsController(odds, football()).matchOdds(42));
    }
    @Test void refusesUnmatchedFixtureWithoutPaidLookup() {
        var odds = new OddsService(RestClient.create(), "test") {
            @Override public List<Fixture> events(String competition) { return List.of(); }
            @Override public AvailableOdds odds(String competition, String eventId) { fail("Must not spend credits for an unmatched fixture"); return null; }
        };
        assertEquals(404, assertThrows(FootballDataException.class, () -> new OddsController(odds, football()).matchOdds(42)).status().value());
    }
}

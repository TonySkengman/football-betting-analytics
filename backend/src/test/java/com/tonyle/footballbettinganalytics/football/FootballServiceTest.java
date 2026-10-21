package com.tonyle.footballbettinganalytics.football;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FootballServiceTest {
    @Test
    void matchMetadataIsSharedBetweenPerformanceAndOddsLookups() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/matches/42"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));
        var service = new FootballService(builder.build(), "test-key");
        var performanceMetadata = service.fetch("/matches/42", RecentPerformance.SourceMatch.class);
        assertSame(performanceMetadata, service.matchSource(42));
        server.verify();
    }

    @Test
    void repeatedListsReuseResponsesAndRateLimitsPauseOtherRequests() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/competitions/PL/matches"))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/competitions/PL/standings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var service = new FootballService(builder.build(), "test-key");
        assertTrue(service.matches().isEmpty());
        assertTrue(service.matches().isEmpty());
        assertThrows(FootballDataException.class, service::standings);
        assertEquals(429, assertThrows(FootballDataException.class, () -> service.matchSource(42)).status().value());
        // Already cached data remains usable during the cooldown.
        assertTrue(service.matches().isEmpty());
        server.verify();
    }

    @Test
    void authenticatesAndMapsRealisticJson() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/competitions/PL/matches"))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess("""
                    {"competition":{"code":"PL"},"matches":[
                      {"id":1,"utcDate":"2026-10-24T17:30:00Z","status":"TIMED","matchday":9,
                       "homeTeam":{"id":1,"name":"Arsenal","tla":"ARS","crest":"unused"},
                       "awayTeam":{"id":2,"name":"Chelsea","tla":"CHE"}},
                      {"id":2,"status":"FINISHED"}]}
                    """, MediaType.APPLICATION_JSON));
        var result = new FootballService(builder.build(), "test-key").matches();
        assertEquals(1, result.size());
        assertEquals("ARS", result.getFirst().home().code());
        server.verify();
    }

    @Test
    void handlesEmptyBody() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withNoContent());
        assertTrue(new FootballService(builder.build(), "test-key").standings().isEmpty());
        server.verify();
    }

    @Test
    void rateLimitDoesNotLeakProviderBody() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private-provider-detail"));
        var error = assertThrows(FootballDataException.class,
                () -> new FootballService(builder.build(), "test-key").matches());
        assertEquals(429, error.status().value());
        assertFalse(error.getMessage().contains("private-provider-detail"));
        server.verify();
    }

    @Test
    void networkFailureProducesSafeError() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withException(new java.io.IOException("connection failed")));
        var error = assertThrows(FootballDataException.class,
                () -> new FootballService(builder.build(), "test-key").standings());
        assertEquals(504, error.status().value());
        server.verify();
    }

    @Test
    void routesChampionsLeagueRequests() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/competitions/CL/matches"))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/competitions/CL/standings"))
                .andRespond(withSuccess("{\"standings\":[]}", MediaType.APPLICATION_JSON));
        var service = new FootballService(builder.build(), "test-key");
        assertTrue(service.matches("CL").isEmpty());
        assertTrue(service.standings("CL").isEmpty());
        server.verify();
    }

    @Test
    void rejectsUnsupportedCompetitionBeforeRequestingProvider() {
        var service = new FootballService(RestClient.create(), "test-key");
        var error = assertThrows(FootballDataException.class, () -> service.matches("INVALID"));
        assertEquals(400, error.status().value());
        assertThrows(FootballDataException.class, () -> service.standings("INVALID"));
    }

    @Test
    void missingChampionsLeagueTableReturnsEmptyButMatchesStillReportErrors() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        var service = new FootballService(builder.build(), "test-key");
        assertTrue(service.standings("CL").isEmpty());
        assertThrows(FootballDataException.class, () -> service.matches("CL"));
        server.verify();
    }

    @Test
    void fetchesSingleMatchAndMapsScore() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/matches/7"))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess("""
                    {"id":7,"status":"FINISHED","homeTeam":{"id":1,"name":"Home"},
                    "awayTeam":{"id":2,"name":"Away"},"score":{"fullTime":{"home":0,"away":2}}}
                    """, MediaType.APPLICATION_JSON));
        var result = new FootballService(builder.build(), "test-key").matchDetails(7);
        assertEquals(0, result.homeScore());
        assertEquals(2, result.awayScore());
        assertTrue(result.statistics().isEmpty());
        server.verify();
    }

    @Test
    void handlesMissingAndInvalidMatchIds() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withNoContent());
        var service = new FootballService(builder.build(), "test-key");
        assertEquals(404, assertThrows(FootballDataException.class, () -> service.matchDetails(7)).status().value());
        assertEquals(400, assertThrows(FootballDataException.class, () -> service.matchDetails(-1)).status().value());
        server.verify();
    }

    @Test
    void recentPerformanceFetchesSeasonOnceForBothTeams() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/matches/99"))
                .andRespond(withSuccess("""
                    {"id":99,"utcDate":"2026-10-21T15:00:00Z",
                     "competition":{"code":"CL","name":"Champions League"},
                     "season":{"startDate":"2026-09-01"},
                     "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"}}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/competitions/CL/matches?season=2026&status=FINISHED"))
                .andExpect(header("X-Auth-Token", "test-key"))
                .andRespond(withSuccess("""
                    {"matches":[{"id":1,"utcDate":"2026-10-01T15:00:00Z","status":"FINISHED",
                    "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                    "score":{"fullTime":{"home":2,"away":0}}}]}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/teams/1/matches?dateFrom=2025-10-21&dateTo=2026-10-22&status=FINISHED&limit=500"))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/teams/2/matches?dateFrom=2025-10-21&dateTo=2026-10-22&status=FINISHED&limit=500"))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
        var response = new FootballService(builder.build(), "test-key").recentPerformance(99);
        assertEquals("Champions League", response.competition());
        assertEquals(1, response.home().summary().wins());
        assertEquals(1, response.away().summary().losses());
        assertEquals(1, response.seasonPerformance().competition().played());
        assertEquals(2.0, response.seasonPerformance().home().home().averageScored());
        assertEquals(0.0, response.seasonPerformance().away().away().averageScored());
        server.verify();
    }
}

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
}

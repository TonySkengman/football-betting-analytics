package com.tonyle.footballbettinganalytics.odds;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.tonyle.footballbettinganalytics.football.FootballDataException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OddsServiceTest {
    private static final String ID = "0123456789abcdef0123456789abcdef";
    private static final String EVENT = "{\"id\":\"" + ID + "\",\"sport_key\":\"soccer_epl\",\"commence_time\":\"2099-01-01T15:00:00Z\",\"home_team\":\"Home\",\"away_team\":\"Away\"}";
    @Test void listsEventsAndCachesOddsWithBackendAuthentication() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v4/sports/soccer_epl/events?apiKey=test-key"))
                .andRespond(withSuccess("[" + EVENT + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v4/sports/soccer_epl/events/" + ID + "/odds?apiKey=test-key&regions=se&markets=h2h,totals,btts&oddsFormat=decimal"))
                .andRespond(withSuccess(EVENT, MediaType.APPLICATION_JSON).header("x-requests-remaining", "497"));
        var service = new OddsService(builder.build(), "test-key");
        assertEquals(1, service.events("PL").size());
        var odds = service.odds("PL", ID);
        assertEquals(List.of("h2h", "totals", "btts"), odds.missingMarkets());
        assertEquals(497, odds.creditsRemaining());
        assertSame(odds, service.odds("PL", ID));
        server.verify();
    }
    @Test void mapsOnlySupportedOutcomesAndExactTwoPointFiveLine() {
        var outcomes = List.of(new OddsService.Outcome("Over", 1.9, 2.5), new OddsService.Outcome("Under", 1.8, 3.5),
                new OddsService.Outcome("Over", Double.NaN, 2.5), new OddsService.Outcome("Under", 1.0, 2.5));
        var event = new OddsService.Event(ID, "soccer_epl", "2099-01-01T15:00:00Z", "Home", "Away",
                List.of(new OddsService.Bookmaker("unibet_se", "Unibet (SE)", "2098-12-31T00:00:00Z", List.of(new OddsService.Market("totals", null, outcomes)))));
        var result = OddsService.map(event, 100);
        assertEquals(1, result.prices().size());
        assertEquals("Over", result.prices().getFirst().outcome());
        assertEquals(1.9, result.prices().getFirst().decimalOdds());
        assertEquals(List.of("h2h", "btts"), result.missingMarkets());
        assertEquals("2098-12-31T00:00:00Z", result.prices().getFirst().updatedAt());
    }
    @Test void excludesNonSwedishBookmakerKeysEvenIfTitlesLookSwedish() {
        var market = new OddsService.Market("h2h", null, List.of(new OddsService.Outcome("Home", 1.9, null)));
        var books = List.of(
                new OddsService.Bookmaker("unibet_se", "Unibet (SE)", null, List.of(market)),
                new OddsService.Bookmaker("leovegas_se", "LeoVegas (SE)", null, List.of(market)),
                new OddsService.Bookmaker("svenskaspel_se", "Svenska Spel", null, List.of(market)),
                new OddsService.Bookmaker("atg_se", "ATG (SE)", null, List.of(market)),
                new OddsService.Bookmaker("unibet_fr", "Unibet (FR)", null, List.of(market)),
                new OddsService.Bookmaker("unknown", "Swedish Book (SE)", null, List.of(market)),
                new OddsService.Bookmaker("pinnacle", "Pinnacle", null, List.of(market)));
        var event = new OddsService.Event(ID, "soccer_epl", "2099-01-01T15:00:00Z", "Home", "Away", books);
        assertEquals(List.of("Unibet (SE)", "LeoVegas (SE)", "Svenska Spel", "ATG (SE)"), OddsService.map(event, 100).prices().stream().map(OddsService.Price::bookmaker).toList());
    }

    @Test void missingKeyAndInvalidInputsFailWithoutProviderCalls() {
        var service = new OddsService(RestClient.create(), "");
        assertEquals(503, assertThrows(FootballDataException.class, () -> service.events("PL")).status().value());
        assertEquals(400, assertThrows(FootballDataException.class, () -> service.events("OTHER")).status().value());
        assertEquals(400, assertThrows(FootballDataException.class, () -> service.odds("PL", "1234")).status().value());
    }
    @Test void providerErrorsNeverExposeKeyOrBodyAndCooldownStopsRepeatedRequests() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private test-key"));
        var service = new OddsService(builder.build(), "test-key");
        var error = assertThrows(FootballDataException.class, () -> service.events("PL"));
        assertFalse(error.getMessage().contains("test-key"));
        assertEquals(429, assertThrows(FootballDataException.class, () -> service.events("PL")).status().value());
        server.verify();
    }
    @Test void lowRemainingCreditsPreventPaidRequest() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("[" + EVENT + "]", MediaType.APPLICATION_JSON).header("x-requests-remaining", "2"));
        var service = new OddsService(builder.build(), "test-key");
        assertEquals(429, assertThrows(FootballDataException.class, () -> service.odds("PL", ID)).status().value());
        server.verify();
    }

    @Test void unknownEventNeverTriggersPaidLookup() {
        var builder = RestClient.builder().baseUrl("https://example.test/v4");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        var service = new OddsService(builder.build(), "test-key");
        assertEquals(404, assertThrows(FootballDataException.class, () -> service.odds("PL", ID)).status().value());
        server.verify();
    }
}

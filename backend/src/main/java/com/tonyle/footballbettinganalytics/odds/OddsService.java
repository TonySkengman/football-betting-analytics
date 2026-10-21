package com.tonyle.footballbettinganalytics.odds;

import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import com.tonyle.footballbettinganalytics.football.FootballDataException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class OddsService {
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String id, String sport_key, String commence_time, String home_team, String away_team, List<Bookmaker> bookmakers) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Bookmaker(String key, String title, String last_update, List<Market> markets) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Market(String key, String last_update, List<Outcome> outcomes) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Outcome(String name, Double price, Double point) {}
    public record Fixture(String eventId, String kickoff, String home, String away) {}
    public record Price(String bookmaker, String market, String outcome, double decimalOdds, String updatedAt) {}
    public record AvailableOdds(Fixture fixture, String fetchedAt, Integer creditsRemaining, List<Price> prices, List<String> missingMarkets) {}
    private record Cached<T>(Instant expires, T value) {}
    private static final Set<String> SWEDISH_BOOKMAKERS = Set.of(
            "atg_se", "betinia_se", "betmgm_se", "betsson", "campobet_se", "expekt_se",
            "hajper_se", "leovegas_se", "mrgreen_se", "nordicbet", "sport888_se", "svenskaspel_se", "unibet_se");
    private final RestClient client;
    private final String apiKey;
    private final Map<String, Cached<List<Fixture>>> events = new HashMap<>();
    private final Map<String, Cached<AvailableOdds>> cache = new LinkedHashMap<>();
    private Instant cooldown = Instant.MIN;
    private Integer remaining;
    private int paidAttempts;

    @org.springframework.beans.factory.annotation.Autowired
    public OddsService(@Value("${odds-api.base-url}") String baseUrl, @Value("${odds-api.api-key:}") String apiKey) {
        this(apiClient(baseUrl), apiKey);
    }
    OddsService(RestClient client, String apiKey) { this.client = client; this.apiKey = apiKey; }
    private static RestClient apiClient(String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    private String sport(String competition) {
        return switch (competition) {
            case "PL" -> "soccer_epl";
            case "CL" -> "soccer_uefa_champs_league";
            default -> throw new FootballDataException(HttpStatus.BAD_REQUEST, "Use competition PL or CL.");
        };
    }
    public synchronized List<Fixture> events(String competition) {
        String key = sport(competition);
        var cached = events.get(key);
        if (cached != null && cached.expires().isAfter(Instant.now())) return cached.value();
        var response = fetch("/sports/" + key + "/events", Event[].class, false);
        if (response == null) throw unreadable();
        var result = Arrays.stream(response).filter(Objects::nonNull).filter(e -> e.id() != null && e.home_team() != null && e.away_team() != null)
                .map(OddsService::fixture).toList();
        events.put(key, new Cached<>(Instant.now().plusSeconds(600), result));
        return result;
    }
    public synchronized AvailableOdds odds(String competition, String eventId) {
        String key = sport(competition);
        if (!eventId.matches("[a-fA-F0-9]{32}")) throw new FootballDataException(HttpStatus.BAD_REQUEST, "Use an event ID returned by /api/odds/events.");
        String cacheKey = key + "/" + eventId;
        var cached = cache.get(cacheKey);
        if (cached != null && cached.expires().isAfter(Instant.now())) return cached.value();
        // Never guess that a football-data.org ID is an Odds API event ID.
        var fixture = events(competition).stream().filter(e -> e.eventId().equals(eventId)).findFirst()
                .orElseThrow(() -> new FootballDataException(HttpStatus.NOT_FOUND, "No matching upcoming odds event found."));
        try {
            if (!Instant.parse(fixture.kickoff()).isAfter(Instant.now())) throw new FootballDataException(HttpStatus.BAD_REQUEST, "Only pre-match odds are supported.");
        } catch (DateTimeException | NullPointerException exception) { throw unreadable(); }
        var response = fetch("/sports/" + key + "/events/" + eventId + "/odds", Event.class, true);
        if (response == null || !eventId.equals(response.id()) || !key.equals(response.sport_key())
                || !fixture.equals(fixture(response))) throw unreadable();
        var result = map(response, remaining);
        if (cache.size() >= 50) cache.remove(cache.keySet().iterator().next());
        cache.put(cacheKey, new Cached<>(Instant.now().plusSeconds(600), result));
        return result;
    }
    static AvailableOdds map(Event event, Integer remaining) {
        var prices = new ArrayList<Price>();
        var seen = new HashSet<String>();
        for (var bookmaker : event.bookmakers() == null ? List.<Bookmaker>of() : event.bookmakers()) {
            if (bookmaker == null || bookmaker.title() == null || bookmaker.key() == null
                    || !SWEDISH_BOOKMAKERS.contains(bookmaker.key())) continue;
            for (var market : bookmaker.markets() == null ? List.<Market>of() : bookmaker.markets()) {
                if (market == null || !Set.of("h2h", "totals", "btts").contains(market.key() == null ? "" : market.key())) continue;
                for (var outcome : market.outcomes() == null ? List.<Outcome>of() : market.outcomes()) {
                    if (outcome == null || outcome.price() == null || !Double.isFinite(outcome.price()) || outcome.price() <= 1) continue;
                    boolean valid = switch (market.key()) {
                        case "h2h" -> Objects.equals(outcome.name(), event.home_team()) || Objects.equals(outcome.name(), event.away_team()) || "Draw".equals(outcome.name());
                        case "totals" -> Double.valueOf(2.5).equals(outcome.point()) && ("Over".equals(outcome.name()) || "Under".equals(outcome.name()));
                        default -> "Yes".equals(outcome.name()) || "No".equals(outcome.name());
                    };
                    if (!valid) continue;
                    seen.add(market.key());
                    prices.add(new Price(bookmaker.title(), market.key(), outcome.name(), outcome.price(), market.last_update() != null ? market.last_update() : bookmaker.last_update()));
                }
            }
        }
        return new AvailableOdds(fixture(event), Instant.now().toString(), remaining, List.copyOf(prices),
                List.of("h2h", "totals", "btts").stream().filter(m -> !seen.contains(m)).toList());
    }
    private static Fixture fixture(Event event) { return new Fixture(event.id(), event.commence_time(), event.home_team(), event.away_team()); }
    private static FootballDataException unreadable() { return new FootballDataException(HttpStatus.BAD_GATEWAY, "The odds provider returned incomplete or unexpected data."); }
    private <T> T fetch(String path, Class<T> type, boolean paid) {
        if (apiKey == null || apiKey.isBlank()) throw new FootballDataException(HttpStatus.SERVICE_UNAVAILABLE, "Set ODDS_API_KEY in the backend terminal and restart the backend.");
        if (cooldown.isAfter(Instant.now())) throw new FootballDataException(HttpStatus.TOO_MANY_REQUESTS, "Odds requests are temporarily paused. Please wait before retrying.");
        if (paid && (paidAttempts >= 20 || remaining != null && remaining < 3)) throw new FootballDataException(HttpStatus.TOO_MANY_REQUESTS, "Odds credit safety limit reached. No further paid requests were made.");
        if (paid) paidAttempts++;
        try {
            var entity = client.get().uri(builder -> {
                builder.path(path).queryParam("apiKey", apiKey.trim());
                if (paid) builder.queryParam("regions", "se").queryParam("markets", "h2h,totals,btts").queryParam("oddsFormat", "decimal");
                return builder.build();
            }).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                cooldown = Instant.now().plusSeconds(60);
                int status = response.getStatusCode().value();
                throw new FootballDataException(status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                        status == 401 || status == 403 ? "Odds access denied. Check the backend key and subscription." : "Odds provider request failed. Check coverage or quota and try later.");
            }).toEntity(type);
            try { remaining = Integer.valueOf(entity.getHeaders().getFirst("x-requests-remaining")); }
            catch (NumberFormatException exception) { /* Provider may omit quota headers. */ }
            return entity.getBody();
        } catch (RestClientException exception) {
            // Upstream errors can include the key in a URL. Never expose exception details.
            cooldown = Instant.now().plusSeconds(60);
            throw new FootballDataException(HttpStatus.BAD_GATEWAY, "Could not load odds from the provider. Please try later.");
        }
    }
}

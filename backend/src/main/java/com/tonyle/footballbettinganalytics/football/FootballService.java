package com.tonyle.footballbettinganalytics.football;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

@Service
public class FootballService {
    private final RestClient client;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public FootballService(@Value("${football-data.base-url}") String baseUrl,
                           @Value("${football-data.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    FootballService(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    public List<Match> matches() { return matches("PL"); }

    public List<Match> matches(String competition) {
        validateCompetition(competition);
        return FootballMapper.matches(fetch("/competitions/" + competition + "/matches", MatchesResponse.class)).stream()
                .filter(match -> "SCHEDULED".equals(match.status()) || "TIMED".equals(match.status()))
                .toList();
    }

    public List<Standing> standings() { return standings("PL"); }

    public List<Standing> standings(String competition) {
        validateCompetition(competition);
        try {
            return FootballMapper.standings(fetch("/competitions/" + competition + "/standings", StandingsResponse.class));
        } catch (FootballDataException exception) {
            // A knockout competition may not have a current standings table.
            if ("CL".equals(competition) && exception.status() == HttpStatus.NOT_FOUND) return List.of();
            throw exception;
        }
    }

    public MatchDetails matchDetails(long id) {
        if (id <= 0) throw new FootballDataException(HttpStatus.BAD_REQUEST, "Match ID must be positive.");
        var response = fetch("/matches/" + id, ApiMatchDetails.class);
        if (response == null || response.id() == 0) {
            throw new FootballDataException(HttpStatus.NOT_FOUND, "Match details are not available.");
        }
        return FootballMapper.details(response);
    }

    public RecentPerformance.Response recentPerformance(long id) {
        if (id <= 0) throw new FootballDataException(HttpStatus.BAD_REQUEST, "Match ID must be positive.");
        var selected = fetch("/matches/" + id, RecentPerformance.SourceMatch.class);
        if (selected == null || selected.id() == 0) {
            throw new FootballDataException(HttpStatus.NOT_FOUND, "Match not found.");
        }
        var cutoff = RecentPerformance.timestamp(selected.utcDate());
        if (cutoff == null || selected.competition() == null || selected.season() == null
                || selected.season().startDate() == null || selected.homeTeam() == null || selected.awayTeam() == null) {
            throw new FootballDataException(HttpStatus.BAD_GATEWAY, "Recent performance is unavailable because match metadata is incomplete.");
        }
        String competition = selected.competition().code();
        validateCompetition(competition);
        String season;
        try { season = String.valueOf(java.time.LocalDate.parse(selected.season().startDate()).getYear()); }
        catch (java.time.format.DateTimeParseException exception) {
            throw new FootballDataException(HttpStatus.BAD_GATEWAY, "The provider returned an invalid season.");
        }
        // Fetch the competition once for both teams, rather than making a separate request per team.
        var response = fetch("/competitions/" + competition + "/matches?season=" + season + "&status=FINISHED",
                RecentPerformance.SourceMatches.class);
        var matches = response == null || response.matches() == null ? List.<RecentPerformance.SourceMatch>of() : response.matches();
        return new RecentPerformance.Response(selected.competition().name(), season, selected.utcDate(),
                RecentPerformance.calculate(selected.homeTeam(), id, cutoff, matches),
                RecentPerformance.calculate(selected.awayTeam(), id, cutoff, matches));
    }

    private void validateCompetition(String competition) {
        if (!"PL".equals(competition) && !"CL".equals(competition)) {
            throw new FootballDataException(HttpStatus.BAD_REQUEST,
                    "Unsupported competition. Use PL (Premier League) or CL (Champions League).");
        }
    }

    private <T> T fetch(String path, Class<T> responseType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new FootballDataException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Football data is not configured. Set FOOTBALL_DATA_API_KEY on the backend.");
        }
        try {
            return client.get().uri(path).header("X-Auth-Token", apiKey.trim()).retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new FootballDataException(HttpStatus.TOO_MANY_REQUESTS,
                                "Football data rate limit reached. Please wait before trying again.");
                    })
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (request, response) -> {
                        throw new FootballDataException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Football data access was denied. Check the backend API key and subscription.");
                    })
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new FootballDataException(HttpStatus.NOT_FOUND,
                                "The requested football data is not available.");
                    })
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new FootballDataException(HttpStatus.BAD_GATEWAY,
                                "Football data request failed (provider HTTP " + response.getStatusCode().value()
                                        + "). Please try again later.");
                    })
                    .body(responseType);
        } catch (ResourceAccessException ex) {
            throw new FootballDataException(HttpStatus.GATEWAY_TIMEOUT,
                    "Could not reach the football data provider. Please try again later.");
        } catch (RestClientException ex) {
            // Never return upstream bodies or exception details, which may contain sensitive data.
            throw new FootballDataException(HttpStatus.BAD_GATEWAY,
                    "The football data provider returned an unreadable response.");
        }
    }
}

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

    public List<Match> matches() {
        return FootballMapper.matches(fetch("/competitions/PL/matches", MatchesResponse.class)).stream()
                .filter(match -> "SCHEDULED".equals(match.status()) || "TIMED".equals(match.status()))
                .toList();
    }

    public List<Standing> standings() {
        return FootballMapper.standings(fetch("/competitions/PL/standings", StandingsResponse.class));
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

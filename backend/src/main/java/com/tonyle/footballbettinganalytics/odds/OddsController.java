package com.tonyle.footballbettinganalytics.odds;

import java.util.List;
import java.time.Instant;
import com.tonyle.footballbettinganalytics.football.FootballService;
import com.tonyle.footballbettinganalytics.football.FootballDataException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/odds")
public class OddsController {
    private final OddsService service;
    private final FootballService football;
    public OddsController(OddsService service, FootballService football) { this.service = service; this.football = football; }

    @GetMapping("/matches/{matchId}")
    public OddsService.AvailableOdds matchOdds(@PathVariable long matchId) {
        var match = football.matchSource(matchId);
        if (match.competition() == null || !("PL".equals(match.competition().code()) || "CL".equals(match.competition().code())))
            throw new FootballDataException(HttpStatus.BAD_REQUEST, "Odds are supported for PL and CL only.");
        try {
            if (!List.of("SCHEDULED", "TIMED").contains(match.status()) || !Instant.parse(match.utcDate()).isAfter(Instant.now()))
                throw new FootballDataException(HttpStatus.BAD_REQUEST, "Only upcoming matches support bookmaker odds.");
        } catch (java.time.DateTimeException | NullPointerException exception) {
            throw new FootballDataException(HttpStatus.BAD_GATEWAY, "Match kickoff metadata is unavailable.");
        }
        String competition = match.competition().code();
        var event = OddsMatchMatcher.find(match, service.events(competition)).orElseThrow(() ->
                new FootballDataException(HttpStatus.NOT_FOUND, "No unique matching odds fixture found. You can still enter odds manually."));
        return service.odds(competition, event.eventId());
    }
    @GetMapping("/events")
    public List<OddsService.Fixture> events(@RequestParam(defaultValue = "PL") String competition) {
        return service.events(competition);
    }
    @GetMapping("/events/{eventId}/diagnostics")
    public List<OddsService.BookmakerDiagnostic> diagnostics(@PathVariable String eventId,
                                                           @RequestParam(defaultValue = "PL") String competition) {
        return service.odds(competition, eventId).diagnostics();
    }
    @GetMapping("/events/{eventId}")
    public OddsService.AvailableOdds odds(@PathVariable String eventId, @RequestParam(defaultValue = "PL") String competition) {
        return service.odds(competition, eventId);
    }
}

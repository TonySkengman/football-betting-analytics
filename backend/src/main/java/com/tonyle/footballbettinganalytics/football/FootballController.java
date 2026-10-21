package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

@RestController
@RequestMapping("/api")
public class FootballController {
    private final FootballService service;

    public FootballController(FootballService service) { this.service = service; }

    @GetMapping("/matches")
    public List<Match> matches(@RequestParam(defaultValue = "PL") String competition) {
        return service.matches(competition);
    }

    @GetMapping("/standings")
    public List<Standing> standings(@RequestParam(defaultValue = "PL") String competition) {
        return service.standings(competition);
    }
    @GetMapping("/matches/{id}")
    public MatchDetails matchDetails(@PathVariable long id) {
        return service.matchDetails(id);
    }
    @GetMapping("/matches/{id}/performance")
    public RecentPerformance.Response recentPerformance(@PathVariable long id) {
        return service.recentPerformance(id);
    }
}

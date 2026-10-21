package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

@RestController
@RequestMapping("/api")
public class FootballController {
    private final FootballService service;

    public FootballController(FootballService service) { this.service = service; }

    @GetMapping("/matches")
    public List<Match> matches() { return service.matches(); }

    @GetMapping("/standings")
    public List<Standing> standings() { return service.standings(); }
}

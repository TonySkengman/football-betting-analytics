package com.tonyle.footballbettinganalytics.odds;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import com.tonyle.footballbettinganalytics.football.RecentPerformance.SourceMatch;

final class OddsMatchMatcher {
    private OddsMatchMatcher() {}
    private static String name(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\b(fc|afc)\\b", " ").replaceAll("\\s+", " ").trim();
        // Explicit aliases only. Unknown names fall back to manual entry instead of fuzzy matching.
        return switch (normalized) {
            case "wolves" -> "wolverhampton wanderers";
            case "bayern munchen", "bayern munich" -> "bayern munich";
            case "internazionale milano", "inter milan" -> "inter milan";
            case "sporting clube de portugal", "sporting lisbon" -> "sporting lisbon";
            default -> normalized;
        };
    }
    static Optional<OddsService.Fixture> find(SourceMatch match, List<OddsService.Fixture> fixtures) {
        if (match.homeTeam() == null || match.awayTeam() == null || match.utcDate() == null) return Optional.empty();
        var home = name(match.homeTeam().name());
        var away = name(match.awayTeam().name());
        if (home.isEmpty() || away.isEmpty()) return Optional.empty();
        var matches = fixtures.stream().filter(f -> home.equals(name(f.home())) && away.equals(name(f.away())))
                .filter(f -> sameKickoff(match.utcDate(), f.kickoff())).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
    private static boolean sameKickoff(String first, String second) {
        try { return Instant.parse(first).equals(Instant.parse(second)); }
        catch (RuntimeException exception) { return false; }
    }
}

package io.sharpen.web;

import io.sharpen.service.TrafficService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Countries and continents from the grouped page views, and the pie built from them. No Spring context. */
class TrafficPlacesTest {

    private static Object[] row(String country, long views, long visitors) { return new Object[]{country, views, visitors}; }

    @Test
    void sharesAreOfLocatedVisitorsAndContinentsAddUp() {
        TrafficService.Places p = TrafficService.places(List.of(
                row("IN", 30, 6), row(null, 50, 9), row("US", 12, 3), row("ZZ", 4, 2), row("GB", 5, 1)));
        assertThat(p.located()).isEqualTo(10);
        assertThat(p.unknown()).isEqualTo(2);
        assertThat(p.notRecorded()).isEqualTo(9);
        assertThat(p.countries()).extracting(TrafficService.Place::name).containsExactly("India", "United States", "United Kingdom");
        assertThat(p.countries()).extracting(TrafficService.Place::percent).containsExactly("60%", "30%", "10%");
        assertThat(p.continents()).extracting(TrafficService.Place::name).containsExactly("Asia", "North America", "Europe");
        assertThat(p.continents().get(0).views()).isEqualTo(30);
    }

    @Test
    void aTinyShareReadsAsLessThanOnePercent() {
        TrafficService.Places p = TrafficService.places(List.of(row("IN", 400, 399), row("FR", 1, 1)));
        assertThat(p.countries().get(1).percent()).isEqualTo("<1%");
    }

    @Test
    void thePieNamesFiveCountriesAndFoldsTheRestIntoOther() {
        List<Object[]> rows = new ArrayList<>();
        String[] codes = {"IN", "US", "GB", "DE", "CA", "AU", "FR", "JP"};
        for (int i = 0; i < codes.length; i++) rows.add(row(codes[i], 10L * (codes.length - i), codes.length - i));
        List<TrafficController.Slice> pie = TrafficController.pie(TrafficService.places(rows));
        assertThat(pie).hasSize(6);
        assertThat(pie).extracting(TrafficController.Slice::slot).containsExactly("1", "2", "3", "4", "5", "other");
        assertThat(pie.get(5).label()).isEqualTo("Other countries (3)");
        assertThat(pie.get(5).visitors()).isEqualTo(3 + 2 + 1);
        assertThat(pie).allSatisfy(s -> assertThat(s.path()).startsWith("M90.0 90.0 L"));
    }

    @Test
    void oneCountryIsAWholeCircleAndNoCountriesNoPie() {
        List<TrafficController.Slice> pie = TrafficController.pie(TrafficService.places(List.<Object[]>of(row("IN", 3, 2))));
        assertThat(pie).hasSize(1);
        assertThat(pie.get(0).percent()).isEqualTo("100%");
        assertThat(pie.get(0).path()).startsWith("M90.0 0 A90.0 90.0 0 1 1 90.0 180.0");
        assertThat(TrafficController.pie(TrafficService.places(List.<Object[]>of(row(null, 3, 2))))).isEmpty();
    }

    @Test
    void sliceAnglesFollowTheValues() {
        // Half and half: the first slice ends straight down, the second sweeps back to the top
        List<String> paths = Charts.pieSlices(List.of(5L, 5L), 90);
        assertThat(paths.get(0)).isEqualTo("M90.0 90.0 L90.0 0.0 A90.0 90.0 0 0 1 90.0 180.0 Z");
        assertThat(paths.get(1)).isEqualTo("M90.0 90.0 L90.0 180.0 A90.0 90.0 0 0 1 90.0 0.0 Z");
        // Three quarters takes the large arc
        assertThat(Charts.pieSlices(List.of(3L, 1L), 90).get(0)).contains(" 0 1 1 ");
    }
}

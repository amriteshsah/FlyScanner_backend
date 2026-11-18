package com.flyscanner.platform;

import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.PassengerRequest;
import com.flyscanner.search.application.FlightSearchService;
import com.flyscanner.search.domain.CabinClass;
import com.flyscanner.search.domain.TripType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class LocalDatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FlightSearchService flightSearchService;

    @Test
    void flywayCreatesTheModuleSchemasAndDomainTablesInH2() {
        assertSchemaExists("account");
        assertSchemaExists("search");
        assertSchemaExists("affiliate");
        assertSchemaExists("alerts");
        assertSchemaExists("notifications");

        assertTableExists("account", "users");
        assertTableExists("search", "flight_searches");
        assertTableExists("search", "flight_legs");
        assertTableExists("search", "search_children");
        assertTableExists("search", "provider_search_statuses");
        assertTableExists("search", "flight_offers");
        assertTableExists("search", "flight_journeys");
        assertTableExists("search", "flight_segments");
        assertTableExists("affiliate", "click_tracking");
        assertTableExists("alerts", "price_alerts");
        assertTableExists("notifications", "notification_history");
    }

    @Test
    void savesSearchOffersJourneysAndSegmentsToH2() {
        FlightSearchRequest request = new FlightSearchRequest(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.now().plusDays(20))),
                new PassengerRequest(1, List.of(6), 0),
                CabinClass.ECONOMY,
                "INR",
                "IN",
                "en-IN"
        );

        var response = flightSearchService.search(request);
        String searchId = response.searchId().toString();

        assertEquals(1, count("SELECT COUNT(*) FROM search.flight_searches WHERE id = ?", searchId));
        assertEquals(1, count("SELECT COUNT(*) FROM search.flight_legs WHERE search_id = ?", searchId));
        assertEquals(1, count("SELECT COUNT(*) FROM search.search_children WHERE search_id = ?", searchId));
        assertEquals(1, count("SELECT COUNT(*) FROM search.provider_search_statuses WHERE search_id = ?", searchId));
        assertEquals(3, count("SELECT COUNT(*) FROM search.flight_offers WHERE search_id = ?", searchId));
        assertEquals(3, count("""
                SELECT COUNT(*) FROM search.flight_journeys journey
                JOIN search.flight_offers offer ON offer.offer_token = journey.offer_token
                WHERE offer.search_id = ?
                """, searchId));
        assertEquals(6, count("""
                SELECT COUNT(*) FROM search.flight_segments segment
                JOIN search.flight_offers offer ON offer.offer_token = segment.offer_token
                WHERE offer.search_id = ?
                """, searchId));
    }

    private void assertSchemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?",
                Integer.class,
                schemaName
        );
        assertTrue(count != null && count > 0, "Expected schema " + schemaName);
    }

    private void assertTableExists(String schemaName, String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """, Integer.class, schemaName, tableName);
        assertTrue(count != null && count > 0, "Expected table " + schemaName + "." + tableName);
    }

    private int count(String sql, String searchId) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, searchId);
        return count == null ? 0 : count;
    }
}

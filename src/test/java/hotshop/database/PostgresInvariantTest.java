package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;

import hotshop.support.PostgresFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresInvariantTest {
    private PostgresFixture fixture;
    private Database database;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new PostgresFixture();
        database = new Database(new DatabaseConfig(fixture.jdbcUrl(), fixture.username(), fixture.password()));
        database.migrate();
        execute("INSERT INTO users(id,username,normalized_username,display_name) "
                + "VALUES ('u','Alice','alice','Alice'),('b','Bob','bob','Bob')");
        execute("INSERT INTO listings (id,seller_id,title,description,category,price_cents,condition,"
                + "pickup_location,status,created_at,updated_at) "
                + "VALUES ('l','u','T','D','OTHER',100,'NEW','Campus','AVAILABLE',0,0)");
        for (int index = 1; index <= 3; index++) {
            execute("INSERT INTO offers VALUES ('o" + index + "','l','b',100,'ACCEPTED',0,1)");
        }
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void migrate_activeSales_allowsHistoricalAndRejectsSecondActive() throws Exception {
        execute(sale("t0", "o1", "CANCELLED"));
        execute(sale("t1", "o2", "ACTIVE"));
        assertConflict(sale("t2", "o3", "ACTIVE"), "23505");
        assertCount("transactions", 2);
    }

    @Test
    void migrate_pendingCancellations_allowsResolvedAndRejectsSecondPending() throws Exception {
        execute(sale("t1", "o1", "ACTIVE"));
        execute(request("r0", "REJECTED", "2"));
        execute(request("r1", "PENDING", "NULL"));
        assertConflict(request("r2", "PENDING", "NULL"), "23505");
        assertCount("cancellation_requests", 2);
    }

    @Test
    void migrate_scheduledMeetups_allowsCancelledAndRejectsSecondScheduled() throws Exception {
        execute(sale("t1", "o1", "ACTIVE"));
        execute(meetup("m0", "CANCELLED"));
        execute(meetup("m1", "SCHEDULED"));
        assertConflict(meetup("m2", "SCHEDULED"), "23505");
        assertCount("meetups", 2);
    }

    @Test
    void migrate_pendingReschedules_allowsResolvedAndRejectsSecondPending() throws Exception {
        execute(sale("t1", "o1", "ACTIVE"));
        execute(meetup("m1", "SCHEDULED"));
        execute(proposal("p0", "REJECTED", "2"));
        execute(proposal("p1", "PENDING", "NULL"));
        assertConflict(proposal("p2", "PENDING", "NULL"), "23505");
        assertCount("meetup_reschedule_proposals", 2);
    }

    @Test
    void migrate_conversations_rejectsDuplicateBuyerAndListing() throws Exception {
        execute(conversation("c1", "b", "u"));
        assertConflict(conversation("c2", "b", "u"), "23505");
        assertCount("conversations", 1);
    }

    @Test
    void migrate_conversations_rejectsIdenticalParticipants() throws Exception {
        execute(conversation("c1", "b", "u"));
        assertConflict(conversation("c2", "u", "u"), "23514");
        assertCount("conversations", 1);
    }

    @Test
    void migrate_messageSequences_acceptsDistinctAndRejectsDuplicate() throws Exception {
        execute(conversation("c1", "b", "u"));
        execute("INSERT INTO messages VALUES ('m1','c1','b',1,'Hello',0)");
        execute("INSERT INTO messages VALUES ('m2','c1','u',2,'Reply',0)");
        assertConflict("INSERT INTO messages VALUES ('m3','c1','b',2,'Duplicate',0)", "23505");
        assertCount("messages", 2);
    }

    @Test
    void migrate_messageSequences_rejectsZero() throws Exception {
        execute(conversation("c1", "b", "u"));
        execute("INSERT INTO messages VALUES ('m1','c1','b',1,'Hello',0)");
        assertConflict("INSERT INTO messages VALUES ('m0','c1','b',0,'Zero',0)", "23514");
        assertCount("messages", 1);
    }

    private void assertConflict(String sql, String state) {
        SQLException failure = assertThrows(SQLException.class, () -> execute(sql));
        assertEquals(state, failure.getSQLState());
    }

    private void assertCount(String table, int expected) throws SQLException {
        int count = database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rows.next();
                return rows.getInt(1);
            }
        });
        assertEquals(expected, count);
    }

    private void execute(String sql) throws SQLException {
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    private static String sale(String id, String offer, String status) {
        return "INSERT INTO transactions (id,listing_id,accepted_offer_id,buyer_id,seller_id,agreed_price_cents,"
                + "listing_title,listing_description,listing_condition,created_at,status) VALUES ('"
                + id + "','l','" + offer + "','b','u',100,'T','D','NEW',0,'" + status + "')";
    }

    private static String request(String id, String status, String resolved) {
        return "INSERT INTO cancellation_requests VALUES ('" + id + "','t1','b',1,'" + status + "',"
                + resolved + ")";
    }

    private static String meetup(String id, String status) {
        return "INSERT INTO meetups VALUES ('" + id + "','t1',10,20,'Campus','" + status + "',1)";
    }

    private static String proposal(String id, String status, String resolved) {
        return "INSERT INTO meetup_reschedule_proposals VALUES ('" + id + "','m1','b',30,40,'Library',1,'"
                + status + "'," + resolved + ")";
    }

    private static String conversation(String id, String buyer, String seller) {
        return "INSERT INTO conversations(id,listing_id,buyer_id,seller_id,created_at) VALUES ('" + id
                + "','l','" + buyer + "','" + seller + "',0)";
    }
}

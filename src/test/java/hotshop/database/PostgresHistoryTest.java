package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import hotshop.repository.MeetupRepository;
import hotshop.repository.TransactionRepository;
import hotshop.support.PostgresTestSupport;

import org.junit.jupiter.api.Test;

class PostgresHistoryTest extends PostgresTestSupport {
    private static final String SELLER = "00000000-0000-0000-0000-000000000001";
    private static final String BUYER = "00000000-0000-0000-0000-000000000002";
    private static final String LISTING = "00000000-0000-0000-0000-000000000003";
    private static final String OFFER = "00000000-0000-0000-0000-000000000004";
    private static final String SALE = "00000000-0000-0000-0000-000000000005";
    private static final String MEETUP = "00000000-0000-0000-0000-000000000006";
    private static final String FIRST = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    private static final String SECOND = "00000000-0000-0000-0000-000000000000";

    @Test
    void readRequests_tiedApplicationClock_preservesInsertionOrderAndPendingLast() throws Exception {
        seedSale();
        execute("INSERT INTO cancellation_requests VALUES ('" + FIRST + "','" + SALE + "','" + BUYER
                + "',1,'REJECTED',1),('" + SECOND + "','" + SALE + "','" + BUYER + "',1,'PENDING',NULL)");
        new Database(databaseConfig).executeTransaction(connection -> {
            var sale = new TransactionRepository().findById(connection, UUID.fromString(SALE)).orElseThrow();
            assertEquals(List.of(UUID.fromString(FIRST), UUID.fromString(SECOND)),
                    sale.getCancellationRequests().stream().map(request -> request.getId()).toList());
            assertTrue(sale.getPendingCancellation().isPresent());
            return null;
        });
    }

    @Test
    void readProposals_tiedApplicationClock_preservesInsertionOrderAndPendingLast() throws Exception {
        seedSale();
        execute("INSERT INTO meetups VALUES ('" + MEETUP + "','" + SALE + "',1000000,2000000,'Library','SCHEDULED',1)");
        execute("INSERT INTO meetup_reschedule_proposals VALUES ('" + FIRST + "','" + MEETUP + "','" + BUYER
                + "',3000000,4000000,'Library',1,'REJECTED',1),('" + SECOND + "','" + MEETUP + "','" + BUYER
                + "',5000000,6000000,'Library',1,'PENDING',NULL)");
        new Database(databaseConfig).executeTransaction(connection -> {
            var meetup = new MeetupRepository().findMeetup(connection, UUID.fromString(MEETUP)).orElseThrow();
            assertEquals(List.of(UUID.fromString(FIRST), UUID.fromString(SECOND)),
                    meetup.getProposals().stream().map(proposal -> proposal.getId()).toList());
            assertTrue(meetup.getPendingProposal().isPresent());
            return null;
        });
    }

    private void seedSale() throws Exception {
        execute("INSERT INTO users(id,username,normalized_username,display_name) VALUES ('" + SELLER
                + "','alice','alice','Alice'),('" + BUYER + "','bobby','bobby','Bob')");
        execute("INSERT INTO listings VALUES ('" + LISTING + "','" + SELLER
                + "','Desk','Wooden desk','FURNITURE',100,'GOOD','Library','RESERVED',0,0)");
        execute("INSERT INTO offers VALUES ('" + OFFER + "','" + LISTING + "','" + BUYER
                + "',100,'ACCEPTED',0,1)");
        execute("INSERT INTO transactions(id,listing_id,accepted_offer_id,buyer_id,seller_id,agreed_price_cents,"
                + "listing_title,listing_description,listing_condition,created_at,status,buyer_confirmed_at) VALUES ('"
                + SALE + "','" + LISTING + "','" + OFFER + "','" + BUYER + "','" + SELLER
                + "',100,'Desk','Wooden desk','GOOD',1,'ACTIVE',1)");
    }

    private void execute(String sql) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

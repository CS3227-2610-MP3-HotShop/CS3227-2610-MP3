package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import hotshop.database.Database;
import hotshop.repository.ChatRepository;
import hotshop.repository.ListingRepository;
import hotshop.repository.MeetupRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;
import hotshop.support.PostgresTestSupport;

import org.junit.jupiter.api.Test;

/** Real services with independent workers/connections; runtime directory locking remains intact. */
class PostgresConcurrencyTest extends PostgresTestSupport {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
    private static final long WRITE_LOCK = 78231L;
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID listing = UUID.randomUUID();

    @Test
    void acceptOffer_twoConnections_commitsOnlyOneCompleteSale() throws Exception {
        seedMarketplace();
        UUID first = seedOffer(buyer);
        UUID second = seedOffer(other);
        blockWrites("offers", "UPDATE");
        try (Services one = services(seller); Services two = services(seller)) {
            race(() -> one.offers.acceptOffer(first), () -> two.offers.acceptOffer(second));
        }
        assertEquals(1, count("transactions WHERE status='ACTIVE'"));
        assertEquals(1, count("offers WHERE status='ACCEPTED'"));
        assertEquals(1, count("offers WHERE status='REJECTED'"));
        assertEquals(1, count("listings WHERE status='RESERVED'"));
    }

    @Test
    void bookSlot_twoConnections_commitsOneMeetupAndRemovesSlots() throws Exception {
        seedMarketplace();
        UUID offer = seedOffer(buyer);
        UUID sale;
        try (Services service = services(seller)) {
            sale = service.offers.acceptOffer(offer).join().transactionId();
        }
        UUID slot;
        try (Services service = services(seller)) {
            slot = service.meetups.offerSlot(sale, CLOCK.instant().plusSeconds(3600),
                    CLOCK.instant().plusSeconds(7200), "Library").join().offeredSlots().get(0).id();
        }
        blockWrites("meetups", "INSERT");
        try (Services one = services(buyer); Services two = services(buyer)) {
            race(() -> one.meetups.bookSlot(slot), () -> two.meetups.bookSlot(slot));
        }
        assertEquals(1, count("meetups WHERE status='SCHEDULED'"));
        assertEquals(0, count("meetup_slots"));
    }

    @Test
    void sendMessage_twoConnections_preservesUniqueSequenceAndNoPartialLoser() throws Exception {
        seedMarketplace();
        UUID conversation;
        try (Services service = services(buyer)) {
            conversation = service.chats.messageSeller(listing, "Initial").join().summary().conversation().getId();
        }
        blockWrites("messages", "INSERT");
        try (Services one = services(buyer); Services two = services(seller)) {
            race(() -> one.chats.sendMessage(conversation, "First"),
                    () -> two.chats.sendMessage(conversation, "Second"));
        }
        assertEquals(2, count("messages"));
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(DISTINCT sequence), MAX(sequence) FROM messages")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
            assertEquals(2, rows.getLong(2));
        }
    }

    private void seedMarketplace() throws Exception {
        execute("INSERT INTO users(id,username,normalized_username,display_name) VALUES ('" + seller
                + "','alice','alice','Alice'),('" + buyer + "','bobby','bobby','Bob'),('" + other
                + "','carol','carol','Carol')");
        execute("INSERT INTO listings VALUES ('" + listing + "','" + seller
                + "','Desk','Wooden desk','FURNITURE',100,'GOOD','Library','AVAILABLE',0,0)");
    }

    private UUID seedOffer(UUID user) throws Exception {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO offers VALUES ('" + id + "','" + listing + "','" + user
                + "',100,'PENDING',0,NULL)");
        return id;
    }

    private void blockWrites(String table, String event) throws Exception {
        execute("CREATE FUNCTION pause_test_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "PERFORM pg_advisory_xact_lock(" + WRITE_LOCK + "); RETURN NEW; END $$");
        execute("CREATE TRIGGER pause_write BEFORE " + event + " ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION pause_test_write()");
    }

    private void race(Supplier<CompletableFuture<?>> first, Supplier<CompletableFuture<?>> second) throws Exception {
        CompletableFuture<?> left;
        CompletableFuture<?> right;
        try (var lock = openDatabaseConnection(); var statement = lock.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(" + WRITE_LOCK + ")");
            left = first.get();
            right = second.get();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean bothWaiting = false;
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM pg_locks WHERE locktype='advisory' "
                        + "AND objid=" + WRITE_LOCK + " AND NOT granted")) {
                    rows.next();
                    if (rows.getInt(1) == 2) {
                        bothWaiting = true;
                        break;
                    }
                }
                Thread.sleep(10);
            }
            assertTrue(bothWaiting, "Both independent service transactions must reach the write barrier");
        }
        int winners = 0;
        for (CompletableFuture<?> result : List.of(left, right)) {
            Throwable failure = result.handle((value, error) -> error).get(30, TimeUnit.SECONDS);
            if (failure == null) {
                winners++;
            } else {
                while (failure instanceof java.util.concurrent.CompletionException) {
                    failure = failure.getCause();
                }
                assertTrue(failure instanceof ServiceException);
                ServiceException service = (ServiceException) failure;
                assertEquals(ServiceException.Code.STORAGE, service.getCode());
                assertTrue(service.getMessage().contains("retry"));
            }
        }
        assertEquals(1, winners);
    }

    private int count(String source) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + source)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Services services(UUID user) {
        return new Services(new Database(databaseConfig), user);
    }

    /** Wiring only; each worker exercises the actual production services and repositories. */
    private static final class Services implements AutoCloseable {
        private final ServiceWorker worker = new ServiceWorker();
        private final OfferService offers;
        private final MeetupService meetups;
        private final ChatService chats;

        private Services(Database database, UUID user) {
            AuthenticatedSession session = new AuthenticatedSession();
            worker.submit(() -> {
                session.login(user);
                return null;
            }).join();
            offers = new OfferService(database, new OfferRepository(), new ListingRepository(),
                    new TransactionRepository(), new ChatRepository(), new UserRepository(), worker, session, CLOCK);
            meetups = new MeetupService(database, new MeetupRepository(), new TransactionRepository(),
                    worker, session, CLOCK);
            chats = new ChatService(database, new ChatRepository(), new ListingRepository(), new OfferRepository(),
                    new TransactionRepository(), new UserRepository(), worker, session, CLOCK);
        }

        @Override
        public void close() {
            worker.close();
        }
    }
}

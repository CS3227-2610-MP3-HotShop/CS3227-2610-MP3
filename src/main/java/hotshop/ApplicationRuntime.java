package hotshop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.CompletionException;

import hotshop.database.Database;
import hotshop.database.DatabaseConfig;
import hotshop.repository.ChatRepository;
import hotshop.repository.ListingRepository;
import hotshop.repository.MeetupRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;
import hotshop.service.AccountService;
import hotshop.service.AuthenticatedSession;
import hotshop.service.ChatService;
import hotshop.service.ListingService;
import hotshop.service.MeetupService;
import hotshop.service.OfferService;
import hotshop.service.ServiceWorker;
import hotshop.service.TransactionService;
import hotshop.storage.ImageStorage;
import hotshop.storage.SupabaseImageObjectStore;
import hotshop.storage.SupabaseStorageConfig;

/** Owns application resources for exactly one local data directory. */
public final class ApplicationRuntime implements AutoCloseable {
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServiceWorker worker = new ServiceWorker();
    private final AccountService accounts;
    private final ListingService listings;
    private final OfferService offers;
    private final TransactionService transactions;
    private final MeetupService meetups;
    private final ChatService chats;
    private final ImageStorage profileImages;
    private final ImageStorage listingImages;
    private final SupabaseImageObjectStore remoteImageStorage;
    private boolean isClosed;

    private ApplicationRuntime(FileChannel lockChannel, FileLock lock, Database database, ImageStorage profileImages,
            ImageStorage listingImages, SupabaseImageObjectStore remoteImageStorage, Clock clock) {
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.profileImages = profileImages;
        this.listingImages = listingImages;
        this.remoteImageStorage = remoteImageStorage;
        UserRepository users = new UserRepository();
        AuthenticatedSession session = new AuthenticatedSession();
        accounts = new AccountService(database, users, worker, session, profileImages, clock);
        ListingRepository listingRepository = new ListingRepository();
        OfferRepository offerRepository = new OfferRepository();
        ChatRepository chatRepository = new ChatRepository();
        TransactionRepository transactionRepository = new TransactionRepository();
        MeetupRepository meetupRepository = new MeetupRepository();
        listings = new ListingService(database, listingRepository, offerRepository, transactionRepository,
                meetupRepository, chatRepository, users, worker, session, listingImages, clock);
        offers = new OfferService(database, offerRepository, listingRepository, transactionRepository,
                chatRepository, users, worker, session, clock);
        transactions = new TransactionService(database, transactionRepository, listingRepository, offerRepository,
                meetupRepository, users, worker, session, clock);
        meetups = new MeetupService(database, meetupRepository, transactionRepository, worker, session, clock);
        chats = new ChatService(database, chatRepository, listingRepository, offerRepository, transactionRepository,
                users, worker, session, clock);
    }

    /**
     * Returns the shared account service.
     *
     * @return the shared account service
     */
    public AccountService getAccounts() {
        return accounts;
    }

    /**
     * Returns the shared listing service.
     *
     * @return the shared listing service
     */
    public ListingService getListings() {
        return listings;
    }

    /**
     * Returns the shared offer service.
     *
     * @return the shared offer service
     */
    public OfferService getOffers() {
        return offers;
    }

    /**
     * Returns the shared agreed-sale service.
     *
     * @return the shared agreed-sale service
     */
    public TransactionService getTransactions() {
        return transactions;
    }

    /**
     * Returns the shared meetup service.
     *
     * @return the shared meetup service
     */
    public MeetupService getMeetups() {
        return meetups;
    }

    /**
     * Returns the shared chat service.
     *
     * @return the shared chat service
     */
    public ChatService getChats() {
        return chats;
    }

    /**
     * Resolves a generated image reference without exposing database or storage mutation to the UI.
     *
     * @param filename the storage-relative image filename
     * @return the resolved path in the profile image namespace; existence is not checked
     * @throws IllegalArgumentException if the name is not generated by this storage or the path is a symbolic link
     */
    public Path getProfileImagePath(String filename) {
        if (profileImages == null) {
            throw new IllegalStateException("Private images do not have local paths");
        }
        return profileImages.resolve(filename);
    }

    /**
     * Resolves a generated listing-image reference within the listing namespace.
     *
     * @param filename the storage-relative image filename
     * @return the resolved path in the listing image namespace; existence is not checked
     * @throws IllegalArgumentException if the name is not generated by this storage or the path is a symbolic link
     */
    public Path getListingImagePath(String filename) {
        if (listingImages == null) {
            throw new IllegalStateException("Private images do not have local paths");
        }
        return listingImages.resolve(filename);
    }

    /**
     * Locks and initializes the data directory using the system clock in the computer's time zone.
     *
     * @param directory the local application data directory
     * @return the initialized runtime, which the caller must close
     * @throws IOException if the directory is already in use or filesystem initialization fails
     * @throws SQLException if database access fails
     */
    public static ApplicationRuntime open(Path directory) throws IOException, SQLException {
        return open(directory, Clock.systemDefaultZone());
    }

    /**
     * Locks and initializes the data directory; failure preserves data and releases acquired resources.
     *
     * @param directory the local application data directory
     * @param clock the clock supplying event times and the local time zone
     * @return the initialized runtime, which the caller must close
     * @throws IOException if the directory is already in use or filesystem initialization fails
     * @throws SQLException if database access fails
     */
    public static ApplicationRuntime open(Path directory, Clock clock) throws IOException, SQLException {
        return open(directory, clock, DatabaseConfig.fromEnvironment(System.getenv()),
                SupabaseStorageConfig.fromEnvironment(System.getenv()));
    }

    /**
     * Opens a runtime against an explicitly configured, already provisioned PostgreSQL database.
     *
     * @param directory the local image storage and lock directory
     * @param clock the application event clock
     * @param config the PostgreSQL connection configuration
     * @return the runtime, which the caller must close
     * @throws IOException if filesystem initialization fails or the directory is locked
     * @throws SQLException if connectivity or schema validation fails
     */
    public static ApplicationRuntime open(Path directory, Clock clock, DatabaseConfig config)
            throws IOException, SQLException {
        return open(directory, clock, config, null);
    }

    /** Opens the production runtime with an explicit private Supabase storage configuration. */
    public static ApplicationRuntime open(Path directory, Clock clock, DatabaseConfig config,
            SupabaseStorageConfig storageConfig) throws IOException, SQLException {
        Files.createDirectories(directory);
        FileChannel channel = FileChannel.open(directory.resolve("application.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        SupabaseImageObjectStore remoteStorage = null;
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                throw new IOException("HotShop is already using this data directory", exception);
            }
            if (lock == null) {
                throw new IOException("HotShop is already using this data directory");
            }
            Database database = new Database(config);
            try {
                database.validateSchema();
                if (storageConfig != null) {
                    database.validateImageStorageBindings(storageConfig);
                }
            } catch (SQLException failure) {
                throw new SQLException("Database unavailable or not provisioned; check configuration and run "
                        + "migrateDatabase", failure.getSQLState());
            }
            ImageStorage profileImages;
            ImageStorage listingImages;
            if (storageConfig == null) {
                // Explicit database-config overload remains the isolated local fixture seam.
                profileImages = new ImageStorage(directory.resolve("images/profiles"));
                listingImages = new ImageStorage(directory.resolve("images/listings"));
            } else {
                remoteStorage = new SupabaseImageObjectStore(storageConfig);
                profileImages = new ImageStorage(remoteStorage, "profiles");
                listingImages = new ImageStorage(remoteStorage, "listings");
            }
            ApplicationRuntime runtime = new ApplicationRuntime(channel, lock, database, profileImages,
                    listingImages, remoteStorage, clock);
            try {
                runtime.accounts.recoverImages().join();
                runtime.listings.recoverImages().join();
            } catch (CompletionException exception) {
                runtime.close();
                throw new IOException("Unable to initialize image storage", exception.getCause());
            }
            return runtime;
        } catch (IOException | SQLException | RuntimeException failure) {
            if (remoteStorage != null) {
                remoteStorage.close();
            }
            channel.close();
            throw failure;
        }
    }

    /**
     * Drains accepted service work and releases the application lock; repeated calls are harmless.
     *
     * @throws IOException if releasing the application lock or its channel fails
     */
    @Override
    public synchronized void close() throws IOException {
        if (isClosed) {
            return;
        }
        worker.close();
        try {
            if (remoteImageStorage != null) {
                remoteImageStorage.close();
            }
        } finally {
            try {
                lock.release();
            } finally {
                lockChannel.close();
                isClosed = true;
            }
        }
    }
}

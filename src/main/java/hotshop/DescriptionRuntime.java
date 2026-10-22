package hotshop;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import hotshop.llm.HttpLlmClient;
import hotshop.llm.LlmClient;
import hotshop.llm.LlmConfiguration;
import hotshop.llm.LlmHttpTransport;
import hotshop.llm.LlmRequestBudget;
import hotshop.llm.SocSearchClient;
import hotshop.service.AuthenticatedSession;
import hotshop.service.DescriptionQuota;
import hotshop.service.DescriptionService;
import hotshop.service.ServiceWorker;

/** Owns description generation resources shared by one server process. */
public final class DescriptionRuntime implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DescriptionRuntime.class.getName());
    private final DescriptionQuota quota = new DescriptionQuota();
    private final LlmClient client;
    private final LlmHttpTransport transport;
    private final Set<DescriptionService> services = new HashSet<>();
    private boolean closed;

    /**
     * Transfers ownership of one shared transport; its close operation must be nonblocking.
     * Application runtimes borrow this owner and never clear its quota history.
     *
     * @param client the shared transport, closed once if it implements AutoCloseable
     */
    public DescriptionRuntime(LlmClient client) {
        this(client, null);
    }

    private DescriptionRuntime(LlmClient client, LlmHttpTransport transport) {
        this.client = Objects.requireNonNull(client);
        this.transport = transport;
    }

    /** Creates a shared transport from an immutable startup snapshot without making a network call. */
    public DescriptionRuntime(LlmConfiguration configuration) {
        this(configuration, new LlmHttpTransport(new LlmRequestBudget()));
    }

    private DescriptionRuntime(LlmConfiguration configuration, LlmHttpTransport transport) {
        this(new HttpLlmClient(configuration, transport), transport);
    }

    /** Creates a buyer adapter borrowing this process owner's HTTP resources. */
    public synchronized SocSearchClient createBuyerSearchClient(java.net.URI endpoint, String apiKey, String model) {
        if (closed || transport == null) {
            throw new IllegalStateException("AI transport owner is unavailable");
        }
        return new SocSearchClient(endpoint, apiKey, model, transport);
    }

    synchronized DescriptionService register(ServiceWorker worker, AuthenticatedSession session) {
        if (closed) {
            throw new IllegalStateException("Description generation server has closed");
        }
        DescriptionService service = new DescriptionService(worker, session, quota, client);
        services.add(service);
        return service;
    }

    synchronized void unregister(DescriptionService service) {
        services.remove(service);
    }

    /** Rejects new services, cancels pending requests and closes transport resources once. */
    @Override
    public void close() {
        List<DescriptionService> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(services);
            services.clear();
        }
        pending.forEach(DescriptionService::close);
        if (client instanceof AutoCloseable resource) {
            try {
                resource.close();
            } catch (Exception failure) {
                // Transport failures may contain private settings; retain only a fixed diagnostic.
                LOGGER.log(System.Logger.Level.WARNING, "LLM_TRANSPORT_CLOSE_FAILED");
            }
        }
        if (transport != null) {
            transport.close();
        }
    }
}

import java.net.URI;
import java.util.concurrent.TimeUnit;

import hotshop.llm.SocSearchClient;

/** Optional one-request connectivity check. Prints only the client's safe result category. */
class SocSearchSmoke {
    private static final String REQUEST = "Find an inexpensive used book";

    private SocSearchSmoke() {
    }

    public static void main(String[] args) {
        String endpoint = System.getenv("SOCLAAS_BASE_URL");
        String model = System.getenv("SOCLAAS_MODEL");
        String token = System.getenv("SOCLAAS_API_KEY");
        if (endpoint == null || model == null || token == null) {
            System.out.println("SoC smoke skipped: required environment is unavailable.");
            return;
        }
        try (var client = new SocSearchClient(URI.create(endpoint), token, model)) {
            SocSearchClient.Result result = client.propose(REQUEST).get(50, TimeUnit.SECONDS);
            if (result instanceof SocSearchClient.Proposed) {
                System.out.println("SoC smoke completed: proposal accepted.");
            } else if (result instanceof SocSearchClient.Failed failed) {
                System.out.println("SoC smoke completed: " + failed.kind());
            } else {
                System.out.println("SoC smoke completed: unknown result.");
            }
        } catch (Exception exception) {
            System.out.println("SoC smoke failed; details were suppressed.");
        }
    }
}

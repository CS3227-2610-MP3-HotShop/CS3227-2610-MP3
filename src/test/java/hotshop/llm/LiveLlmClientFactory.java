package hotshop.llm;

/** TASK-014 supplies an explicitly authorised adapter to the real production client. */
public interface LiveLlmClientFactory extends AutoCloseable {
    /** Create the production client; never a fake success client or an alternate prompt implementation. */
    LlmClient create() throws Exception;

    /** A reviewed public run identifier, never a token, endpoint, prompt or raw private model setting. */
    String evaluationVersion();

    @Override
    void close() throws Exception;
}

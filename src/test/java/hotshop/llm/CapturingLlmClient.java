package hotshop.llm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import hotshop.model.Condition;

/** Controlled transport collaborator; no configuration, networking or automatic completion. */
final class CapturingLlmClient implements LlmClient {
    private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
    private final AtomicInteger count = new AtomicInteger();

    @Override
    public CompletableFuture<Result> generate(String title, Condition condition) {
        CompletableFuture<Result> completion = new CompletableFuture<>();
        calls.add(new Call(title, condition, completion));
        count.incrementAndGet();
        return completion;
    }

    Call takeCall() throws InterruptedException {
        Call call = calls.poll(5, TimeUnit.SECONDS);
        if (call == null) {
            throw new AssertionError("Expected a captured synthetic generation call");
        }
        return call;
    }

    int callCount() {
        return count.get();
    }

    record Call(String title, Condition condition, CompletableFuture<Result> completion) {
    }
}

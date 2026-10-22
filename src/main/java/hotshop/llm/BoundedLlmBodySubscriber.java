package hotshop.llm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

/** One-chunk HTTP backpressure with independently bounded wire input and decoded output. */
final class BoundedLlmBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    static final int MAX_BODY_BYTES = 65536;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final BodyInput input = new BodyInput();
    private final boolean gzip;
    private Flow.Subscription subscription;
    private byte[] pending;
    private int position;
    private int received;
    private boolean ended;
    private boolean cancelled;
    private IOException inputFailure;
    private InputStream decoder;

    BoundedLlmBodySubscriber(boolean gzip, Executor worker) {
        this.gzip = gzip;
        worker.execute(this::decode);
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription incoming) {
        synchronized (this) {
            if (subscription != null || cancelled) {
                incoming.cancel();
                return;
            }
            subscription = incoming;
        }
        incoming.request(1);
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> buffers) {
        if (cancelled || ended) {
            return;
        }
        long count = 0;
        for (ByteBuffer buffer : buffers) {
            count += buffer.remaining();
        }
        if (count > MAX_BODY_BYTES - received || pending != null) {
            fail(new InvalidBodyException());
            return;
        }
        received += (int) count;
        byte[] chunk = new byte[(int) count];
        int offset = 0;
        for (ByteBuffer buffer : buffers) {
            int length = buffer.remaining();
            buffer.get(chunk, offset, length);
            offset += length;
        }
        pending = chunk;
        position = 0;
        notifyAll();
    }

    @Override
    public synchronized void onError(Throwable failure) {
        if (!cancelled && !ended) {
            inputFailure = new IOException("Provider stream failed");
            ended = true;
            body.completeExceptionally(failure);
            notifyAll();
        }
    }

    @Override
    public synchronized void onComplete() {
        ended = true;
        notifyAll();
    }

    void cancel() {
        InputStream current;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            pending = null;
            if (subscription != null) {
                subscription.cancel();
            }
            body.cancel(true);
            current = decoder;
            notifyAll();
        }
        if (current != null) {
            try {
                current.close();
            } catch (IOException failure) {
                // Decoder close failures carry no useful public information.
            }
        }
    }

    private void decode() {
        try {
            InputStream stream = gzip ? new GZIPInputStream(input) : input;
            synchronized (this) {
                decoder = stream;
                if (cancelled) {
                    stream.close();
                    return;
                }
            }
            byte[] bytes;
            try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (count > MAX_BODY_BYTES - output.size()) {
                        throw new InvalidBodyException();
                    }
                    output.write(buffer, 0, count);
                }
                // Gzip can stop after its trailer; still enforce the cap and deadline through HTTP EOF.
                while (input.read(buffer) != -1) {
                    // Discard only already-counted, bounded trailing wire bytes.
                }
                bytes = output.toByteArray();
            }
            body.complete(bytes);
        } catch (IOException failure) {
            fail(new InvalidBodyException());
        } catch (RuntimeException failure) {
            fail(new InvalidBodyException());
        }
    }

    private synchronized void fail(InvalidBodyException failure) {
        if (!body.isDone()) {
            body.completeExceptionally(failure);
        }
        cancel();
    }

    static final class InvalidBodyException extends IOException {
        InvalidBodyException() {
            super("Invalid provider body");
        }
    }

    private final class BodyInput extends InputStream {
        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            while (true) {
                Flow.Subscription request = null;
                synchronized (BoundedLlmBodySubscriber.this) {
                    if (cancelled || inputFailure != null) {
                        throw new IOException("Provider stream closed");
                    }
                    if (pending != null && position < pending.length) {
                        int count = Math.min(length, pending.length - position);
                        System.arraycopy(pending, position, target, offset, count);
                        position += count;
                        return count;
                    }
                    if (pending != null) {
                        pending = null;
                        if (!ended) {
                            request = subscription;
                        }
                    }
                    if (request == null) {
                        if (ended) {
                            return -1;
                        }
                        try {
                            BoundedLlmBodySubscriber.this.wait();
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Provider stream interrupted");
                        }
                    }
                }
                if (request != null) {
                    request.request(1);
                }
            }
        }
    }
}

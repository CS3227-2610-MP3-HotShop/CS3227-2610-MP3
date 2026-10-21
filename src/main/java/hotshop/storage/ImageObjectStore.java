package hotshop.storage;

import java.io.IOException;

/** Byte-oriented storage for validated application image objects. */
public interface ImageObjectStore {
    /** Stores a new immutable image object. */
    void put(String namespace, String filename, byte[] bytes, String contentType) throws IOException;

    /** Reads one object from the fixed namespace. */
    byte[] read(String namespace, String filename, int maximumBytes) throws IOException;

    /** Reads an object or returns null only when the provider confirms that it is absent. */
    byte[] readIfExists(String namespace, String filename, int maximumBytes) throws IOException;

    /** Deletes one known application object. */
    void delete(String namespace, String filename) throws IOException;
}

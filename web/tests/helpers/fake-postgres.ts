/**
 * A PostgreSQL server that speaks exactly one thing: the TLS handshake answer.
 *
 * It exists so a test can ask "did that client try to encrypt the connection"
 * and get a real answer off the wire, rather than reading back an options
 * object the client built. `scripts/db-check.ts` runs in its own process, so
 * its options are not inspectable; what it puts on the socket is.
 *
 * HOW THE NEGOTIATION WORKS. Before anything else, a libpq-style client that
 * wants TLS sends an 8-byte SSLRequest: a 4-byte length of 8, then the magic
 * number 80877103. The server answers with a SINGLE byte, `S` for "yes, start
 * TLS" or `N` for "no". A client that does NOT want TLS skips all of this and
 * sends a StartupMessage, whose first four bytes are its own length and whose
 * next four are a protocol version, never the magic number.
 *
 * So the first eight bytes are a complete and unambiguous answer, and this
 * server needs no certificate, no PostgreSQL, and no network beyond loopback.
 *
 * It always answers `N`. A client that required TLS then fails, which is the
 * outcome the tests assert on; a client that never asked gets far enough to
 * reveal that it never asked.
 */

import { networkInterfaces } from 'node:os';
import net from 'node:net';

const SSL_REQUEST_CODE = 80877103;
const SSL_REQUEST_LENGTH = 8;

/**
 * A minimal `ErrorResponse`, so a client that gets past the handshake stops
 * instead of retrying.
 *
 * Without it the client sees a socket that simply closes, treats it as a
 * transient network fault and reconnects, and the test spends its whole
 * timeout watching that loop rather than asserting anything.
 *
 * Format: 'E', Int32 length, then NUL-terminated (fieldType, value) pairs,
 * then a final NUL.
 */
function errorResponse(): Buffer {
  const fields = Buffer.from('SFATAL\0C28000\0Mfake postgres: handshake probe only\0\0', 'utf8');
  const packet = Buffer.alloc(1 + 4 + fields.length);
  packet.write('E', 0, 'latin1');
  packet.writeInt32BE(4 + fields.length, 1);
  fields.copy(packet, 5);
  return packet;
}

/**
 * An address of this machine that is NOT loopback by anyone's definition.
 *
 * `127.0.0.2` was the first choice, because the whole of 127.0.0.0/8 is
 * loopback and needs no network. It does not work: the TLS rules under test
 * correctly treat the entire 127.x range as local, so a server there is never
 * asked for TLS and the test would pass while proving nothing. The rule is
 * right and the trick was wrong.
 *
 * So the fake binds to a real interface address on an ephemeral port, for the
 * few hundred milliseconds a test takes.
 */
export function nonLoopbackAddress(): string | undefined {
  for (const addresses of Object.values(networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) return address.address;
    }
  }
  return undefined;
}

export interface FakePostgres {
  /** The address to put in a connection URL. */
  readonly host: string;
  readonly port: number;
  /** True once some client has sent an SSLRequest. */
  requestedTls(): boolean;
  /** True once some client has connected at all. */
  connected(): boolean;
  close(): Promise<void>;
}

export interface FakePostgresOptions {
  /**
   * Defaults to this machine's first non-loopback IPv4 address, so the TLS
   * rules under test see a remote host. Pass `127.0.0.1` to test the local
   * case.
   */
  readonly host?: string;
}

export async function startFakePostgres(
  options: FakePostgresOptions = {},
): Promise<FakePostgres> {
  const host = options.host ?? nonLoopbackAddress();
  if (host === undefined) {
    throw new Error(
      'No non-loopback IPv4 address on this machine, so the remote-host TLS rule cannot ' +
        'be exercised. Connect a network interface, or pass an explicit host.',
    );
  }

  let sawSslRequest = false;
  let sawConnection = false;

  const server = net.createServer((socket) => {
    sawConnection = true;

    socket.on('data', (chunk) => {
      const isSslRequest =
        chunk.length >= SSL_REQUEST_LENGTH &&
        chunk.readInt32BE(0) === SSL_REQUEST_LENGTH &&
        chunk.readInt32BE(4) === SSL_REQUEST_CODE;

      if (isSslRequest) {
        sawSslRequest = true;
        // 'N': TLS refused. A client that requires TLS must now give up, which
        // is the behaviour under test. One that merely prefers it will follow
        // with a StartupMessage, handled on the next chunk.
        socket.write(Buffer.from('N', 'latin1'));
        return;
      }

      // A StartupMessage: the client is talking in the clear. Refuse it in
      // PostgreSQL's own words so it fails instead of reconnecting.
      socket.end(errorResponse());
    });

    socket.on('error', () => {
      // A client giving up mid-handshake is the expected outcome, not a fault.
    });
  });

  server.on('error', () => {
    // Surfaced by the listen promise below; ignored afterwards so a late
    // socket error cannot take the process down.
  });

  await new Promise<void>((resolve, reject) => {
    server.once('error', (error: NodeJS.ErrnoException) => {
      reject(
        error.code === 'EADDRNOTAVAIL'
          ? new Error(`Could not bind ${host}: this machine has no such address.`)
          : error,
      );
    });
    server.listen(0, host, resolve);
  });

  const address = server.address();
  if (address === null || typeof address === 'string') {
    throw new Error('fake postgres did not get a TCP address');
  }

  return {
    host,
    port: address.port,
    requestedTls: () => sawSslRequest,
    connected: () => sawConnection,
    close: () =>
      new Promise<void>((resolve) => {
        server.close(() => resolve());
      }),
  };
}

CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    username TEXT NOT NULL,
    normalized_username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    profile_image TEXT,
    preferred_pickup_location TEXT
);

CREATE TABLE credentials (
    user_id TEXT PRIMARY KEY NOT NULL REFERENCES users(id),
    algorithm TEXT NOT NULL,
    iterations INTEGER NOT NULL CHECK (iterations > 0),
    salt BYTEA NOT NULL,
    password_hash BYTEA NOT NULL
);

CREATE TABLE listings (
    id TEXT PRIMARY KEY NOT NULL,
    seller_id TEXT NOT NULL REFERENCES users(id),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL
        CHECK (category IN ('ELECTRONICS', 'BOOKS', 'CLOTHING', 'FURNITURE', 'SPORTS', 'OTHER')),
    price_cents INTEGER NOT NULL CHECK (price_cents BETWEEN 1 AND 100000000),
    condition TEXT NOT NULL CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    pickup_location TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'ARCHIVED')),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL CHECK (updated_at >= created_at)
);

CREATE INDEX listings_by_seller ON listings(seller_id);

CREATE INDEX listings_by_status ON listings(status);

CREATE TABLE listing_images (
    listing_id TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 0 AND 9),
    filename TEXT NOT NULL UNIQUE,
    PRIMARY KEY (listing_id, display_order)
);

CREATE TABLE image_cleanup (
    namespace TEXT NOT NULL CHECK (namespace IN ('profiles', 'listings')),
    filename TEXT NOT NULL,
    PRIMARY KEY (namespace, filename)
);



CREATE TABLE offers (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    amount_cents INTEGER NOT NULL CHECK (amount_cents BETWEEN 1 AND 100000000),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at BIGINT NOT NULL,
    closed_at BIGINT,
    CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    CHECK (closed_at IS NULL OR closed_at >= created_at)
);

CREATE UNIQUE INDEX one_pending_offer_per_buyer ON offers(listing_id, buyer_id) WHERE status = 'PENDING';

CREATE INDEX offers_by_buyer ON offers(buyer_id);

CREATE TABLE transactions (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    accepted_offer_id TEXT NOT NULL UNIQUE REFERENCES offers(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    seller_id TEXT NOT NULL REFERENCES users(id),
    agreed_price_cents INTEGER NOT NULL CHECK (agreed_price_cents BETWEEN 1 AND 100000000),
    listing_title TEXT NOT NULL,
    listing_description TEXT NOT NULL,
    listing_condition TEXT NOT NULL CHECK (listing_condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    created_at BIGINT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    buyer_confirmed_at BIGINT,
    seller_confirmed_at BIGINT
);

CREATE UNIQUE INDEX one_active_sale_per_listing ON transactions(listing_id) WHERE status = 'ACTIVE';

ALTER TABLE transactions ADD COLUMN cancelled_at BIGINT;

ALTER TABLE transactions ADD COLUMN cancelled_by TEXT REFERENCES users(id);

CREATE TABLE cancellation_requests (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    requester_id TEXT NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    resolved_at BIGINT,
    CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CHECK (resolved_at IS NULL OR resolved_at >= created_at),
    history_order BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE UNIQUE INDEX one_pending_request_per_sale ON cancellation_requests(transaction_id)
    WHERE status = 'PENDING';

CREATE INDEX transactions_by_buyer ON transactions(buyer_id);

CREATE INDEX transactions_by_seller ON transactions(seller_id);

CREATE TABLE meetup_slots (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    start_at BIGINT NOT NULL,
    end_at BIGINT NOT NULL,
    location TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    CHECK (end_at > start_at)
);

CREATE INDEX meetup_slots_by_sale ON meetup_slots(transaction_id);

CREATE TABLE meetups (
    id TEXT PRIMARY KEY NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions(id),
    start_at BIGINT NOT NULL,
    end_at BIGINT NOT NULL,
    location TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    created_at BIGINT NOT NULL,
    CHECK (end_at > start_at)
);

CREATE UNIQUE INDEX one_scheduled_meetup_per_sale ON meetups(transaction_id) WHERE status = 'SCHEDULED';

CREATE TABLE meetup_reschedule_proposals (
    id TEXT PRIMARY KEY NOT NULL,
    meetup_id TEXT NOT NULL REFERENCES meetups(id),
    proposer_id TEXT NOT NULL REFERENCES users(id),
    start_at BIGINT NOT NULL,
    end_at BIGINT NOT NULL,
    location TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    resolved_at BIGINT,
    CHECK (end_at > start_at),
    CHECK ((status = 'PENDING') = (resolved_at IS NULL)),
    CHECK (resolved_at IS NULL OR resolved_at >= created_at),
    history_order BIGINT GENERATED ALWAYS AS IDENTITY
);

CREATE UNIQUE INDEX one_pending_move_per_meetup ON meetup_reschedule_proposals(meetup_id)
    WHERE status = 'PENDING';

CREATE TABLE conversations (
    id TEXT PRIMARY KEY NOT NULL,
    listing_id TEXT NOT NULL REFERENCES listings(id),
    buyer_id TEXT NOT NULL REFERENCES users(id),
    seller_id TEXT NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL,
    buyer_read_sequence BIGINT NOT NULL DEFAULT 0 CHECK (buyer_read_sequence >= 0),
    buyer_opened_at BIGINT,
    seller_read_sequence BIGINT NOT NULL DEFAULT 0 CHECK (seller_read_sequence >= 0),
    seller_opened_at BIGINT,
    UNIQUE (listing_id, buyer_id),
    CHECK (buyer_id <> seller_id)
);

CREATE INDEX conversations_by_buyer ON conversations(buyer_id);

CREATE INDEX conversations_by_seller ON conversations(seller_id);

CREATE TABLE messages (
    id TEXT PRIMARY KEY NOT NULL,
    conversation_id TEXT NOT NULL REFERENCES conversations(id),
    sender_id TEXT NOT NULL REFERENCES users(id),
    sequence BIGINT NOT NULL CHECK (sequence >= 1),
    text TEXT NOT NULL CHECK (length(text) BETWEEN 1 AND 1000),
    sent_at BIGINT NOT NULL,
    UNIQUE (conversation_id, sequence)
);

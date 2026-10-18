-- user_id has no foreign key on purpose: INV-01 owns `users` and adds the constraint.
create table sessions (
  id uuid primary key,
  token_hash text not null unique,
  user_id uuid not null,
  created_at timestamptz not null,
  last_seen_at timestamptz not null
);

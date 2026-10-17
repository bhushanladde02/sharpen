-- Invite a friend (2026-10-16). Safe to run more than once.
-- On the server the deploy script applies it; by hand:
--   dc exec -T db psql -U sharpen sharpen < ~/sharpen/src/main/resources/db/migrations/2026-10-16-invites.sql

-- Which member's /join/<handle> link an account was created from (null for everyone else).
alter table person add column if not exists invited_by bigint references person (id) on delete set null;

-- One row per invitation email Sharpen sent. The friend's address is kept only as a SHA-256 hash: enough to never
-- write to it twice and to notice when they join, not enough to read it back. token is the unsubscribe secret.
create table if not exists invite (
    id          bigserial    primary key,
    inviter_id  bigint       not null references person (id) on delete cascade,
    email_hash  varchar(64)  not null,
    sent_at     timestamp(6) with time zone not null default now(),
    accepted_at timestamp(6) with time zone,
    token       varchar(40)  not null
);
create unique index if not exists ux_invite_email_hash on invite (email_hash);
create unique index if not exists ux_invite_token      on invite (token);

-- Addresses (hashed) that asked never to be emailed again.
create table if not exists mail_opt_out (
    email_hash  varchar(64)  primary key,
    created_at  timestamp(6) with time zone not null default now()
);

-- "Sign in with Google / GitHub" (2026-10-08). Safe to run more than once.
-- On the server the deploy script applies it; by hand:
--   dc exec -T db psql -U sharpen sharpen < ~/sharpen/src/main/resources/db/migrations/2026-10-08-sign-in-with.sql

-- An account created through Google or GitHub has no password until its owner sets one in Settings.
alter table person alter column password_hash drop not null;

-- Which provider accounts may sign in as which person. Keyed by the provider's permanent user id, not by email.
create table if not exists person_identity (
    id          bigserial    primary key,
    person_id   bigint       not null references person (id) on delete cascade,
    provider    varchar(20)  not null,
    subject     varchar(190) not null,
    email       varchar(190),
    username    varchar(100),
    created_at  timestamp(6) with time zone not null default now()
);
create unique index if not exists ux_identity_provider_subject on person_identity (provider, subject);
create unique index if not exists ux_identity_person_provider  on person_identity (person_id, provider);

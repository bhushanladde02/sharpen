-- Profile pictures (2026-09-10). Safe to run more than once.
-- On the server:  dc exec -T db psql -U sharpen sharpen < ~/sharpen/src/main/resources/db/migrations/2026-09-10-avatars.sql
alter table person add column if not exists avatar_version integer not null default 0;

create table if not exists person_avatar (
    person_id    bigint      primary key references person (id) on delete cascade,
    content_type varchar(40) not null,
    bytes        bytea       not null,
    updated_at   timestamp(6) with time zone not null default now()
);

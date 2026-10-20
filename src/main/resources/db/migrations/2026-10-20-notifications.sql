-- In-app notifications (2026-10-20). Safe to run more than once.
-- On the server the deploy script applies it; by hand:
--   dc exec -T db psql -U sharpen sharpen < ~/sharpen/src/main/resources/db/migrations/2026-10-20-notifications.sql

-- One row per notification for one person: welcome, a friend joined, a report is ready; for the owner, a new
-- member or a new feedback message. read_at is set when it is opened or "Mark all as read" is pressed.
create table if not exists notification (
    id          bigserial     primary key,
    person_id   bigint        not null references person (id) on delete cascade,
    kind        varchar(30)   not null,
    title       varchar(200)  not null,
    body        varchar(500),
    link        varchar(200),
    created_at  timestamp(6) with time zone not null default now(),
    read_at     timestamp(6) with time zone
);
create index if not exists ix_notification_person on notification (person_id, created_at);

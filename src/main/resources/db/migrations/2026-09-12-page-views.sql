-- First-party traffic analytics (2026-09-12). Safe to run more than once.
create table if not exists page_view (
    id          bigserial    primary key,
    occurred_at timestamp(6) with time zone not null default now(),
    view_day    date         not null,
    path        varchar(200) not null,
    referrer    varchar(190),
    visitor     varchar(64)  not null,
    lang        varchar(16),
    signed_in   boolean      not null default false
);
create index if not exists ix_page_view_day  on page_view (view_day);
create index if not exists ix_page_view_path on page_view (path);

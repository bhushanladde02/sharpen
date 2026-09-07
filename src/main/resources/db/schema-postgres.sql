-- Sharpen schema for PostgreSQL. Matches the JPA entities exactly (ddl-auto=validate in the postgres profile).
-- Release 1: move this into Flyway as V1__init.sql and add the flyway-core dependency.

create table person (
    id               bigserial primary key,
    email            varchar(190) not null,
    password_hash    varchar(100) not null,
    display_name     varchar(120) not null,
    handle           varchar(60)  not null,
    account_type     varchar(20)  not null,
    headline         varchar(160),
    job_title        varchar(120),
    industry         varchar(80),
    years_experience integer,
    location         varchar(120),
    primary_tools    varchar(300),
    public_profile   boolean      not null default true,
    api_key          varchar(64)  not null,
    created_at       timestamp(6) with time zone not null default now()
);
create unique index ux_person_email   on person (email);
create unique index ux_person_handle  on person (handle);
create unique index ux_person_api_key on person (api_key);

create table usage_session (
    id                     bigserial primary key,
    person_id              bigint       not null references person (id) on delete cascade,
    occurred_on            date         not null,
    context                varchar(20)  not null,
    tool                   varchar(60)  not null,
    task_category          varchar(20)  not null,
    duration_minutes       integer      not null default 0,
    prompt_count           integer      not null default 0,
    human_contribution_pct integer      not null default 50,
    verified_output        boolean      not null default false,
    learned_something      boolean      not null default false,
    outcome                integer      not null default 3,
    source                 varchar(20)  not null,
    self_assessed          boolean      not null default false,
    external_id            varchar(120),
    tokens_in              bigint,
    tokens_out             bigint,
    notes                  varchar(500),
    created_at             timestamp(6) with time zone not null default now()
);
create index        ix_session_person_date     on usage_session (person_id, occurred_on);
create unique index ux_session_person_external on usage_session (person_id, external_id);

create table monthly_report (
    id            bigserial primary key,
    person_id     bigint      not null references person (id) on delete cascade,
    year_month    varchar(7)  not null,
    ai_score      integer     not null,
    independence  integer     not null,
    effectiveness integer     not null,
    verification  integer     not null,
    growth        integer     not null,
    breadth       integer     not null,
    session_count integer     not null,
    total_minutes integer     not null,
    payload       text        not null,
    generated_at  timestamp(6) with time zone not null default now()
);
create unique index ux_report_person_month on monthly_report (person_id, year_month);

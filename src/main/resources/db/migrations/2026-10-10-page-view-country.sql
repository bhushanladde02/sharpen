-- Country of each page view (2026-10-10): ISO 3166-1 alpha-2, 'ZZ' when unknown, null for earlier views.
-- Looked up from the address in memory when the view is recorded; the address is not stored. Safe to run more than once.
alter table page_view add column if not exists country varchar(2);

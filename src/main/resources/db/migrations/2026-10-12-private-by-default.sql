-- New profiles start private (2026-10-12). Only the column default changes: every existing profile keeps the
-- setting its owner has now. The application sets the value on every insert anyway. Safe to run more than once.
alter table person alter column public_profile set default false;

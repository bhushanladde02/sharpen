-- First / middle / last name on person (2026-09-21). Safe to run more than once.
-- On the server:  dc exec -T db psql -U sharpen sharpen < ~/sharpen/src/main/resources/db/migrations/2026-09-21-person-names.sql
-- display_name stays the shown name; individuals get it split into parts, company accounts keep nulls.
alter table person add column if not exists first_name  varchar(60);
alter table person add column if not exists middle_name varchar(60);
alter table person add column if not exists last_name   varchar(60);

-- Backfill existing individuals from display_name: first word, last word, whatever lies between as middle.
update person
   set first_name  = split_part(btrim(display_name), ' ', 1),
       last_name   = case when position(' ' in btrim(display_name)) > 0
                          then regexp_replace(btrim(display_name), '^.*\s', '') end,
       middle_name = case when array_length(regexp_split_to_array(btrim(display_name), '\s+'), 1) > 2
                          then array_to_string((regexp_split_to_array(btrim(display_name), '\s+'))[2:array_length(regexp_split_to_array(btrim(display_name), '\s+'), 1) - 1], ' ') end
 where account_type = 'INDIVIDUAL' and first_name is null;

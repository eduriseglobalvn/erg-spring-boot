-- Candidate report only. Do not turn every row into an index automatically.
select
    tc.table_schema,
    tc.table_name,
    kcu.column_name,
    tc.constraint_name
from information_schema.table_constraints tc
join information_schema.key_column_usage kcu
  on kcu.constraint_name = tc.constraint_name
 and kcu.table_schema = tc.table_schema
where tc.constraint_type = 'FOREIGN KEY'
order by tc.table_schema, tc.table_name, tc.constraint_name, kcu.ordinal_position;


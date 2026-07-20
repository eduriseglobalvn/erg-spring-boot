-- Run after the accepted workload has warmed the system.
select
    calls,
    round(total_exec_time::numeric, 2) as total_ms,
    round(mean_exec_time::numeric, 2) as mean_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    left(query, 500) as query
from pg_stat_statements
where dbid = (select oid from pg_database where datname = current_database())
order by total_exec_time desc
limit 50;


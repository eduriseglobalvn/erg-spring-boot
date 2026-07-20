package com.erg.ergspringboot.service.reporting;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresReportAggregateReconciliationStore implements ReportAggregateReconciliationStore {
    private final JdbcClient jdbc;

    public PostgresReportAggregateReconciliationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Task> claim(int limit) {
        return jdbc.sql("""
                with due as (
                    select tenant_id, aggregate_id, source_updated_at
                    from report_reconciliation_queue
                    where aggregate_type = 'assignment_progress'
                      and (status = 'pending' or (status = 'processing' and lease_until < now()))
                      and available_at <= now()
                    order by available_at
                    for update skip locked
                    limit :limit
                )
                update report_reconciliation_queue queue
                set status = 'processing', attempts = attempts + 1,
                    lease_until = now() + interval '2 minutes'
                from due
                where queue.tenant_id = due.tenant_id
                  and queue.aggregate_type = 'assignment_progress'
                  and queue.aggregate_id = due.aggregate_id
                returning queue.tenant_id, queue.aggregate_id, due.source_updated_at
                """)
                .param("limit", limit)
                .query((row, number) -> new Task(
                        row.getString("tenant_id"), row.getString("aggregate_id"),
                        row.getObject("source_updated_at", OffsetDateTime.class)))
                .list();
    }

    @Override
    public void reconcile(Task task) {
        jdbc.sql("select erg_reconcile_assignment_progress(:tenantId, :aggregateId)")
                .param("tenantId", task.tenantId()).param("aggregateId", task.aggregateId())
                .query(Integer.class).optional();
        jdbc.sql("""
                delete from report_reconciliation_queue
                where tenant_id = :tenantId and aggregate_type = 'assignment_progress'
                  and aggregate_id = :aggregateId and source_updated_at = :sourceUpdatedAt
                """)
                .param("tenantId", task.tenantId()).param("aggregateId", task.aggregateId())
                .param("sourceUpdatedAt", task.sourceUpdatedAt()).update();
    }
}

package com.erg.ergspringboot.platform.performance;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Counts Hibernate SQL statements against the request thread that issued them. */
public class RequestExecutionStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        RequestExecutionContext.incrementStatementCount();
        return sql;
    }
}

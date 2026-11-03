package com.erg.ergspringboot.platform.graphql;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bounds the cardinality of the client-chosen GraphQL {@code operationName} used as a metric tag.
 *
 * <p>With a configured allowlist only those names are reported. Without one, well-formed names are
 * admitted first-come up to {@code maxMetricOperations}; everything else is tagged {@code other}.</p>
 */
final class GraphQlOperationMetricTag {

    static final String OTHER = "other";
    private static final Pattern OPERATION_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final Set<String> allowlist;
    private final int maxAdmitted;
    private final Set<String> admitted = ConcurrentHashMap.newKeySet();

    GraphQlOperationMetricTag(Set<String> allowlist, int maxAdmitted) {
        this.allowlist = allowlist == null ? Set.of() : Set.copyOf(allowlist);
        this.maxAdmitted = Math.max(0, maxAdmitted);
    }

    String tagFor(String operationName) {
        if (operationName == null || !OPERATION_NAME.matcher(operationName).matches()) {
            return OTHER;
        }
        if (!allowlist.isEmpty()) {
            return allowlist.contains(operationName) ? operationName : OTHER;
        }
        if (admitted.contains(operationName)) {
            return operationName;
        }
        synchronized (admitted) {
            if (admitted.size() < maxAdmitted) {
                admitted.add(operationName);
                return operationName;
            }
        }
        return admitted.contains(operationName) ? operationName : OTHER;
    }

}

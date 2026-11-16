package com.erg.ergspringboot.authorization.catalog;

import java.util.List;
import java.util.Set;

/** One of the seven business roles. {@code id} is roles.id, {@code code} is roles.name (the technical code). */
public record BusinessRole(
        String id,
        String code,
        String label,
        String description,
        List<String> canDo,
        List<String> cannotDo,
        boolean exclusive,
        Set<String> permissions) {
}

package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RouteInventoryContractTest {

    private static final Path INVENTORY = Path.of("docs/architecture/api-inventory.md");
    private static final Path REST_CATALOG =
            Path.of("src/test/resources/performance/api-performance-catalog.yaml");
    private static final Pattern OWNER_ROW = Pattern.compile(
            "\\| `(?<prefix>com\\.erg\\.ergspringboot\\.controller[^`]*)` \\| `(?<owner>[^`]+)` \\|");

    @Test
    void everyCatalogedRestHandlerHasAnExplicitModuleOwnerAndStatusPolicy() throws IOException {
        String inventory = Files.readString(INVENTORY);
        List<String> catalog = Files.readAllLines(REST_CATALOG);
        Map<String, String> ownerByControllerPrefix = ownerMappings(inventory);
        Set<String> missingOwners = new LinkedHashSet<>();
        Set<String> classifications = new LinkedHashSet<>();
        int declaredRouteCount = -1;
        int actualRouteCount = 0;

        for (String rawLine : catalog) {
            String line = rawLine.strip();
            if (line.startsWith("route-count:")) {
                declaredRouteCount = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            } else if (line.startsWith("- route:")) {
                actualRouteCount++;
                String handler = line.substring(line.indexOf("=>") + 2).trim();
                String controller = handler.substring(0, handler.lastIndexOf('.'));
                if (ownerByControllerPrefix.keySet().stream().noneMatch(prefix ->
                        controller.equals(prefix) || controller.startsWith(prefix + '.'))) {
                    missingOwners.add(controller);
                }
            } else if (line.startsWith("classification:")) {
                classifications.add(line.substring(line.indexOf(':') + 1).trim());
            }
        }

        assertThat(actualRouteCount).isEqualTo(declaredRouteCount);
        assertThat(inventory).contains("REST route count: `" + declaredRouteCount + "`");
        assertThat(missingOwners).as("REST handlers without a module owner").isEmpty();
        classifications.forEach(classification -> assertThat(inventory)
                .as("classification %s needs a canonical/prototype status policy", classification)
                .contains("`" + classification + "`"));
    }

    @Test
    void everyCatalogedGraphQlFieldIsOwnedByTheInventory() throws IOException {
        String inventory = Files.readString(INVENTORY);
        List<String> catalog = Files.readAllLines(REST_CATALOG);
        Set<String> fields = new LinkedHashSet<>();
        boolean graphql = false;
        int declaredCount = -1;

        for (String rawLine : catalog) {
            String line = rawLine.strip();
            if (line.equals("graphql:")) {
                graphql = true;
            } else if (graphql && line.startsWith("mapped-method-count:")) {
                declaredCount = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            } else if (graphql && line.startsWith("- ")) {
                fields.add(line.substring(2).trim());
            }
        }

        assertThat(fields).hasSize(declaredCount);
        assertThat(inventory).contains("GraphQL mapped field count: `" + declaredCount + "`");
        fields.forEach(field -> assertThat(inventory)
                .as("GraphQL field %s needs an owner", field)
                .contains("`" + field + "`"));
    }

    private Map<String, String> ownerMappings(String inventory) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = OWNER_ROW.matcher(inventory);
        while (matcher.find()) {
            result.put(matcher.group("prefix"), matcher.group("owner"));
        }
        return result;
    }
}

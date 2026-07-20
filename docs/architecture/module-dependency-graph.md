# Module dependency graph

The current explicit graph intentionally has one migrated pilot:

```text
ErgSpringApplication
        |
        v
     calendar                 reporting        authorization
        |                         |                  |
        +--> legacy services <----+------------------+
        +--> platform persistence/security adapters
```

The compatibility seam is temporary. A caller may depend on
`CalendarOperations` and the public `CalendarBatchPublished` event; it must
not import `calendar.internal`, a calendar repository, or a JPA entity. The
graph is generated from the compiled application by the Modulith
`Documenter` test and is checked by `ApplicationModules.verify()`.

Reporting owns the web-facing authorization boundary through
`ReportingOperations`; the legacy job and LMS assignment services remain a
temporary implementation seam until their internal slices are migrated.

# P3-10: keep or remove each prototype module

Plan item P3-10 in `README.md`. Decided 2026-11-04 under the authority the repository owner delegated
for this item. Rule used: a module stays when a frontend (`erg` or `erg-system`) calls its routes;
otherwise it is deleted.

Today the kept modules run only under `@Profile("prototype")` with in-memory stores, so production does
not start them and their URLs return 404 there. The public `permitAll` rules for them stay in
`platform/security/SecurityConfig.java`: the `erg` site calls those routes, so removing the rules would
be a breaking authorization change for a consumer.

| Module | Code | Used by | Decision |
|---|---|---|---|
| CMS posts/pages/menus | `controller/cms/PostController`, `PageMenuController`, `service/cms/CmsService` | `erg`, `erg-system` | Keep |
| Catalog courses / e-learning | `controller/catalog/CourseController`, `ElearningController`, `service/catalog/CatalogService` | `erg`, `erg-system` | Keep |
| Engagement: reviews, recruitment | `controller/engagement/EngagementController` | `erg`, `erg-system` | Keep |
| Documents | `controller/documents/DocumentController`, `service/documents/DocumentService` | `erg` | Keep |
| SEO | `controller/seo/SeoController` | `erg` | Keep |
| Crawler / RSS | `controller/crawler/CrawlerController`, `RssBlacklistController` | `erg` | Keep |
| Trending bot | `controller/trending/TrendingBotController` | `erg` | Keep |
| AI content | `controller/ai/AiContentController` | `erg` | Keep |
| Analytics ops | `controller/operations/AnalyticsOperationsController` | `erg` | Keep |
| Migration platform | `controller/platform/MigrationPlatformController` (8 static checklist routes) | nobody | **Removed** |
| Calendar Excel import | `service/calendar/CalendarExcelImportService` | `erg-system` | Keep |
| Enterprise connectors | `enterpriseconnectors/internal/ReferenceEnterpriseConnectorAdapter` | real module, reference adapter | Keep |

## What "Keep" means

Each kept module still has to become real before production can use it. Follow the contract protocol
(`add -> publish -> sync -> generate -> migrate -> deprecate -> observe -> remove`): a Flyway migration
with tenant RLS, a JPA store instead of the in-memory map, tenant-scoped authorization, then drop
`@Profile("prototype")`. That is feature work per module and is tracked outside this audit plan.

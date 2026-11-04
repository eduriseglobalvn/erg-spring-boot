# P3-10: keep or remove each prototype module

Plan item P3-10 in `README.md` needs a product decision. This page lists every prototype surface so the
decision is one mark per row. Source: file 12 §3.1 and file 14 §5.

Today these modules exist only under `@Profile("prototype")` with in-memory stores. Production does not
start them, so their URLs return 404 there. The risk is the public `permitAll` rules in
`platform/security/SecurityConfig.java` (lines ~112-130): a future controller on the same path would be
public at once. Removing those rules is an authorization change, a breaking change under the contract
protocol, so it waits for this decision.

| Module | Code | Public rule today | Decision (Keep = build persistence, Remove = delete code + rules) |
|---|---|---|---|
| CMS posts/pages/menus | `controller/cms/PostController`, `PageMenuController`, `service/cms/CmsService` | GET `/api/v1/posts/**`, `/api/v1/pages/**`, `/api/v1/menus/structure` | ☐ Keep ☐ Remove |
| Catalog courses / e-learning | `controller/catalog/CourseController`, `ElearningController`, `service/catalog/CatalogService` | GET `/api/v1/courses/**` | ☐ Keep ☐ Remove |
| Engagement: reviews, recruitment | `controller/engagement/EngagementController` | GET+POST `/api/v1/reviews/**`, `/api/v1/recruitment/**` | ☐ Keep ☐ Remove |
| Documents | `controller/documents/DocumentController`, `service/documents/DocumentService` | none | ☐ Keep ☐ Remove |
| SEO | `controller/seo/SeoController` | none (`seo.manage`) | ☐ Keep ☐ Remove |
| Crawler / RSS | `controller/crawler/CrawlerController`, `RssBlacklistController` | none (`crawler.manage`) | ☐ Keep ☐ Remove |
| Trending bot | `controller/trending/TrendingBotController` | GET `/api/v1/trending/**` | ☐ Keep ☐ Remove |
| AI content | `controller/ai/AiContentController` | none | ☐ Keep ☐ Remove |
| Analytics ops | `controller/operations/AnalyticsOperationsController` | none | ☐ Keep ☐ Remove |
| Migration platform | `controller/platform/MigrationPlatformController` | none | ☐ Keep ☐ Remove |
| Calendar Excel import | `service/calendar/CalendarExcelImportService` (throws `NotImplementedException`) | none | ☐ Keep ☐ Remove |
| Enterprise connectors | `enterpriseconnectors/internal/ReferenceEnterpriseConnectorAdapter` (always `SUCCEEDED`) | none | ☐ Keep ☐ Remove |

Before deciding "Remove" for CMS, catalog, engagement or trending, confirm that the public site
(`erg` repository) does not call these routes against a prototype deployment.

## After the decision

- **Remove:** delete the controllers, services and their tests, drop the matching `SecurityConfig`
  rules, regenerate `contracts/openapi/erg-openapi.json`, and lower the horizontal-package ceilings.
- **Keep:** follow the contract protocol (`add -> publish -> sync -> generate -> migrate -> deprecate ->
  observe -> remove`): a Flyway migration with tenant RLS, a JPA store instead of the in-memory map,
  tenant-scoped authorization, then remove `@Profile("prototype")`.

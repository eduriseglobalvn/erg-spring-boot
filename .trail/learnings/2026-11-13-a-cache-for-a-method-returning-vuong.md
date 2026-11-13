---
title: "A cache for a method returning Optional must declare the inner type, because Spring stores the unwrapped value"
author: Vuong
date: 2026-11-13
branch: fix/quiz-package-cache-type
id: l-e2128bf0
scope: repo
status: active
src: agent
---

## Decided
Spring's cache abstraction unwraps Optional before put() and re-wraps on get(), so a cache descriptor's value type is the bare response, never Optional<...>. A wrong type made every Redis (L2) encode throw "Incompatible types", and because put() ran the encode inside the request, the first quiz package load returned 500 (43% of first loads in production). L2 encode errors now only skip Redis and count a metric; the request keeps the value already loaded.

## Watch out
A second request after the 500 succeeded from L1, which hid the bug as a "flaky first load". Grep the backend log for "Cannot encode cache" if a cache never shows keys in Redis.

In the code: LmsCacheDescriptorContributor (quizRuntimePackage), ErgTieredCache.put (metric l2/put/encode_error), test CacheDescriptorContributorTest.noCacheDeclaresAnOptionalValueBecauseSpringStoresTheUnwrappedValue.

---
title: SMTP timeouts are 30 seconds because the production mail relay takes about 9 seconds to accept a mail
author: Vuong
date: 2026-11-18
branch: fix/smtp-timeout-30s
id: l-43b17cb1
scope: repo
status: active
src: agent
touches:
  - src/main/resources/application.yml
  - src/test/java/com/erg/ergspringboot/platform/web/ApplicationYamlProfileBindingTest.java
---

## Decided
The mail socket timeouts default to 30 s. The relay (pro207.emailserver.vn:465) needs ~9 s to accept a mail when sent from the production server, so the old 5 s default made every send fail with "Read timed out". From a developer machine the same send is fast, so local tests never showed it.

## Tried and ruled out
- Wrong SMTP credentials or From address: the same config sends fine locally, and the server password matches.
- Outbox key mismatch: the stuck events were created after the container started, so they were encrypted and decrypted with the same key.

## Watch out
- Before blaming config, run a one-off send on the server itself (docker run --rm of the backend image with the backend's mail env) and print the exception chain; the backend before the outbox-logging fix logged nothing on a failed send.
- 30 s stays under the outbox lease (60 s, renewed per event). Going above the lease would let another instance reclaim and resend a code mail.

In the code: src/main/resources/application.yml spring.mail.properties.mail.smtp.{connectiontimeout,timeout,writetimeout}; production also sets SPRING_MAIL_SMTP_*_TIMEOUT=30000 in /opt/erg/ops/docker/.env.production.

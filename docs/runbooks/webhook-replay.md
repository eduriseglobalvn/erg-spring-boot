# Webhook replay response

Provider signatures, timestamp/replay windows and the durable inbox are the
source of truth. A repeated provider delivery should return the recorded
receipt and must not execute the business effect twice.

For an incident, capture provider, webhook id, receipt status, signature result,
tenant classification and correlation id. Replay only from the provider's
verified delivery log through the normal ingress endpoint; never bypass
verification by inserting a business event directly.

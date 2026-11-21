-- Handover approval now sets crm_school_deals.signed_at / signed_revenue, and the SEO KPIs count only
-- signed_at IS NOT NULL. Deals approved before that change (approved handover, stage 'active') or already in the
-- legacy 'signed' stage would drop out of the KPIs, so give them the same stamp.
-- signed_at: the approval time (crm_handovers.updated_at of the latest approved handover), else the deal's updated_at.
-- signed_revenue keeps an existing value and otherwise takes expected_revenue, as the approval code does.
-- One plain UPDATE (no DDL, no extra locks beyond the row locks); the crm tables use ENABLE (not FORCE) RLS, so the
-- migration owner sees every tenant, like V118.
UPDATE crm_school_deals d
SET signed_at = COALESCE(
        (SELECT max(h.updated_at) FROM crm_handovers h
          WHERE h.opportunity_id = d.id AND h.tenant_id = d.tenant_id AND h.status = 'approved'),
        d.updated_at),
    signed_revenue = COALESCE(d.signed_revenue, d.expected_revenue)
WHERE d.signed_at IS NULL
  AND (lower(d.stage) = 'signed'
       OR EXISTS (SELECT 1 FROM crm_handovers h
                   WHERE h.opportunity_id = d.id AND h.tenant_id = d.tenant_id AND h.status = 'approved'));

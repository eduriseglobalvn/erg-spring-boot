package com.erg.ergspringboot.notifications;

/** FAILED: a scheduled dispatch could not run (creator lost scope, audience too large or an error). */
public enum CampaignStatus { DRAFT, SCHEDULED, SENDING, SENT, CANCELLED, FAILED }

package com.erg.ergspringboot.notifications.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Opens the campaign API to admins and teachers; who they may address is checked by AudienceScopeGuard. */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@methodAuthorization.isAllowedAny(authentication, 'notification.campaign.send', '*')")
public @interface NotificationCampaignSend {
}

package com.erg.ergspringboot.platform.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Authentication transport settings shared by the BFF adapters. */
@Component
@Validated
@ConfigurationProperties("erg.auth")
public class ErgAuthProperties {

    private boolean bffEnabled;
    private String bffTenantId = "erg";

    @Valid
    private final RapidSessionReplacement rapidSessionReplacement = new RapidSessionReplacement();

    @Valid
    private final LoginRateLimit loginRateLimit = new LoginRateLimit();

    /** Wrong passwords per account inside {@link #failedLoginWindow} before the account is locked. */
    @Min(1)
    @Max(1000)
    private int maxFailedLogin = 5;
    private java.time.Duration failedLoginWindow = java.time.Duration.ofMinutes(15);
    private java.time.Duration blockDuration = java.time.Duration.ofMinutes(15);

    /** Argon2 hashes allowed at once (each one allocates ~16 MB); callers beyond this wait, then get 429. */
    @Min(1)
    @Max(256)
    private int passwordHashConcurrency = 8;

    public int getPasswordHashConcurrency() { return passwordHashConcurrency; }
    public void setPasswordHashConcurrency(int passwordHashConcurrency) { this.passwordHashConcurrency = passwordHashConcurrency; }

    public int getMaxFailedLogin() { return maxFailedLogin; }
    public void setMaxFailedLogin(int maxFailedLogin) { this.maxFailedLogin = maxFailedLogin; }
    public java.time.Duration getFailedLoginWindow() { return failedLoginWindow; }
    public void setFailedLoginWindow(java.time.Duration failedLoginWindow) { this.failedLoginWindow = failedLoginWindow; }
    public java.time.Duration getBlockDuration() { return blockDuration; }
    public void setBlockDuration(java.time.Duration blockDuration) { this.blockDuration = blockDuration; }

    @AssertTrue(message = "erg.auth.failed-login-window and erg.auth.block-duration must be positive")
    public boolean isLockoutDurationValid() {
        return failedLoginWindow != null && !failedLoginWindow.isNegative() && !failedLoginWindow.isZero()
                && blockDuration != null && !blockDuration.isNegative() && !blockDuration.isZero();
    }

    public boolean isBffEnabled() {
        return bffEnabled;
    }

    public void setBffEnabled(boolean bffEnabled) {
        this.bffEnabled = bffEnabled;
    }

    public String getBffTenantId() {
        return bffTenantId;
    }

    public void setBffTenantId(String bffTenantId) {
        this.bffTenantId = bffTenantId == null ? "" : bffTenantId.trim();
    }

    public RapidSessionReplacement getRapidSessionReplacement() {
        return rapidSessionReplacement;
    }

    public LoginRateLimit getLoginRateLimit() {
        return loginRateLimit;
    }

    @AssertTrue(message = "erg.auth.bff-tenant-id must not be blank")
    public boolean isBffTenantIdValid() {
        return bffTenantId != null && !bffTenantId.isBlank();
    }

    public static final class RapidSessionReplacement {
        @Min(1)
        @Max(100)
        private int threshold = 5;
        @Min(1)
        @Max(1440)
        private long windowMinutes = 10;

        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public long getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(long windowMinutes) { this.windowMinutes = windowMinutes; }
    }

    public static final class LoginRateLimit {
        private boolean enabled = true;
        @Min(1)
        @Max(100_000)
        private int sourcePerMinute = 30;
        @Min(1)
        @Max(100_000)
        private int accountPerMinute = 10;
        @Min(1)
        @Max(100_000)
        private int devicePerMinute = 20;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getSourcePerMinute() { return sourcePerMinute; }
        public void setSourcePerMinute(int sourcePerMinute) { this.sourcePerMinute = sourcePerMinute; }
        public int getAccountPerMinute() { return accountPerMinute; }
        public void setAccountPerMinute(int accountPerMinute) { this.accountPerMinute = accountPerMinute; }
        public int getDevicePerMinute() { return devicePerMinute; }
        public void setDevicePerMinute(int devicePerMinute) { this.devicePerMinute = devicePerMinute; }
    }
}

package com.erg.ergspringboot.platform.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("erg.webhook")
public class WebhookProperties {
    private int maxBodyBytes = 64 * 1024;
    private Duration replayTtl = Duration.ofHours(24);
    private Duration timestampTolerance = Duration.ofMinutes(5);
    private String discordPublicKey = "";
    private String telegramSecretToken = "";

    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }
    public Duration getReplayTtl() { return replayTtl; }
    public void setReplayTtl(Duration replayTtl) { this.replayTtl = replayTtl; }
    public Duration getTimestampTolerance() { return timestampTolerance; }
    public void setTimestampTolerance(Duration timestampTolerance) { this.timestampTolerance = timestampTolerance; }
    public String getDiscordPublicKey() { return discordPublicKey; }
    public void setDiscordPublicKey(String discordPublicKey) { this.discordPublicKey = discordPublicKey; }
    public String getTelegramSecretToken() { return telegramSecretToken; }
    public void setTelegramSecretToken(String telegramSecretToken) { this.telegramSecretToken = telegramSecretToken; }
}

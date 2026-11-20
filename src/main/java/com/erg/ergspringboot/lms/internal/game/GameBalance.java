package com.erg.ergspringboot.lms.internal.game;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable numbers of the HACK game: loot table, vault value, stars. Bound from {@code erg.games.balance.*}.
 * Weights are relative (they need not sum to 100); the vault payout is the "knob" of 1600-2000.
 */
@Component
@ConfigurationProperties(prefix = "erg.games.balance")
public class GameBalance {

    /** Gift kinds in roll order. */
    public static final String[] GIFT_ORDER = {"SCRAP", "SMALL_WALLET", "BIG_WALLET", "VAULT", "HACK_COMMAND"};

    private Map<String, Integer> weights = new LinkedHashMap<>(Map.of(
            "SCRAP", 60, "SMALL_WALLET", 22, "BIG_WALLET", 10, "VAULT", 3, "HACK_COMMAND", 5));
    private Map<String, Integer> coins = new LinkedHashMap<>(Map.of(
            "SCRAP", 50, "SMALL_WALLET", 150, "BIG_WALLET", 400, "VAULT", 1600, "HACK_COMMAND", 0));
    private int baseStars = 100;
    private int streakStars = 10;
    private int streakCap = 5;
    private int firewallStreak = 3;
    private int requeueAfter = 3;
    private int countdownMs = 3000;
    private int maxOpenRooms = 200;

    public int weight(String kind, boolean allowHack) {
        int base = weights.getOrDefault(kind, 0);
        if ("HACK_COMMAND".equals(kind)) return allowHack ? base : 0;
        // With hack off the hack-command share becomes scrap (65% total with the default table).
        return "SCRAP".equals(kind) && !allowHack ? base + weights.getOrDefault("HACK_COMMAND", 0) : base;
    }

    public int coinsOf(String kind) {
        return coins.getOrDefault(kind, 0);
    }

    public Map<String, Integer> getWeights() { return weights; }
    public void setWeights(Map<String, Integer> weights) { this.weights = weights; }
    public Map<String, Integer> getCoins() { return coins; }
    public void setCoins(Map<String, Integer> coins) { this.coins = coins; }
    public int getBaseStars() { return baseStars; }
    public void setBaseStars(int baseStars) { this.baseStars = baseStars; }
    public int getStreakStars() { return streakStars; }
    public void setStreakStars(int streakStars) { this.streakStars = streakStars; }
    public int getStreakCap() { return streakCap; }
    public void setStreakCap(int streakCap) { this.streakCap = streakCap; }
    public int getFirewallStreak() { return firewallStreak; }
    public void setFirewallStreak(int firewallStreak) { this.firewallStreak = firewallStreak; }
    public int getRequeueAfter() { return requeueAfter; }
    public void setRequeueAfter(int requeueAfter) { this.requeueAfter = requeueAfter; }
    public int getCountdownMs() { return countdownMs; }
    public void setCountdownMs(int countdownMs) { this.countdownMs = countdownMs; }
    public int getMaxOpenRooms() { return maxOpenRooms; }
    public void setMaxOpenRooms(int maxOpenRooms) { this.maxOpenRooms = maxOpenRooms; }
}

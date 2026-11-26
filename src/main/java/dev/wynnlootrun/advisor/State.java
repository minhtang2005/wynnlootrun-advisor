package dev.wynnlootrun.advisor;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

record State(Goal goal, Camp camp, int completed, int remaining, int failures, int timer, int choices,
             int orange, int activeOrange, int rainbow, int red, int aqua, int beaconRerolls, int pulls, int banked,
             int endRerolls, int sacrifices, int curses, int radiant, int healthCurse, int resistCurse,
             int radiantChance, int potency, int arc, int interest, int equilibrium, int bluePurpleOffers,
             Map<String, Integer> boons, Map<Beacon, Integer> used, Set<String> missions, Set<String> trials,
             Set<String> removed, List<Objective> objectives, boolean objectivesKnown, boolean removalKnown,
             boolean poolKnownExhausted, boolean bankSafe, boolean requiemActive,
             Set<Beacon> previousInterlude, List<Offer> offers) {
    static final int UNKNOWN = -1;

    enum Goal { MAX_PULL, SACRIFICE_STACK, CASHOUT, FLYING_CHEST }
    enum Camp {
        CANYON_SOUTH(150, .15, 575, -5025), CANYON_NORTH(150, .15, 575, -5025),
        CORKUS(180, .10, -1560, -2675), MOLTEN_HEIGHTS(150, .20, 1270, -5130),
        SKY_ISLANDS(150, .15, 1035, -4420), SILENT_EXPANSE(210, .10, 990, -785),
        FRUMA_WEST(300, .05, -2002, -775), FRUMA_EAST(300, .05, -1294, -1146),
        UNKNOWN(0, 0, 0, 0);

        final int seconds, x, z;
        final double hp;
        Camp(int seconds, double hp, int x, int z) {
            this.seconds = seconds;
            this.hp = hp;
            this.x = x;
            this.z = z;
        }
        boolean fruma() { return seconds == 300; }
    }

    enum Beacon {
        WHITE, RAINBOW, CRIMSON, GREY, DARK_GREY, ORANGE, AQUA, RED, VIOLET, PURPLE, PINK, BLUE, GREEN, YELLOW;
        String label() { return name().replace('_', ' '); }
    }
    enum Kind { MISSION, TRIAL, BOON, REMOVE, CHALLENGE_REWARD, REWARD, UNKNOWN }
    enum Target { CHALLENGES, CHESTS, TIME, PULLS, CURSES, RADIANT_MOBS, MOBS, BLUE, POTENCY, BEACON_OFFERS, UNKNOWN }

    record Offer(Beacon beacon, String task, String type, int distance, boolean vibrant, int shownTier) {
        String label() {
            if (!task.isBlank()) return beacon.label() + " / " + task;
            if (type.isBlank() || type.equals("UNKNOWN")) return beacon.label();
            return beacon.label() + " / " + type.replace('_', ' ') + (distance >= 0 ? " (" + distance + "m)" : "");
        }
    }

    record Objective(String name, Kind kind, Target target, int left) {}
    record Choice(String name, String description, int slot, int amount, List<Integer> cardSlots) {
        Choice(String name, String description, int slot, int amount) {
            this(name, description, slot, amount, List.of(slot));
        }
    }
    record Menu(Kind kind, List<Choice> choices, Set<String> actions, boolean targetLoot,
                boolean rewardsReadable, int pulls, int sacrifices) {
        static final Menu EMPTY = new Menu(Kind.UNKNOWN, List.of(), Set.of(), false, false, -1, -1);
        static final Menu CHALLENGE_REWARDS = new Menu(Kind.CHALLENGE_REWARD, List.of(), Set.of(), false, false, -1, -1);
    }

    record InputStatus(String summary, List<String> missing) {}

    InputStatus inputStatus(Menu menu, boolean choosingBeacon) {
        List<String> missing = new ArrayList<>();
        if (menu.kind() == Kind.CHALLENGE_REWARD) return new InputStatus("Current challenge", List.of());
        if (menu.kind() == Kind.REWARD) {
            if (!menu.rewardsReadable()) missing.add("Reward contents unconfirmed");
            if (menu.actions().isEmpty()) missing.add("Reward actions unconfirmed");
            return new InputStatus("Reward chest", List.copyOf(missing));
        }
        String summary = "";
        if (menu.kind() != Kind.UNKNOWN) {
            summary = "Read " + menu.choices().size() + " choices";
            if (menu.choices().isEmpty()) missing.add("Menu choices unread");
        } else if (choosingBeacon) {
            summary = "Read " + offers.size() + (choices > 0 ? "/" + choices : "") + " beacons";
            if (choices > offers.size()) missing.add("Beacon offer still incomplete");
            if (beaconRerolls < 0) missing.add("Beacon rerolls unknown");
        }
        if (timer < 0) missing.add("Timer unknown");
        if (objectives.stream().anyMatch(objective -> objective.left() < 0)) missing.add("Objective progress unknown");
        if (pulls < 0) missing.add("Current pulls unknown");
        if (curses < 0) missing.add("Curse count unknown");
        return new InputStatus(summary, List.copyOf(missing));
    }

    boolean has(String name) { return missions.contains(name) || trials.contains(name); }
    boolean active(String name) { return objectives.stream().anyMatch(objective -> objective.name.equals(name)); }
    int count(Beacon beacon) { return used.getOrDefault(beacon, UNKNOWN); }
    int headroom() {
        return failures < 0 || completed < 0 || remaining < 0 ? UNKNOWN
                : Math.max(0, 100 - failures - completed - remaining);
    }
    int boonCount() { return boons.values().stream().mapToInt(Integer::intValue).sum(); }
    boolean strictRunFacts() {
        return completed >= 0 && remaining > 0 && objectivesKnown && removalKnown;
    }
}

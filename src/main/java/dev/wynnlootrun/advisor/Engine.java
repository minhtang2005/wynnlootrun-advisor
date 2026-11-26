package dev.wynnlootrun.advisor;

import static dev.wynnlootrun.advisor.State.Beacon.*;
import static dev.wynnlootrun.advisor.State.Goal.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.wynnlootrun.advisor.State.Beacon;
import dev.wynnlootrun.advisor.State.Choice;
import dev.wynnlootrun.advisor.State.Menu;
import dev.wynnlootrun.advisor.State.Objective;
import dev.wynnlootrun.advisor.State.Offer;

final class Engine {
    static final String COURT = "King's Court", RITUAL = "Sacrificial Ritual", INDIGO = "Route Indigo",
            PURPLE_DOUBLE = "Porphyrophobia", EXORCIST = "Exorcist", ARC = "Training Arc", BEAT = "Backup Beat",
            OPTIMISM = "Optimism", DYING = "Dying Light", ALL_IN = "All In", MONO = "Monochromokopia",
            OSTINATO = "Ostinato", OPAL = "Opal Offering", MATERIAL = "Materialism",
            GREED = "Cleansing Greed", INTEREST = "Interest Scheme", LIGHTS = "Lights Out",
            CHRONO = "Chronotrigger", WARMTH = "Warmth Devourer", TREASURY = "Treasury Bill";
    static final Set<String> SAFE_REMOVAL = Set.of("High Roller", "Redemption", COURT);
    private static final double[][] BASE = {
        {1.45, 1, .75, 1.20, 1.15, .55, 1, 1.10},
        {.85, .95, 1.80, 1.30, 1.15, .45, 1.15, 1.20},
        {1.10, 1.55, 1.50, 1.25, 1.10, .45, 1.25, 1.25},
        {.70, .65, .50, 1.15, 1, 1.85, .90, 1.15}
    };
    private static final Set<String> LONG_TERM = Set.of(BEAT, RITUAL, "Complete Chaos", INDIGO,
            PURPLE_DOUBLE, "High Spirits", "Beleza Pura");
    private static final Comparator<Rating> ORDER = Comparator.comparingDouble(Rating::points).reversed()
            .thenComparing(Rating::label).thenComparingInt(Rating::slot);

    record Rating(String label, double points, int tier, int slot, List<Double> contributions, String reason) {
        String badge() {
            if (points >= 95) return "S+";
            if (points >= 82) return "S";
            if (points >= 68) return "A";
            if (points >= 52) return "B";
            if (points >= 35) return "C";
            return "AVOID";
        }
    }
    record Advice(String action, String explanation, List<Rating> ranked, List<String> warnings,
                  Rating recommended, String quickReason) {
        Advice(String action, String explanation, List<Rating> ranked, List<String> warnings) {
            this(action, explanation, ranked, warnings, null, explanation);
        }

        List<Rating> visibleRatings() {
            if (recommended == null) return ranked.stream().limit(3).toList();
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(recommended),
                    ranked.stream().filter(rating -> !rating.equals(recommended))).limit(3).toList();
        }
    }
    record Economy(double rerolls, double rerollValue, double sacrificeValue, double effectivePulls) {}
    enum Feasibility { FORMED, FEASIBLE, UNKNOWN, IMPOSSIBLE }
    record Combo(String name, Feasibility status, List<String> missions, List<String> trials, Set<Beacon> beacons) {}
    private record ComboFit(double points, String explanation) {}
    private record Candidate(Offer offer, Rating rating, double progress, double delay) {
        double rushScore() { return progress * delay; }

        Rating adjusted() {
            List<Double> axes = new ArrayList<>(rating.contributions);
            axes.set(7, rushScore());
            return new Rating(rating.label, rating.points + rushScore(), rating.tier, rating.slot,
                    List.copyOf(axes), rating.reason);
        }
    }

    private final double[] alpha;
    Engine(double[] tuning) {
        if (tuning.length != 8) throw new IllegalArgumentException("Expected eight weights");
        alpha = tuning.clone();
        for (int i = 0; i < alpha.length; i++) alpha[i] = Double.isFinite(alpha[i]) ? clamp(alpha[i], 0, 2) : 1;
    }

    Advice advise(State s, Menu menu) {
        if (menu.kind() == State.Kind.CHALLENGE_REWARD) return new Advice("Collect Challenge Rewards",
                "Take this challenge's rewards to continue.", List.of(), List.of());
        if (menu.kind() == State.Kind.REWARD) return rewards(s, menu);
        if (menu.kind() != State.Kind.UNKNOWN) return pick(s, menu);
        if (s.offers().isEmpty()) return new Advice("Waiting for visible offers",
                "Advice updates during a beacon interlude or a recognized choice screen.", List.of(), warnings(s));
        List<Combo> combos = combos(s);
        List<Candidate> eligible = s.offers().stream().map(offer -> beacon(s, offer, combos))
                .filter(candidate -> Double.isFinite(candidate.rating.points)).toList();
        boolean preferOther = orangeCovered(s) && eligible.stream().anyMatch(candidate -> candidate.offer.beacon() == ORANGE)
                && eligible.stream().anyMatch(candidate -> candidate.offer.beacon() != ORANGE);
        List<Candidate> options = preferOther
                ? eligible.stream().filter(candidate -> candidate.offer.beacon() != ORANGE).toList()
                : eligible;
        List<Rating> ranked = options.stream().map(Candidate::adjusted).sorted(ORDER).toList();
        if (ranked.isEmpty()) return new Advice("No eligible visible beacon", "Check the visible offers.",
                List.of(), warnings(s));
        Rating best = ranked.getFirst();
        boolean critical = s.timer() >= 0 && s.timer() < 240 || s.remaining() == 1;
        boolean rescueVisible = eligible.stream().anyMatch(candidate -> switch (candidate.offer.beacon()) {
            case GREEN -> !s.has(CHRONO) && !s.active(CHRONO);
            case WHITE, RED, AQUA, ORANGE, RAINBOW, GREY, PINK, DARK_GREY, CRIMSON -> true;
            case VIOLET -> s.has(EXORCIST);
            default -> candidate.progress > 0;
        });
        boolean noCombo = combos.stream().allMatch(combo -> combo.status == Feasibility.IMPOSSIBLE);
        boolean sufficientOffers = s.choices() > 0 && s.offers().size() >= s.choices();
        if (noCombo && s.strictRunFacts() && s.poolKnownExhausted() && s.beaconRerolls() == 0
                && sufficientOffers && !rescueVisible && s.bankSafe()
                && (s.completed() < 4 || s.pulls() >= 0 && s.pulls() < 30)
                && s.endRerolls() == 0 && s.sacrifices() == 0) {
            return new Advice("KILL RUN / consider forfeiting",
                    "All goal-compatible combos are infeasible, recovery is exhausted, and rewards are weak.",
                    ranked, warnings(s));
        }
        int threshold = s.choices() < 0 ? 66 : s.choices() >= 5 ? 74 : 66;
        threshold += (s.has(OPTIMISM) ? 6 : 0) + (s.has("Gourmand") ? 5 : 0)
                + (s.camp().fruma() && s.has(BEAT) ? 4 : 0) - (s.beaconRerolls() == 1 ? 12 : 0);
        boolean onlyLockedRecovery = !rescueVisible && (s.remaining() > 0 && s.remaining() <= 2
                && s.headroom() != 0 && s.previousInterlude().contains(RED)
                || s.timer() >= 0 && s.timer() < 180 && s.previousInterlude().contains(GREEN));
        if (s.beaconRerolls() > 0 && sufficientOffers && best.points < threshold && !onlyLockedRecovery
                && !(critical && rescueVisible)) {
            return new Advice("Consider beacon reroll", "Best visible score " + number(best.points)
                    + " is below " + threshold + " | " + best.reason, ranked, warnings(s));
        }

        Candidate rush = options.stream().filter(candidate -> candidate.progress > 0)
                .max(Comparator.comparingDouble(Candidate::progress)
                        .thenComparingDouble(candidate -> candidate.rating.points)
                        .thenComparing(candidate -> candidate.rating.label)).orElse(null);
        Candidate alternative = rush == null ? null : options.stream().filter(candidate -> candidate != rush)
                .min(Comparator.comparing(Candidate::rating, ORDER)).orElse(null);
        String timing = "";
        if (alternative != null) {
            double delta = rush.rating.points + rush.rushScore() - alternative.rating.points;
            timing = (delta >= 5 ? "Rush objective" : delta <= -5 ? "Delay objective" : "Close choice")
                    + " (" + (delta >= 0 ? "+" : "") + number(delta) + ")";
            if (delta <= -5) best = alternative.adjusted();
            if (delta >= 5) best = rush.adjusted();
        }
        String explanation = timing.isEmpty() ? best.reason : best.reason + " | " + timing;
        if (preferOther) explanation += " | Orange deferred: choice bonus already covered";
        return new Advice("Take " + best.label, explanation,
                ranked, warnings(s), best, timing.isEmpty() ? quickReason(best) : timing);
    }

    private static boolean orangeCovered(State s) {
        if (s.choices() >= 0) return s.choices() >= 6;
        return s.offers().size() >= 6 || s.activeOrange() >= 2 && s.orange() > 3;
    }

    private static String quickReason(Rating rating) {
        String reason = rating.reason;
        String fallback = NO_DIRECT_COMBO.explanation + " | ";
        if (reason.startsWith(fallback)) reason = reason.substring(fallback.length());
        int separator = reason.indexOf(" | ");
        return separator < 0 ? reason : reason.substring(0, separator);
    }

    int tier(State s, Offer offer) {
        if (offer.shownTier() >= 0) return Math.min(3, offer.shownTier());
        int vibrant = offer.vibrant() || s.rainbow() > 0 ? 1 : 0;
        int aqua = offer.beacon() == AQUA ? 0 : Math.max(0, s.aqua());
        boolean beleza = offer.beacon() != AQUA && s.has("Beleza Pura")
                && s.offers().stream().anyMatch(other -> other.beacon() == AQUA);
        int indigo = s.has(INDIGO) && (offer.beacon() == BLUE || offer.beacon() == PURPLE) ? 2 : 0;
        return Math.min(3, vibrant + aqua + (beleza ? 1 : 0) + indigo);
    }

    static double retention(int sacrifices) { return sacrifices <= 0 ? 0 : sacrifices / (sacrifices + 1.0); }
    static double chance(double effectivePulls, double oneIn) {
        return -Math.expm1(Math.max(0, effectivePulls) * Math.log1p(-1 / oneIn));
    }
    static Economy economy(State s) {
        double total = Math.max(0, s.pulls()) + Math.max(0, s.banked());
        int sacrifices = Math.max(0, s.sacrifices());
        boolean converts = s.has(ALL_IN) || s.goal() == CASHOUT;
        double rerolls = Math.max(0, s.endRerolls()) + (converts ? 3.0 * sacrifices : 0);
        double marginal = total / (rerolls + 1);
        double gamma = sacrifices < 4 ? 2.8 : sacrifices < 6 ? 2 : sacrifices < 8 ? 1.4 : 1;
        double sacrifice = converts ? 3 * marginal : s.goal() == SACRIFICE_STACK
                ? total * gamma / ((sacrifices + 1.0) * (sacrifices + 2.0)) : .6 * marginal;
        return new Economy(rerolls, marginal, sacrifice, total * (rerolls + 1));
    }

    private double[] weights(State s, Beacon beacon) {
        Economy economy = economy(s);
        int c = s.completed(), rem = s.remaining(), timer = s.timer();
        boolean allIn = s.active(ALL_IN), requiem = s.requiemActive();
        double curseMultiplier = requiem ? 0 : 1 + (allIn ? 1 : 0) - (s.has("Inner Peace") ? .33 : 0);
        double health = curseType(s.healthCurse(), s.curses(), Double.POSITIVE_INFINITY);
        double resistance = curseType(s.resistCurse(), s.curses(), 28);
        double radiant = curseType(s.radiant(), s.curses(), Double.POSITIVE_INFINITY) * curseMultiplier;
        double hp = 1 + (requiem ? 0 : s.camp().hp * Math.max(0, c))
                + .25 * health * curseMultiplier;
        double ehp = hp / (1 - Math.min(.70, .025 * resistance * curseMultiplier));
        double survival = (.65 + .08 * ehp) * (radiant >= 15 ? 3.4 : radiant >= 10 ? 2.35 : radiant >= 5 ? 1.55 : 1)
                + (allIn || s.active("Hubris") ? .6 : 0);
        double setup = switch (beacon) {
            case ORANGE -> s.choices() < 0 ? 1 : s.choices() < 6 ? 1.60 + .35 * (6 - s.choices())
                    : s.orange() < 0 ? 1 : s.orange() <= 3 ? 1.45 : s.orange() <= 8 ? .65 : .15;
            case RAINBOW -> s.has(DYING) ? 2.60 : s.rainbow() == 0 && c >= 10 ? 1.95
                    : s.rainbow() > 0 && s.rainbow() <= 4 ? 1.25 : s.rainbow() > 4 ? .25 : 1;
            case GREY -> c >= 30 && c <= 50 && s.missions().size() < 4 ? 1.2 + .05 * (c - 25) : 1;
            case CRIMSON -> c >= 20 && c <= 70 ? 1.35 + .06 * Math.max(0, c - 45) : 1;
            default -> 1;
        };
        if (c >= 0 && c <= 10 && (beacon == ORANGE && s.choices() < 6
                || beacon == WHITE || beacon == PINK || beacon == GREY)) setup *= 1.80;
        double longevity = s.headroom() == 0 ? 0 : rem < 0 ? 1 : rem <= 2 ? 3 : rem <= 5 ? 1.8 : rem <= 15 ? 1 : .45;
        if (s.has("Knife Edge")) longevity *= .35;
        if (beacon == GREEN) {
            longevity = timer < 0 ? 1 : timer < 180 ? 3.2 : timer < 300 ? 1.9 : timer <= 720 ? .9
                    : timer > 840 && (s.has(BEAT) || s.has(ARC)) ? -1.5 : .2;
            if ((s.active(CHRONO) && timer >= 0 && timer < 540) || s.active("Gambling Beast")) longevity = 1.8;
        }
        double[] modifiers = {
            (c >= 0 && c <= 10 ? .55 : 1 + .04 * Math.min(20, economy.rerolls)) + (s.active(TREASURY) ? .25 : 0),
            clamp(economy.rerollValue / 28, .70, 2.80),
            s.goal() == SACRIFICE_STACK || s.has(ALL_IN) ? clamp(economy.sacrificeValue / 30, 1.10, 3.20) : .75,
            setup, longevity,
            1 + (s.has(GREED) ? .55 : 0) + (s.has("Jester's Trick") ? .45 : 0)
                    + (s.active("Side Hustle") ? .80 : 0) + (s.has(MATERIAL) ? .35 : 0),
            survival, 1
        };
        for (int i = 0; i < modifiers.length; i++) modifiers[i] *= BASE[s.goal().ordinal()][i] * alpha[i];
        return modifiers;
    }

    private static double curseType(int count, int total, double cap) {
        return Math.min(cap, count >= 0 ? count : Math.max(0, total) / 5.0);
    }

    private Candidate beacon(State s, Offer offer, List<Combo> combos) {
        Beacon color = offer.beacon();
        int t = tier(s, offer);
        int c = s.completed(), headroom = s.headroom(), rem = s.remaining();
        String lock = beaconLock(s, color);
        if (!lock.isEmpty()) return new Candidate(offer,
                new Rating(offer.label(), Double.NEGATIVE_INFINITY, t, -1, List.of(), lock), 0, 0);
        double[] x = new double[8];
        double bonus = 0;
        int pulls = 0, curse = 0, chests = 0, time = 0, added = 0, potency = 0;
        switch (color) {
            case WHITE -> { added = 15 + 5 * t; x[3] = c < 11 ? 13 : 8; }
            case RAINBOW -> { x[3] = 16 + 4 * t; x[2] = s.has(DYING) ? 28 : 0; }
            case CRIMSON -> {
                x[3] = 24 + 6 * Math.min(t, 2);
                x[1] = s.goal() == CASHOUT ? 13 : 8;
                x[2] = s.goal() == SACRIFICE_STACK ? 17 : 6;
            }
            case GREY -> x[3] = 26 + 5 * Math.min(t, 2);
            case DARK_GREY -> {
                x[3] = 21 + 3 * t;
                bonus += s.missions().stream().anyMatch(SAFE_REMOVAL::contains) ? 40 : 0;
            }
            case ORANGE -> {
                x[3] = 16 + 3 * t;
                x[6] = s.has("Brass Tacks") ? 2 * Math.max(0, s.choices()) : 0;
            }
            case AQUA -> x[3] = (7 + 5 * Math.min(t, 2)) * (s.aqua() == 3 ? .25 : 1);
            case RED -> {
                added = 6 + 3 * t;
                if (s.timer() >= 0 && s.timer() < 300) bonus -= 22;
                if (s.has("Thrill Seeker") && s.red() >= 0) {
                    pulls += Math.min(3, 1 + s.red() / 6);
                    bonus += s.red() % 6 == 5 ? 12 : s.red() < 18 ? 6 : 0;
                }
            }
            case VIOLET -> {
                pulls = 10 + (s.has(ARC) ? Math.max(0, s.arc()) : 0);
                curse = (10 - 2 * t) * (s.has(EXORCIST) ? -1 : 1);
                if (s.has(EXORCIST) && s.has(PURPLE_DOUBLE)) bonus += t < 2 ? 10 : -7;
            }
            case PURPLE -> {
                pulls = (2 + 2 * t) * (s.has(PURPLE_DOUBLE) ? 2 : 1);
                curse = rem == 1 ? 0 : 2 + 2 * t;
            }
            case PINK -> {
                x[3] = 13 + 5 * t;
                bonus += (s.has(OPTIMISM) ? 14 : 0) + (s.has("Gourmand") ? 12 : 0)
                        - (s.camp().fruma() && s.has(BEAT) ? 12 : 0);
            }
            case BLUE -> {
                potency = 100 * (t + 1) + (s.has("Equilibrium") ? Math.max(0, s.equilibrium()) : 0);
                x[6] = potency / 20.0 / (1 + .09 * s.boonCount());
                if (s.has(OSTINATO)) pulls += 2 * s.boons().values().stream().mapToInt(Integer::intValue)
                        .max().stream().filter(n -> n > 0).map(n -> n + 1).sum();
                if (s.has(OPAL)) x[0] = .8 * opal(potency);
                if (s.has("Orphion's Grace")) x[6] *= 1.5;
            }
            case GREEN -> {
                time = 210 + 120 * t;
                if (s.has(CHRONO) || s.active(CHRONO)) {
                    if (s.pulls() >= 0) pulls += (int) (s.pulls() * .015 * time / 120);
                    time = -time;
                    if (s.timer() >= 0 && s.timer() + time < 180) bonus -= 80;
                }
                x[6] = 8;
                bonus -= s.has("Thrill Seeker") && s.red() > 0 ? 18 : 0;
            }
            case YELLOW -> {
                chests = 2 + t + (s.has(INTEREST) ? Math.max(0, s.interest()) : 0);
                x[6] = s.has(GREED) ? 1.4 * chests : 0;
            }
        }
        int actualAdded = headroom < 0 ? added : Math.min(added, headroom);
        x[0] += 2.3 * pulls;
        x[4] = 2.8 * actualAdded + time / 24.0;
        x[5] = 5 * chests;
        x[6] -= 1.4 * curse;
        if (offer.distance() >= 0) bonus -= Math.min(10, offer.distance() / 175.0);
        if (rem == 1 && actualAdded > 0) bonus += 50;
        if (color == GREEN && time > 0 && s.timer() >= 0 && s.timer() < 180) bonus += 25;

        double progress = 0, delay = 0;
        for (Objective objective : s.objectives()) {
            if (objective.left() <= 0 || rem == 1 && actualAdded == 0) continue;
            int gain = switch (objective.target()) {
                case CHALLENGES -> 1;
                case CHESTS -> chests + (offer.type().contains("LOOT") ? 2 : 0) + (s.has(MATERIAL) ? 2 : 0);
                case TIME -> s.active(CHRONO) || color == RED ? 0 : Math.max(0, time + ordinaryTime(s));
                case PULLS -> 1 + pulls;
                case CURSES -> Math.max(0, curse);
                case RADIANT_MOBS -> s.radiantChance() > 0 && combat(offer) ? 3 : Math.max(0, curse / 2);
                case MOBS -> combat(offer) ? 10 : 0;
                case BLUE -> color == BLUE ? 1 : 0;
                case POTENCY -> potency;
                case BEACON_OFFERS -> s.choices() <= 0 ? 0 : Math.min(6, s.choices() + (color == ORANGE ? 1 : 0));
                case UNKNOWN -> 0;
            };
            double fraction = clamp(gain / (double) objective.left(), 0, 1);
            double cost = delay(s, objective, gain);
            if (Math.abs(fraction * cost) > Math.abs(progress * delay)) {
                progress = fraction;
                delay = cost;
            }
        }
        delay *= BASE[s.goal().ordinal()][7] * alpha[7];
        ComboFit fit = beaconFit(s, combos, color, t);
        Rating rating = score(s, offer.label(), color, 24, x, bonus + fit.points, t, -1,
                fit.explanation + " | Tier " + t + (offer.shownTier() >= 0 ? " shown" : s.aqua() < 0 && color != AQUA ? " estimated" : "")
                        + " | " + (pulls > 0 ? "+" + pulls + " projected pulls" : color.label() + " effect"));
        return new Candidate(offer, rating, progress, delay);
    }

    private static int ordinaryTime(State s) {
        return s.timer() < 0 || s.timer() >= 900 ? 0 : Math.min(s.camp().seconds, 900 - s.timer());
    }
    private static boolean combat(Offer offer) {
        return offer.type().contains("SLAY") || offer.type().contains("DEFEND") || offer.type().contains("DESTROY");
    }
    private static int opal(int potency) { return 1 + 2 * Math.max(0, (potency - 100) / 50); }

    private static String beaconLock(State s, Beacon color) {
        int c = s.completed(), used = s.count(color);
        if (s.remaining() == 1 && Set.of(YELLOW, GREY, CRIMSON, BLUE).contains(color)) return "Last challenge";
        return switch (color) {
            case RAINBOW, VIOLET -> c >= 0 && c < 10 ? "Opens at C10" : "";
            case DARK_GREY -> c >= 0 && c < 15 || used >= (s.has(MONO) ? 3 : 2)
                    || s.objectivesKnown() && s.missions().isEmpty() ? "Removal unavailable" : "";
            case CRIMSON -> c >= 0 && (c < 20 || c > 70) || used >= (s.has(COURT) || s.removed().contains(COURT) ? 3 : 2)
                    || s.objectives().stream().anyMatch(o -> o.kind() == State.Kind.TRIAL) ? "Trial unavailable" : "";
            case GREY -> s.missions().size() >= 4 || s.objectives().stream().anyMatch(o -> o.kind() == State.Kind.MISSION)
                    || s.removalKnown() && s.count(DARK_GREY) >= 0 && used >= 3 + s.count(DARK_GREY) + (s.has(MONO) ? 1 : 0)
                    ? "Mission slot unavailable" : "";
            case WHITE -> s.headroom() == 0 || used >= (s.has(MONO) ? 2 : 1) ? "No challenge capacity or uses" : "";
            case RED -> s.headroom() == 0 ? "No challenge capacity" : "";
            case BLUE -> used >= 30 ? "Blue limit reached" : "";
            default -> "";
        };
    }

    private static double delay(State s, Objective o, int gain) {
        double bleed = switch (o.name()) {
            case WARMTH -> s.has(RITUAL) ? 0 : 35;
            case TREASURY -> 22;
            case DYING -> 25;
            case "Side Hustle", "Adrenaline Junkie", "Imperitia" -> 18;
            case "Sprezzatura" -> s.radiant() >= 10 ? 0 : -18;
            default -> 0;
        };
        double passive = switch (o.name()) {
            case BEAT -> s.camp().fruma() ? 20 : 11;
            case RITUAL -> 18;
            case PURPLE_DOUBLE -> 16;
            case "High Roller", "Redemption" -> 6;
            default -> 0;
        };
        double lock = o.kind() == State.Kind.MISSION && s.completed() >= 28 && s.missions().size() < 3
                ? 12 + 1.8 * (s.completed() - 28) : 0;
        int perTurn = switch (o.target()) {
            case PULLS -> 8;
            case CHESTS -> 4;
            case TIME -> Math.max(1, s.camp().seconds);
            case CURSES -> 4;
            case MOBS -> 10;
            case RADIANT_MOBS -> 3;
            case POTENCY -> 300;
            case BEACON_OFFERS -> Math.max(1, s.choices());
            default -> 1;
        };
        int turns = (o.left() + perTurn - 1) / perTurn;
        double deadline = s.remaining() == 2 && gain >= o.left() ? 120
                : s.remaining() > 0 && s.remaining() <= turns + 1 ? 45 : 0;
        return bleed + passive + lock + deadline;
    }

    private Rating score(State s, String label, Beacon beacon, double base, double[] x,
                         double bonus, int tier, int slot, String reason) {
        double[] weights = weights(s, beacon);
        List<Double> contributions = new ArrayList<>(8);
        double points = base + bonus;
        for (int i = 0; i < weights.length; i++) {
            double value = weights[i] * x[i];
            contributions.add(value);
            points += value;
        }
        return new Rating(label, points, tier, slot, List.copyOf(contributions), reason);
    }

    private static double clamp(double number, double low, double high) {
        return Math.max(low, Math.min(high, number));
    }
    private static String number(double n) { return String.format(Locale.ROOT, "%.0f", n); }

    private Advice pick(State s, Menu menu) {
        List<Rating> rows = new ArrayList<>();
        for (Choice choice : menu.choices()) {
            Catalog.Entry rule = Catalog.find(choice.name());
            if (rule == null) continue;
            Rating row = switch (menu.kind()) {
                case MISSION -> mission(s, rule, choice, false);
                case REMOVE -> mission(s, rule, choice, true);
                case TRIAL -> trial(s, rule, choice);
                case BOON -> boon(s, rule, choice);
                default -> null;
            };
            if (row != null) {
                ComboFit fit = menuFit(s, menu.kind(), choice, row.points);
                rows.add(new Rating(row.label, row.points + fit.points, row.tier, row.slot, row.contributions,
                        fit.explanation + " | " + row.reason));
            }
        }
        List<Rating> ranked = rows.stream().filter(r -> Double.isFinite(r.points)).sorted(ORDER).toList();
        if (ranked.isEmpty()) return new Advice("Waiting for eligible visible choices",
                "The menu may still be loading or use an unrecognized layout.", List.of(), warnings(s));
        Rating best = ranked.getFirst();
        String verb = menu.kind() == State.Kind.REMOVE ? "Remove " : "Choose ";
        return new Advice(verb + best.label, "Slot " + (best.slot + 1) + " | " + best.reason,
                ranked, warnings(s), best, quickReason(best));
    }

    private Rating mission(State s, Catalog.Entry rule, Choice choice, boolean removal) {
        if (rule.kind() != State.Kind.MISSION) return null;
        String name = rule.name();
        if (!removal && (s.removed().contains(name) || s.missions().contains(name))) return null;
        double[] x = switch (name) {
            case "High Roller" -> axes(0, 17, 0, 0, 0, 0, 0);
            case "Redemption" -> axes(0, 0, 21, 0, 0, 0, 0);
            case COURT -> axes(0, 0, 0, s.completed() > 70 ? 0 : 19, 0, 0, 0);
            case RITUAL -> axes(-3, 0, 0, 0, 17, 0, 0);
            case INDIGO, PURPLE_DOUBLE -> axes(16, 0, 0, 8, 0, 0, -3);
            case "Beleza Pura" -> axes(3, 0, 0, 7 + Math.max(0, s.choices()), 0, 0, 0);
            case EXORCIST -> axes(0, 0, 0, 0, 0, 0, 15);
            case "Orphion's Grace" -> axes(0, 0, 0, 0, 0, 0, 12);
            case "Complete Chaos" -> axes(9, 0, 0, 6, 4, 2, 0);
            case GREED -> axes(0, 0, 0, 0, 0, 4, 12);
            case MATERIAL -> axes(0, 0, 0, 0, 0, 13, 0);
            case "Balancing Act" -> axes(0, 0, 0, 0, 0, 0, 13);
            case "High Spirits" -> axes(0, 0, 0, 13, 0, 0, 0);
            case "Radiant Hunter" -> axes(s.radiantChance() > 0 ? 12 : 4, 0, 0, 0, 0, 0, 3);
            case "Knife Edge" -> axes(s.remaining() > 0 ? 4 * Math.max(0, 7 - s.remaining()) : 4, 0, 0, 0, 0, 0, 0);
            case ARC -> axes(s.camp().fruma() ? 15 : 10, 0, 0, 0, 0, 0, 0);
            case BEAT -> axes(0, 0, 0, s.camp().fruma() ? 20 : 10, 0, 0, 0);
            case "Jester's Trick" -> axes(4, 0, 0, 0, 4, 9, 0);
            case OSTINATO -> axes(11, 0, 0, 0, 0, 0, -3);
            case OPTIMISM, "Gourmand" -> axes(0, 0, 0, 8 + Math.min(8, Math.max(0, s.beaconRerolls())), 0, 0, 0);
            case "Inner Peace" -> axes(0, 0, 0, 0, 0, 0, 12);
            case "Chronokinesis" -> axes(8, 0, 0, 0, -4, 0, 0);
            case INTEREST -> axes(0, 0, 0, 0, 0, 15, 0);
            case "Equilibrium" -> axes(0, 0, 0, 3, 0, 0, 8);
            case "Thrill Seeker" -> axes(9, 0, 0, 0, 2, 0, 0);
            case OPAL -> axes(12, 0, 0, 0, 0, 0, -4);
            case "Requiem" -> axes(0, 0, 0, 0, 0, 0, 18);
            case "Temporal Spur" -> axes(6, 0, 0, 0, s.timer() >= 600 ? 2 : -10, 6, 0);
            case "Brass Tacks" -> axes(0, 0, 0, 0, 0, 0, 7);
            case "Stasis" -> axes(0, 0, 0, 0, 9, 0, 0);
            case "Hoarder" -> axes(0, 0, 0, 0, 0, 6, 0);
            default -> throw new IllegalStateException("Unscored mission " + name);
        };
        double synergy = synergy(s, name);
        if (LONG_TERM.contains(name)) {
            double factor = (100 - Math.max(0, s.completed())) / 100.0;
            for (int i = 0; i < x.length; i++) x[i] *= factor;
        }
        if (Set.of("Requiem", OPAL, "Balancing Act").contains(name) && (s.completed() > 45 || s.radiant() >= 10)) synergy += 12;
        if ((name.equals(BEAT) || name.equals(ARC)) && s.timer() > 900) synergy -= 15;
        if (name.equals("High Spirits") && s.rainbow() > 10) synergy -= 35;
        if (name.equals(OSTINATO) && (s.has(DYING) || s.active(DYING))) synergy -= 35;
        if (s.remaining() == 1 && !removal) synergy -= 90;
        Rating keep = score(s, name, WHITE, .65 * rule.ev(), x, synergy, -1, choice.slot(), rule.effect());
        if (!removal) return keep;
        boolean permanent = SAFE_REMOVAL.contains(name);
        return new Rating(name, permanent ? 100 + rule.ev() / 100.0 : 85 - keep.points, -1, choice.slot(), List.of(),
                permanent ? "Keeps the one-time reward and frees a mission slot." : "Lowest estimated value to keep.");
    }

    private Rating trial(State s, Catalog.Entry rule, Choice choice) {
        if (rule.kind() != State.Kind.TRIAL || s.trials().contains(rule.name())) return null;
        String name = rule.name();
        int c = s.completed(), rem = s.remaining(), time = s.timer(), rad = s.radiant();
        if (ALL_IN.equals(name) && s.goal() == SACRIFICE_STACK) return null;
        if ("Gambling Beast".equals(name) && (rem == 1 || c >= 0 && c < 65 && rem > 12)) return null;
        double[] x = switch (name) {
            case ALL_IN -> axes(0, 14 + 9 * Math.max(0, s.sacrifices()), 0, 0, 0, 0, -13);
            case TREASURY -> axes(Math.max(0, s.pulls()) * .22, 0, 0, 0, 0, 0, 0);
            case CHRONO -> axes(19, 0, 0, 0, time >= 540 ? 5 : -14, 0, 0);
            case "Adrenaline Junkie" -> axes(0, 23, 0, 0, 0, 0, -6);
            case "Hubris" -> axes(0, 12, 13, 0, 0, 0, -14);
            case "Imperitia", "Ultimate Sacrifice" -> axes(0, 0, 25, 0, 0, 0, -8);
            case WARMTH -> axes(0, 12, 13, 0, s.has(RITUAL) ? 0 : -16, 0, 0);
            case DYING -> axes(0, 0, Math.min(33, Math.max(0, Math.min(100 - Math.max(c, 0), 55)) * .65), 7, 0, 0, -3);
            case MONO -> axes(0, 0, 0, c > 50 ? 5 : 22, 0, 0, 0);
            case "Side Hustle" -> axes(0, 23, 0, 0, -20, 8, 0);
            case LIGHTS -> axes(2 * (Math.max(0, rad) + Math.max(0, s.radiantChance())), 0, 0, 0, 0, 0, 12);
            case "Sprezzatura" -> axes(0, 16, 0, 0, 0, 0, -10);
            case "Gambling Beast" -> axes(0, time >= 720 ? 26 : 9, 0, 0, -14, 0, 0);
            default -> throw new IllegalStateException("Unscored trial " + name);
        };
        double bonus = synergy(s, name);
        bonus += switch (name) {
            case ALL_IN -> (s.goal() == CASHOUT ? 25 : 0)
                    - (rad >= 10 && !s.has("Inner Peace") && !s.requiemActive() ? 38 : 0);
            case TREASURY -> s.pulls() >= 150 && c >= 50 ? 25 : s.pulls() >= 0 && s.pulls() < 60 ? -30 : 0;
            case CHRONO -> time >= 540 && c >= 25 && c <= 60 ? 20 : time >= 0 && time < 420 ? -38 : 0;
            case DYING -> (s.has(OSTINATO) ? -45 : 0) + (c > 65 ? -30 : c >= 20 && c <= 45
                    && (s.goal() == CASHOUT || s.goal() == SACRIFICE_STACK) ? 25 : 0);
            case WARMTH -> !s.has(RITUAL) && rem >= 0 && rem < 15 ? -32 : 0;
            case "Hubris" -> s.requiemActive() ? 20 : rad >= 10 || s.active(ALL_IN) ? -46 : 0;
            case "Ultimate Sacrifice" -> rad >= 10 && s.boonCount() > 0 ? -22 : 0;
            case "Side Hustle" -> s.goal() == FLYING_CHEST || s.has(MATERIAL) || s.has(INTEREST) ? 26 : -28;
            case LIGHTS -> rad > 5 ? 17 : 0;
            case "Sprezzatura" -> s.has(INDIGO) && s.has(PURPLE_DOUBLE) ? -22 : 0;
            case "Gambling Beast" -> (c >= 70 || rem >= 5 && rem <= 8) && time >= 720 ? 28 : -22;
            default -> 0;
        };
        if (rem == 1) bonus -= 100;
        if (rem > 0 && Set.of(ALL_IN, CHRONO, "Ultimate Sacrifice", "Hubris").contains(name)) {
            int observedDuration = VisibleText.firstNumber(Catalog.normalize(choice.description()),
                    "\\b(?:next|complete|survive|finish) ([0-9,]+) challenges?\\b");
            if (rem <= (observedDuration > 0 ? observedDuration : 12)) bonus -= 42;
        }
        return score(s, name, WHITE, .68 * rule.ev(), x, bonus, -1, choice.slot(), rule.effect());
    }

    private Rating boon(State s, Catalog.Entry rule, Choice choice) {
        if (rule.kind() != State.Kind.BOON) return null;
        int c = Math.max(0, s.completed()), rem = s.remaining(), timer = s.timer();
        boolean chest = s.goal() == FLYING_CHEST || s.has(MATERIAL) || s.has(INTEREST);
        double now = switch (rule.name()) {
            case "Retrograde Champion", "Mob Slaughter" -> c / 10.0;
            case "Madman" -> s.curses() < 0 ? .45 : s.curses() / 8.0;
            case "Lightbringer" -> s.boonCount() / 5.0;
            case "Parsimonious" -> (chest ? c * 1.5 : c) / 35.0;
            case "Clockworker" -> timer < 0 ? .5 : (timer / 60) / 15.0;
            case "Slowrunner" -> timer < 0 ? .5 : timer < 360 ? 1 : .15;
            case "Patient Champion" -> c >= 20 ? 1 : .4 + .03 * c;
            case "Persistent Champion" -> .1;
            case "Heavensent" -> .88;
            case "Killstreak" -> .80;
            case "Bad Omen" -> s.goal() == MAX_PULL || c < 80 ? .95 : .35;
            case "Midas Touch" -> c < 35 || s.has(OSTINATO) || s.has(DYING) ? .90 : .45;
            case "Looter", "Serendipity", "Picky Looter" -> chest ? .95 : .48;
            case "Persnickety" -> s.bluePurpleOffers() >= 20 ? 1 : .18;
            default -> throw new IllegalStateException("Unscored boon " + rule.name());
        };
        double future = switch (rule.name()) {
            case "Persistent Champion" -> Math.min((100 - c) / 15.0, rem < 0 ? 1 : rem / 15.0 + .10);
            case "Heavensent" -> rem >= 0 && rem <= 5 ? .55 : 1;
            case "Persnickety" -> rem > 20 || s.bluePurpleOffers() >= 20 ? .85 : .25;
            default -> now;
        };
        int duplicates = s.boons().getOrDefault(rule.name(), 0);
        double power = choice.amount() > 0 ? choice.amount() / 100.0 : 1.0;
        if (s.has("Orphion's Grace")) power *= 1.5;
        double score = 100 * (.65 * clamp(now, 0, 1) + .35 * clamp(future, 0, 1)) * power;
        if (s.has(OSTINATO) && duplicates > 0) score = .5 * score + 4.6 * (duplicates + 1);
        if (s.active(DYING) && now >= .85) score += 25;
        return new Rating(rule.name(), score, -1, choice.slot(), List.of(), rule.effect());
    }

    private static double[] axes(double pulls, double rerolls, double sacrifices, double setup,
                                 double longevity, double chests, double survival) {
        return new double[] {pulls, rerolls, sacrifices, setup, longevity, chests, survival, 0};
    }

    private static double synergy(State s, String name) {
        double total = 0;
        if (pair(s, name, PURPLE_DOUBLE, INDIGO) || pair(s, name, EXORCIST, ARC)
                || pair(s, name, GREED, MATERIAL) || pair(s, name, RITUAL, WARMTH)
                || name.equals(BEAT) && s.camp().fruma()) total += 24;
        for (String counterpart : List.of("Gourmand", BEAT, DYING)) {
            if (pair(s, name, OPTIMISM, counterpart)) total += 16;
        }
        if (pair(s, name, "Inner Peace", ALL_IN) || pair(s, name, "Equilibrium", DYING)
                || pair(s, name, "Equilibrium", OPAL)) total += 16;
        if (pair(s, name, "Knife Edge", RITUAL)) total -= 35;
        return total;
    }

    private static boolean pair(State s, String name, String a, String b) {
        return name.equals(a) && (s.has(b) || s.active(b)) || name.equals(b) && (s.has(a) || s.active(a));
    }

    List<Combo> combos(State s) {
        return combos(s, "");
    }

    private List<Combo> combos(State s, String offered) {
        List<Combo> combos = new ArrayList<>();
        if (s.goal() == MAX_PULL || s.goal() == CASHOUT) combos.add(combo(s, "Exodia",
                List.of(), List.of(DYING, ALL_IN), offered, RAINBOW));
        if (s.goal() == SACRIFICE_STACK) {
            for (String source : List.of("Imperitia", "Ultimate Sacrifice", "Hubris", WARMTH)) {
                combos.add(combo(s, "Sacrifice Vault / " + source, List.of(), List.of(DYING, source), offered, RAINBOW));
            }
        }
        if (s.goal() != FLYING_CHEST) {
            combos.add(combo(s, "Indigo Purple", List.of(PURPLE_DOUBLE, INDIGO), List.of(), offered, PURPLE, RAINBOW, AQUA));
            combos.add(combo(s, "Violet Hyperfarm", List.of(EXORCIST, ARC), List.of(), offered, VIOLET));
            combos.add(combo(s, "Indigo Hyperfarm / Treasury Bill", List.of(PURPLE_DOUBLE, INDIGO),
                    List.of(TREASURY), offered, PURPLE));
            combos.add(combo(s, "Indigo Hyperfarm / Chronotrigger", List.of(PURPLE_DOUBLE, INDIGO),
                    List.of(CHRONO), offered, PURPLE));
            combos.add(combo(s, "Ostinato Boon-Burner", List.of(OSTINATO), List.of(), offered, BLUE));
            combos.add(combo(s, "Opal Boon-Burner", List.of(OPAL, INDIGO), List.of(), offered, BLUE, AQUA));
        }
        if (s.camp().fruma() || s.camp() == State.Camp.UNKNOWN) {
            Combo fruma = combo(s, "Fruma Perpetual Reroll", List.of(BEAT, OPTIMISM), List.of(), offered, PINK);
            combos.add(s.camp() == State.Camp.UNKNOWN && fruma.status != Feasibility.IMPOSSIBLE
                    ? new Combo(fruma.name, Feasibility.UNKNOWN, fruma.missions, fruma.trials, fruma.beacons) : fruma);
        }
        combos.add(combo(s, "Flying Chest & Radiance Sweeper", List.of(MATERIAL, GREED), List.of(), offered, YELLOW));
        combos.add(combo(s, "Flying Chest / Side Hustle", List.of(MATERIAL, INTEREST),
                List.of("Side Hustle"), offered, YELLOW));
        combos.add(combo(s, "Flying Chest / Lights Out", List.of(MATERIAL, GREED),
                List.of(LIGHTS), offered, YELLOW));
        combos.add(combo(s, "Warmth Ritual", List.of(RITUAL), List.of(WARMTH), offered, PURPLE, VIOLET));
        if (s.goal() != SACRIFICE_STACK) {
            combos.add(combo(s, "Gambling Beast Cashout", List.of(), List.of("Gambling Beast"), offered, GREEN));
        }
        return List.copyOf(combos);
    }

    private static Combo combo(State s, String name, List<String> missions, List<String> trials,
                               String offered, Beacon... beacons) {
        Feasibility status = feasibility(s, missions, trials, offered);
        return new Combo(name, status, missions, trials, Set.of(beacons));
    }

    private static Feasibility feasibility(State s, List<String> missions, List<String> trials, String offered) {
        if (trials.contains(DYING) && (s.has(OSTINATO) || s.active(OSTINATO) || offered.equals(OSTINATO))
                || missions.contains(OSTINATO) && (s.has(DYING) || s.active(DYING) || offered.equals(DYING))
                || s.goal() == SACRIFICE_STACK && trials.contains(DYING) && (s.has(ALL_IN) || s.active(ALL_IN))
                || (missions.contains(OSTINATO) || missions.contains(OPAL)) && s.count(BLUE) >= 30
                || trials.contains(DYING) && !s.has(DYING) && s.count(BLUE) >= 30
                        && s.potency() >= 0 && s.potency() < 800
                || missions.stream().anyMatch(s.removed()::contains)) {
            return Feasibility.IMPOSSIBLE;
        }
        int missingM = missing(s, missions, offered);
        int missingT = missing(s, trials, offered);
        boolean pending = missions.stream().anyMatch(s::active) || trials.stream().anyMatch(s::active)
                || missions.contains(offered) || trials.contains(offered);
        if (missingM == 0 && missingT == 0 && !pending) return Feasibility.FORMED;
        if (!s.strictRunFacts()) return Feasibility.UNKNOWN;
        if (missingT > 0 && s.completed() > 70 || missingM > 0 && s.poolKnownExhausted()
                || missingT > 0 && s.poolKnownExhausted() || s.headroom() == 0 && s.remaining() <= 1) {
            return Feasibility.IMPOSSIBLE;
        }
        int freeMissionSlots = Math.max(0, 4 - s.missions().size());
        if (s.removalKnown() && s.count(DARK_GREY) >= 0) {
            int removals = Math.max(0, (s.has(MONO) ? 3 : 2) - s.count(DARK_GREY));
            if (missingM + (missions.contains(offered) ? 1 : 0) > freeMissionSlots + removals) {
                return Feasibility.IMPOSSIBLE;
            }
        }
        int crimson = s.count(CRIMSON);
        int cap = s.has(COURT) || s.active(COURT) || s.removed().contains(COURT) || offered.equals(COURT) ? 3 : 2;
        boolean canAcquireCourt = s.completed() <= 50 && !s.removed().contains(COURT) && freeMissionSlots > 0;
        if (crimson >= 0 && missingT > Math.max(0, Math.max(cap, canAcquireCourt ? 3 : 2) - crimson)) {
            return Feasibility.IMPOSSIBLE;
        }
        return Feasibility.FEASIBLE;
    }

    private static int missing(State s, List<String> requirements, String offered) {
        return (int) requirements.stream().filter(name -> !name.equals(offered) && !s.has(name) && !s.active(name)).count();
    }

    private static final ComboFit NO_DIRECT_COMBO = new ComboFit(0, "Combo: no direct link | Dynamic score");

    private static ComboFit bestFit(ComboFit current, double points, Combo combo, String effect) {
        if (combo.status == Feasibility.UNKNOWN) points *= .5;
        return points <= current.points ? current : new ComboFit(points,
                "Combo: " + combo.name + " | " + effect
                        + (combo.status == Feasibility.UNKNOWN ? " (unconfirmed)" : ""));
    }

    private static ComboFit beaconFit(State s, List<Combo> combos, Beacon color, int tier) {
        ComboFit best = NO_DIRECT_COMBO;
        for (Combo combo : combos) {
            if (combo.status == Feasibility.IMPOSSIBLE) continue;
            int missions = missing(s, combo.missions, ""), trials = missing(s, combo.trials, "");
            if (combo.beacons.contains(color) && combo.status == Feasibility.FORMED
                    && !(color == VIOLET && s.has(EXORCIST) && s.has(PURPLE_DOUBLE) && tier > 1)) {
                best = bestFit(best, 8, combo, "use active synergy");
            }
            if (color == BLUE && combo.trials.contains(DYING) && s.active(DYING)) {
                best = bestFit(best, 7 + tier, combo, "build Dying Light potency");
            }
            if (color == RAINBOW && combo.trials.contains(DYING) && s.has(DYING)) {
                best = bestFit(best, 9, combo, "Rainbow yields a sacrifice");
            }
            if (color == GREY && missions > 0 || color == CRIMSON && trials > 0
                    || color == DARK_GREY && missions > 0 && s.missions().stream().anyMatch(SAFE_REMOVAL::contains)) {
                best = bestFit(best, 7, combo, "opens a slot for a missing component");
            }
            if (s.completed() >= 0 && s.completed() <= 10 && (missions + trials > 0 || combo.status == Feasibility.FORMED)) {
                switch (color) {
                    case ORANGE -> {
                        if (s.choices() < 6) best = bestFit(best, 6, combo, "more early beacon choices");
                    }
                    case WHITE -> best = bestFit(best, 5, combo, "more challenges to assemble the combo");
                    case PINK -> best = bestFit(best, 4, combo, "bank early rerolls to find components");
                    default -> {}
                }
            }
        }
        return best;
    }

    private ComboFit menuFit(State s, State.Kind kind, Choice choice, double baseScore) {
        ComboFit best = NO_DIRECT_COMBO;
        String name = choice.name();
        String offered = kind == State.Kind.MISSION || kind == State.Kind.TRIAL ? name : "";
        for (Combo combo : combos(s, offered)) {
            if (combo.status == Feasibility.IMPOSSIBLE) continue;
            int missing = missing(s, combo.missions, offered) + missing(s, combo.trials, offered);
            if (kind == State.Kind.REMOVE && combo.missions.contains(name)) {
                return new ComboFit(-12, "Combo: removing this breaks " + combo.name);
            }
            if (kind == State.Kind.REMOVE && SAFE_REMOVAL.contains(name) && missing > 0) {
                best = bestFit(best, 7, combo, "preserve the one-time reward and reopen a slot");
            }
            if (kind == State.Kind.MISSION && combo.missions.contains(name)
                    || kind == State.Kind.TRIAL && combo.trials.contains(name)) {
                best = bestFit(best, missing == 0 ? 13 : 8, combo,
                        missing == 0 ? "secures the final component on completion" : "adds a missing component");
            }
            if (kind == State.Kind.MISSION && name.equals(COURT) && missing(s, combo.trials, "") > 0
                    && s.completed() <= 70) {
                best = bestFit(best, 8, combo, "extra Crimson for a missing trial");
            }
            if (kind == State.Kind.BOON && combo.trials.contains(DYING)
                    && s.active(DYING) && baseScore >= 85) {
                best = bestFit(best, 7, combo, "supports sustained Dying Light potency");
            }
            if (kind == State.Kind.BOON && combo.status == Feasibility.FORMED && combo.beacons.contains(BLUE)) {
                if (combo.missions.contains(OSTINATO) && s.boons().containsKey(name)) {
                    best = bestFit(best, 7, combo, "duplicate boon yields pulls");
                } else if (combo.missions.contains(OPAL) && choice.amount() >= 200) {
                    best = bestFit(best, 6, combo, "high potency for Opal Offering");
                }
            }
        }
        return best;
    }

    private Advice rewards(State s, Menu menu) {
        Set<String> actions = menu.actions();
        if (!menu.rewardsReadable()) {
            String conditional = actions.contains("reroll") ? "If there is no target loot, a visible reroll may be useful."
                    : actions.contains("sacrifice") && s.goal() == SACRIFICE_STACK && menu.sacrifices() >= 5
                    ? "If there is no target loot, sacrifice keeps " + percent(menu.sacrifices()) + "."
                    : "Loot or available actions could not be confirmed.";
            return new Advice("Inspect reward chest", conditional, List.of(), List.of());
        }
        String action, reason;
        int sacrifices = menu.sacrifices();
        if (menu.targetLoot()) {
            action = "Claim target reward";
            reason = "A visible Shiny or Mythic is already available.";
        } else if (actions.contains("reroll")) {
            action = "Consider reward reroll";
            reason = "No target reward is identified. Review the replacement cost in the chest.";
        } else if (actions.contains("sacrifice") && s.goal() == SACRIFICE_STACK && !s.has(ALL_IN) && sacrifices >= 5) {
            action = "Consider sacrifice";
            reason = "Keeps " + percent(sacrifices) + " of pulls for this camp.";
        } else if (actions.contains("claim")) {
            action = "Claim rewards";
            reason = "Review the visible rewards and any available sacrifice before closing.";
        } else {
            action = "Review reward actions";
            reason = "A safe action could not be confirmed.";
        }
        return new Advice(action, reason, List.of(), List.of());
    }

    private static String percent(int sacrifices) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * retention(sacrifices));
    }

    private static List<String> warnings(State s) {
        List<String> messages = new ArrayList<>();
        if (s.timer() > 900 && (s.has(BEAT) || s.has(ARC))) messages.add("Timer above 15m blocks ordinary time bonuses.");
        if (s.timer() >= 0 && s.timer() < 240) messages.add("Timer below 4m.");
        if (s.radiant() >= 15) messages.add("Radiant 15+: exponential scaling.");
        else if (s.radiant() >= 10) messages.add("Radiant 10+: Radiance spreads.");
        else if (s.radiant() >= 5) messages.add("Radiant 5+: explosions on death.");
        if (s.remaining() > 0 && s.remaining() <= 2) messages.add("Last challenge can suppress objective rewards.");
        if (s.orange() > 0 && s.orange() <= 3) messages.add("Orange choice bonus expires soon.");
        if (s.rainbow() > 0 && s.rainbow() <= 3) messages.add("Vibrant bonus expires soon.");
        if (s.completed() >= 64 && s.completed() <= 70) messages.add("Crimson window is ending.");
        if (!s.objectivesKnown()) messages.add("Objective status unconfirmed.");
        return List.copyOf(messages);
    }
}

package dev.wynnlootrun.advisor;

import static dev.wynnlootrun.advisor.State.Beacon.*;
import static dev.wynnlootrun.advisor.State.Goal.*;
import static dev.wynnlootrun.advisor.State.Kind.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EngineTest {
    private static final Engine ENGINE = new Engine(new double[] {1, 1, 1, 1, 1, 1, 1, 1});
    private static int checks;

    public static void main(String[] args) {
        math();
        beacons();
        orangeCoverage();
        everyOption();
        missionTrialAndBoon();
        comboDrivenOffers();
        recommendationMatchesHighlight();
        rewardRules();
        forfeiting();
        visibleInputs();
        clipInputs();
        duplicateCardSlots();
        System.out.println("Passed " + checks + " deterministic checks.");
    }

    private static void duplicateCardSlots() {
        String title = "\uE03B\uF02C";
        var rows = List.of(
                new VisibleText.Row(11, "Temporal Spur", List.of("Click to choose!"), false),
                new VisibleText.Row(13, "Stasis", List.of("Click to choose!"), false),
                new VisibleText.Row(15, "King’s Court", List.of(), false),
                new VisibleText.Row(20, "Temporal Spur", List.of("Click to choose!"), false),
                new VisibleText.Row(22, "Stasis", List.of("Click to choose!"), false),
                new VisibleText.Row(24, "§cKing's Court", List.of("Click to choose!"), false));
        var menu = VisibleText.menu(title, rows, true, false);
        expect(menu.kind() == MISSION && menu.choices().size() == 3, "Six recognized slots are three distinct cards");
        var court = menu.choices().getLast();
        expect(court.name().equals("King's Court") && court.slot() == 24 && court.cardSlots().equals(List.of(15, 24)),
                "Prefer the clickable copy and keep both slots for the outline");
        var fixture = new Fixture();
        fixture.goal = CASHOUT;
        var advice = ENGINE.advise(fixture.state(), menu);
        expect(advice.ranked().stream().filter(row -> row.label().equals("King's Court")).count() == 1,
                "The same mission cannot occupy two places in the ranking");
    }

    private static void math() {
        close(Engine.retention(7), .875);
        close(Engine.retention(0), 0);
        close(Engine.chance(100_000, 100_000), .6321223982);
        Fixture fixture = new Fixture();
        fixture.goal = CASHOUT;
        fixture.pulls = 400;
        fixture.bank = 1193;
        fixture.endRerolls = 4;
        fixture.sacrifices = 6;
        var economy = Engine.economy(fixture.state());
        close(economy.rerolls(), 22);
        close(economy.effectivePulls(), 36639);
        close(economy.sacrificeValue(), 3 * 1593.0 / 23);
    }

    private static void beacons() {
        Fixture fixture = new Fixture();
        fixture.missions = Set.of("Porphyrophobia", "Route Indigo", "Beleza Pura");
        fixture.offers = List.of(offer(PURPLE, true), offer(AQUA, false));
        fixture.aqua = 2;
        expect(ENGINE.tier(fixture.state(), fixture.offers.getFirst()) == 3, "Vibrant, Indigo and Aqua cap at tier 3");
        expect(ENGINE.tier(fixture.state(), fixture.offers.getLast()) == 0, "Aqua excludes its own stacks and Beleza");
        fixture.aqua = 0;
        expect(ENGINE.tier(fixture.state(), offer(BLUE, false)) == 3, "Beleza buffs non-Aqua");
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take PURPLE"), "Purple is offered and pays +16 pulls");
        fixture.remaining = 1;
        fixture.offers = List.of(offer(BLUE, true), offer(YELLOW, true), offer(PURPLE, true), offer(WHITE, true));
        var advice = ENGINE.advise(fixture.state(), State.Menu.EMPTY);
        expect(advice.action().startsWith("Take WHITE"), "White can extend the final challenge");
        expect(advice.ranked().stream().noneMatch(row -> row.label().startsWith("BLUE") || row.label().startsWith("YELLOW")),
                "Blue and Yellow are masked at the last challenge");
        fixture.remaining = 8;
        fixture.c = 19;
        fixture.offers = List.of(offer(CRIMSON, false));
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).ranked().isEmpty(), "Crimson gate C20");
        fixture.c = 70;
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).ranked().size() == 1, "Crimson includes C70");
        fixture.c = 71;
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).ranked().isEmpty(), "Crimson closes after C70");
        fixture.c = 70;
        fixture.redRerolls = 4;
        fixture.choices = 3;
        fixture.offers = List.of(offer(YELLOW, false), offer(GREEN, false), offer(AQUA, false));
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().contains("reroll"), "Poor complete visible offer can reroll");
        fixture.choices = 6;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().contains("reroll"), "Partially loaded offers cannot recommend reroll");
        fixture.choices = 3;
        fixture.timer = 120;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().contains("reroll"), "Visible emergency recovery takes precedence");
        fixture.timer = 400;
        fixture.redRerolls = 0;
        fixture.c = 25;
        fixture.remaining = 2;
        fixture.objectives = List.of(new State.Objective("Dying Light", TRIAL, State.Target.POTENCY, 100));
        fixture.offers = List.of(offer(BLUE, false), offer(GREEN, false));
        advice = ENGINE.advise(fixture.state(), State.Menu.EMPTY);
        expect(advice.action().startsWith("Take BLUE") && advice.explanation().contains("Rush objective"), "Rush to complete before last challenge");
        fixture.remaining = 16;
        fixture.timer = 160;
        fixture.objectives = List.of(new State.Objective("Sprezzatura", TRIAL, State.Target.CURSES, 6));
        fixture.offers = List.of(offer(VIOLET, false), offer(GREEN, false));
        advice = ENGINE.advise(fixture.state(), State.Menu.EMPTY);
        expect(advice.action().startsWith("Take GREEN") && advice.explanation().contains("Delay objective"),
                "Sprezzatura preserves its reroll timer while survival needs Green");
    }

    private static void everyOption() {
        Fixture fixture = new Fixture();
        expect(Catalog.ENTRIES.size() == 64, "All 64 mission, trial, and boon entries loaded");
        for (var goal : State.Goal.values()) {
            fixture.goal = goal;
            fixture.c = 30;
            fixture.remaining = 12;
            fixture.missions = Set.of("High Roller");
            fixture.offers = java.util.Arrays.stream(State.Beacon.values()).map(b -> offer(b, false)).toList();
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).ranked().size() == 14, "All 14 beacon types for " + goal);
            for (var kind : List.of(MISSION, TRIAL, BOON)) {
                var options = Catalog.ENTRIES.stream().filter(entry -> entry.kind() == kind).map(Catalog.Entry::name).toList();
                var advice = ENGINE.advise(fixture.state(), picks(kind, options.toArray(String[]::new)));
                int size = options.size() - (kind == MISSION || kind == TRIAL && goal == SACRIFICE_STACK ? 1 : 0);
                expect(advice.ranked().size() == size, "Every visible legal " + kind + " for " + goal);
                expect(advice.equals(ENGINE.advise(fixture.state(), picks(kind, options.toArray(String[]::new)))),
                        "Deterministic " + kind + " scores for " + goal);
            }
        }
    }

    private static void missionTrialAndBoon() {
        Fixture fixture = new Fixture();
        fixture.goal = SACRIFICE_STACK;
        fixture.sacrifices = 4;
        var advice = ENGINE.advise(fixture.state(), picks(TRIAL, "Ultimate Sacrifice", "All In"));
        expect(advice.ranked().size() == 1 && advice.action().equals("Choose Ultimate Sacrifice"), "All In hard block");
        advice = ENGINE.advise(fixture.state(), picks(REMOVE, "Route Indigo", "High Roller"));
        expect(advice.action().equals("Remove High Roller"), "Dark Grey favors permanent payout");
        fixture.objectives = List.of(new State.Objective("Dying Light", TRIAL, State.Target.POTENCY, 300));
        fixture.timer = 800;
        expect(ENGINE.advise(fixture.state(), picks(BOON, "Slowrunner", "Patient Champion")).action()
                .equals("Choose Patient Champion"), "Prioritize sustained potency when clearing Dying Light");
        fixture.removed = Set.of("Route Indigo");
        expect(ENGINE.advise(fixture.state(), picks(MISSION, "Route Indigo")).ranked().isEmpty(), "Removed mission does not return");
    }

    private static void rewardRules() {
        Fixture fixture = new Fixture();
        fixture.goal = SACRIFICE_STACK;
        var safe = Set.of("claim", "reroll", "sacrifice");
        expect(ENGINE.advise(fixture.state(), new State.Menu(REWARD, List.of(), safe, true, true, 500, 7))
                .action().startsWith("Claim target"), "Target reward overrides reroll and sacrifice");
        expect(ENGINE.advise(fixture.state(), new State.Menu(REWARD, List.of(), safe, false, true, 500, 7))
                .action().contains("reroll"), "Reroll before sacrifice");
        expect(ENGINE.advise(fixture.state(), new State.Menu(REWARD, List.of(), Set.of("claim", "sacrifice"), false, true, 500, 7))
                .explanation().contains("87.5%"), "Sacrifice reports rational retention");
        expect(ENGINE.advise(fixture.state(), new State.Menu(REWARD, List.of(), safe, false, false, 500, 7))
                .action().startsWith("Inspect"), "Unknown rewards guard");
    }

    private static void comboDrivenOffers() {
        Fixture fixture = new Fixture();
        fixture.c = 0;
        fixture.choices = 3;
        fixture.offers = List.of(offer(WHITE, false), offer(ORANGE, false), offer(PINK, false));
        var advice = ENGINE.advise(fixture.state(), State.Menu.EMPTY);
        expect(advice.action().equals("Take ORANGE / Visible Task"), "Early challenge comes from the visible offer");
        expect(advice.explanation().contains("more early beacon choices")
                && advice.ranked().stream().allMatch(row -> row.reason().contains("Combo:")),
                "Early offers explain how they set up a combo");

        fixture.c = 4;
        fixture.missions = Set.of("Porphyrophobia");
        var menu = picks(MISSION, "Route Indigo", "Stasis");
        advice = ENGINE.advise(fixture.state(), menu);
        expect(advice.action().equals("Choose Route Indigo") && advice.explanation().contains("Indigo Purple")
                && advice.explanation().contains("final component"), "Mission offer completes the right combo");
        fixture.missions = Set.of();
        var withoutPartner = ENGINE.advise(fixture.state(), menu);
        expect(advice.ranked().getFirst().points() > withoutPartner.ranked().getFirst().points(),
                "Known mission partner changes the numeric ranking");

        fixture.goal = CASHOUT;
        fixture.c = 68;
        fixture.sacrifices = 5;
        fixture.trials = Set.of("Dying Light");
        fixture.used = Map.of(CRIMSON, 2);
        advice = ENGINE.advise(fixture.state(), picks(TRIAL, "All In", "Side Hustle"));
        expect(advice.action().equals("Choose All In") && advice.explanation().contains("Exodia"),
                "The currently visible final Crimson offer counts when checking Exodia");
        fixture.goal = SACRIFICE_STACK;
        advice = ENGINE.advise(fixture.state(), picks(TRIAL, "All In", "Ultimate Sacrifice"));
        expect(advice.action().equals("Choose Ultimate Sacrifice") && advice.explanation().contains("Sacrifice Vault"),
                "A different goal uses a different feasible combo for the same offer");
        fixture.trials = Set.of();
        fixture.missions = Set.of("Ostinato");
        advice = ENGINE.advise(fixture.state(), picks(TRIAL, "Dying Light"));
        expect(!advice.explanation().contains("Sacrifice Vault"), "An anti-synergy blocks the combo bonus");
    }

    private static void forfeiting() {
        Fixture fixture = new Fixture();
        fixture.c = 98;
        fixture.choices = 1;
        fixture.remaining = 2;
        fixture.endRerolls = 0;
        fixture.sacrifices = 0;
        fixture.pulls = 12;
        fixture.offers = List.of(offer(PURPLE, false));
        fixture.poolExhausted = true;
        fixture.bankSafe = true;
        expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "All combos and recovery exhausted");
        fixture.redRerolls = -1;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "Unknown rerolls suppress forfeiting");
        fixture.redRerolls = 0;
        fixture.missions = Set.of("Porphyrophobia", "Route Indigo");
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "Formed combo prevents forfeiting");
        fixture.missions = Set.of();
        fixture.poolExhausted = false;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "Open pool prevents forfeiting");
        fixture.poolExhausted = true;
        fixture.known = false;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "Unknown history prevents forfeiting");
        fixture.known = true;
        fixture.choices = 4;
        expect(!ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("KILL RUN"), "Incomplete offer prevents forfeiting");
    }

    private static void visibleInputs() {
        var rows = List.of(new VisibleText.Row(11, "§bKing’s Court", List.of("Complete 3 challenges"), false),
                new VisibleText.Row(15, "High Roller", List.of("Already selected"), false));
        var menu = VisibleText.menu("Choose a Mission!", rows, true, false);
        expect(menu.kind() == MISSION && menu.choices().size() == 1 && menu.choices().getFirst().slot() == 11, "Read active server menu slots only");
        expect(VisibleText.menu("Remove a Mission", rows, true, false).kind() == REMOVE, "Read mission removal intent");
        expect(VisibleText.menu("Choose a Mission!", rows, false, false).kind() == UNKNOWN, "Outside runs ignore lookalike menus");
        expect(VisibleText.objective("Collect Pulls 14/25", Catalog.find("Treasury Bill")).left() == 11, "Parse objective progress");
        expect(VisibleText.objective("800% active boon potency", Catalog.find("Dying Light")).left() == -1, "A goal alone is not progress");
        var reward = VisibleText.menu("", List.of(
                new VisibleText.Row(4, "Close Chest", List.of(), false),
                new VisibleText.Row(6, "Reroll rewards", List.of("0 rerolls remaining"), false),
                new VisibleText.Row(9, "Unidentified Mythic Wand", List.of(), false)), false, true);
        expect(reward.targetLoot() && reward.rewardsReadable() && !reward.actions().contains("reroll"), "Reward parsing uses visible loot and guards unavailable rerolls");
    }

    private static void recommendationMatchesHighlight() {
        Fixture fixture = new Fixture();
        fixture.c = 63;
        fixture.remaining = 5;
        fixture.timer = 296;
        fixture.objectives = List.of(new State.Objective("High Roller", MISSION, State.Target.CHESTS, 8),
                new State.Objective("Warmth Devourer", TRIAL, State.Target.PULLS, 4));
        fixture.offers = List.of(new State.Offer(GREY, "", "SLAY", 50, false, 0),
                new State.Offer(BLUE, "", "LOOT", 50, false, 1),
                new State.Offer(RAINBOW, "", "SLAY", 50, false, 2),
                new State.Offer(VIOLET, "", "LOOT", 50, false, 1),
                new State.Offer(DARK_GREY, "", "SLAY", 50, false, 3),
                new State.Offer(PINK, "", "SLAY", 50, false, 2));
        var advice = ENGINE.advise(fixture.state(), State.Menu.EMPTY);
        expect(advice.ranked().getFirst().label().startsWith("RAINBOW"), "Audit fixture keeps score order available");
        expect(advice.action().startsWith("Take VIOLET") && advice.recommended().equals(advice.visibleRatings().getFirst()),
                "Rush or Delay recommendation owns the HUD highlight");
        fixture.offers = List.of(offer(GREEN, false), offer(PURPLE, false));
        fixture.choices = 4;
        fixture.redRerolls = -1;
        var missing = fixture.state().inputStatus(State.Menu.EMPTY, true);
        expect(missing.summary().equals("Read 2/4 beacons") && missing.missing().contains("Beacon rerolls unknown"),
                "HUD reports incomplete offer and unknown rerolls separately");
    }

    private static void clipInputs() {
        var source = List.of("Lootrun:", "Collect your rewards!", "- Time Left: 15:00 [+43s]", "- Challenges: 26/51",
                "Hubris:", "- Complete 0/10 Challenges", "Opal Offering:", "- Add 12.5/18m to your timer");
        var sidebar = VisibleText.lootrunSidebar(source);
        expect(sidebar.collecting() && !sidebar.choosing() && sidebar.remaining() == 25 && sidebar.timer() == 900,
                "Challenge Rewards is its own phase");
        var objectives = VisibleText.sidebarObjectives(source);
        expect(objectives.size() == 2 && objectives.getFirst().left() == 10
                && objectives.getLast().left() == 330 && objectives.getLast().target() == State.Target.TIME,
                "Use live Hubris progress and convert fractional minutes to seconds");
        var redemption = VisibleText.sidebarObjectives(List.of("Redemption:", "- Get offered 6/13 Beacons")).getFirst();
        expect(redemption.left() == 7 && redemption.target() == State.Target.BEACON_OFFERS,
                "Redemption objective is the number of offers");
        expect(VisibleText.beaconRerollChat("Click here to reroll\n(1 reroll left)", false).count() == 1,
                "Read chat reroll count displayed under the clickable prompt");
        expect(VisibleText.beaconRerollChat("Click here to reroll", false).prompt()
                && VisibleText.beaconRerollChat("(1 reroll left)", true).count() == 1
                && VisibleText.beaconRerollChat("(1 reroll left)", false).count() < 0,
                "Read separate reroll lines only in prompt context");
        String beaconRow = VisibleText.beaconChatLine("\uDB00\uDC22Purple Beacon\uDB00\uDC4BBlue Beacon");
        expect(VisibleText.beaconChatRow(beaconRow).equals(Map.of(PURPLE, false, BLUE, false)),
                "Wynncraft's custom spacing glyph separates Purple and Blue without ordinary spaces");
        expect(VisibleText.beaconChatLine("\uDB00\uDC66Choose a Beacon!").equals("choose a beacon!"),
                "Wynncraft's custom spacing glyph does not hide the selection prompt");
        expect(VisibleText.beaconChatRow("vibrant dark gray beacon orange beacon").equals(Map.of(DARK_GREY, true, ORANGE, false))
                        && VisibleText.beaconChatRow("purple beacon offers reward pulls").isEmpty(),
                "Chat tracker reads Vibrant labels and ignores non-offer sentences");
        var cards = List.of(new VisibleText.Row(11, "Redemption",
                List.of("Objective:", "Get offered 13 Beacons", "Click to choose!"), false));
        expect(VisibleText.menu("Select a Mission", cards, true, false).choices().getFirst().slot() == 11,
                "Read the visible card and ignore decorative slots");
        String packedTitle = "\uE03B\uF02C";
        var packedMission = VisibleText.menu(packedTitle, cards, true, false);
        expect(packedMission.kind() == MISSION && packedMission.choices().size() == 1
                && packedMission.choices().getFirst().slot() == 11,
                "Identify a mission from its card when the title contains only resource-pack glyphs");
        var packedTrial = VisibleText.menu(packedTitle, List.of(
                new VisibleText.Row(10, "\uE03BAll In", List.of("Click to choose!"), false),
                new VisibleText.Row(12, "Adrenaline Junkie", List.of("Click to choose!"), false),
                new VisibleText.Row(17, "\uF02C", List.of(), false)), true, false);
        expect(packedTrial.kind() == TRIAL && packedTrial.choices().size() == 2,
                "Identify a trial from known cards and ignore decorative items");
        expect(VisibleText.menu(packedTitle, cards, false, false).kind() == UNKNOWN,
                "Do not identify a glyph-titled mission selector outside a Lootrun");
        expect(VisibleText.menu("Challenge Rewards", cards, true, false).kind() == CHALLENGE_REWARD,
                "A challenge chest never becomes the end reward chest");
        var preview = VisibleText.menu("End Reward Chest", List.of(
                new VisibleText.Row(4, "Loot Preview", List.of(), false),
                new VisibleText.Row(6, "Reroll Rewards", List.of(), false)), false, true);
        expect(!preview.rewardsReadable(), "A preview header alone does not confirm absence of a target reward");
    }

    private static void orangeCoverage() {
        for (State.Goal goal : List.of(CASHOUT, SACRIFICE_STACK)) {
            Fixture fixture = new Fixture();
            fixture.goal = goal;
            fixture.c = 8;
            fixture.choices = 6;
            fixture.orange = 25;
            fixture.activeOrange = 3;
            var orange = offer(ORANGE, true);
            var blue = offer(BLUE, false);
            fixture.offers = List.of(orange, blue);
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take BLUE"),
                    goal + " skips Orange at six choices with three active stacks and 25 challenges left");
            fixture.choices = -1;
            fixture.activeOrange = 2;
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take BLUE"),
                    goal + " skips Orange with two active stacks and no confirmed choice count");
            fixture.orange = 3;
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take ORANGE"),
                    goal + " keeps Orange eligible if the nearest stack is about to expire and choices are unknown");
            fixture.orange = 25;
            fixture.choices = 5;
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take ORANGE"),
                    goal + " values restoring the sixth choice even with two active stacks");
            fixture.choices = -1;
            fixture.activeOrange = 0;
            fixture.offers = List.of(orange, blue, offer(WHITE, false), offer(PINK, false),
                    offer(YELLOW, false), offer(PURPLE, false));
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).ranked().stream()
                    .noneMatch(rating -> rating.label().startsWith("ORANGE")),
                    goal + " recognizes the cap when six different offers are visible");
            fixture.choices = 6;
            fixture.offers = List.of(orange, offer(RAINBOW, false));
            expect(ENGINE.advise(fixture.state(), State.Menu.EMPTY).action().startsWith("Take ORANGE"),
                    goal + " keeps Orange as a fallback if the other visible beacon is locked");
        }
    }

    private static State.Menu picks(State.Kind kind, String... names) {
        List<State.Choice> choices = new ArrayList<>();
        for (int i = 0; i < names.length; i++) choices.add(new State.Choice(names[i], "", i, -1));
        return new State.Menu(kind, List.copyOf(choices), Set.of(), false, false, -1, -1);
    }

    private static State.Offer offer(State.Beacon beacon, boolean vibrant) {
        return new State.Offer(beacon, "Visible Task", "SLAY", 0, vibrant, -1);
    }

    private static void expect(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
        checks++;
    }

    private static void close(double actual, double expected) {
        expect(Math.abs(actual - expected) < .000001, "Expected " + expected + " but got " + actual);
    }

    private static final class Fixture {
        State.Goal goal = MAX_PULL;
        int c = 30, remaining = 20, timer = 600, choices = 4, aqua = 0, redRerolls = 0;
        int orange, activeOrange;
        int pulls = 80, bank = 0, endRerolls = 2, sacrifices = 2;
        boolean known = true, poolExhausted, bankSafe;
        Set<String> missions = Set.of(), trials = Set.of(), removed = Set.of();
        Map<State.Beacon, Integer> used = Map.of();
        List<State.Objective> objectives = List.of();
        List<State.Offer> offers = List.of();

        State state() {
            return new State(goal, State.Camp.FRUMA_WEST, c, remaining, 0, timer, choices, orange, activeOrange, 0, 0, aqua,
                    redRerolls, pulls, bank, endRerolls, sacrifices, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    Map.of(), used, missions, trials, removed, objectives, known, known, poolExhausted, bankSafe, false,
                    Set.of(), offers);
        }
    }
}

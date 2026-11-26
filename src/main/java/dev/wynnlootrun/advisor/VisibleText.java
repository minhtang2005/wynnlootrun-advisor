package dev.wynnlootrun.advisor;

import static dev.wynnlootrun.advisor.State.Kind.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.wynnlootrun.advisor.State.Choice;
import dev.wynnlootrun.advisor.State.Kind;
import dev.wynnlootrun.advisor.State.Menu;
import dev.wynnlootrun.advisor.State.Target;

final class VisibleText {
    private static final Pattern TIME_LEFT = Pattern.compile("^[-—]\\s*time left:\\s*(\\d+):([0-5]\\d)(?: \\[[+-]\\d+[ms]\\])?$");
    private static final Pattern CHALLENGES = Pattern.compile("^[-—]\\s*challenges:\\s*(\\d+)/(\\d+)(?: \\[[+-]\\d+])?$");
    private static final String COUNT = "([0-9][0-9,]*(?:\\.[0-9]+)?)";
    private static final String TIME_UNIT = "(m(?:in(?:ute)?s?)?|s(?:ec(?:ond)?s?)?)?";
    private static final Pattern FRACTION = Pattern.compile("(?<![0-9.])" + COUNT + TIME_UNIT
            + "\\s*/\\s*" + COUNT + TIME_UNIT + "\\b");
    private static final Pattern REROLL_PROMPT = Pattern.compile("\\bclick here to reroll[!.]?(?: |$)");
    private static final Pattern REROLL_COUNT = Pattern.compile("^\\(?([0-9,]+) (?:beacon )?rerolls? (?:left|remaining)\\)?[!.]?$");
    private static final Pattern BEACON_SPACING = Pattern.compile("[\\x{CFFFF}-\\x{D0078}]");
    private static final Pattern BEACON_NAME = Pattern.compile(
            "\\G\\s*(vibrant )?(dark gr[ae]y|gr[ae]y|white|rainbow|crimson|orange|aqua|red|violet|purple|pink|blue|green|yellow) beacon");
    private static final Pattern POTENCY = Pattern.compile("(?i)([0-9,]+)%\\s*(?:active (?:boon )?)?potency");
    private static final Pattern MYTHIC = Pattern.compile("(?i)\\b(?:unidentified mythic|mythic (?:item|weapon|armou?r|bow|wand|dagger|spear|relik|boots|leggings|helmet|chestplate)|shiny mythic)\\b");
    private static final Set<String> PRIORITY_ITEMS = Set.of("warp", "divzer", "olympic", "fatal", "nirvana", "lament", "hero");

    record Row(int slot, String name, List<String> lore, boolean mythic) {}
    record RunSidebar(boolean choosing, boolean collecting, boolean ending, int completed, int remaining, int timer) {
        static final RunSidebar UNKNOWN = new RunSidebar(false, false, false, -1, -1, -1);
        boolean running() { return !ending && (choosing || collecting || completed >= 0 && timer >= 0); }
    }

    static RunSidebar lootrunSidebar(List<String> lines) {
        boolean inLootrun = false, choosing = false, collecting = false, ending = false;
        int completed = -1, remaining = -1, timer = -1;
        for (String raw : lines) {
            String line = Catalog.normalize(raw);
            if (line.equals("lootrun:") || line.equals("lootrun")) {
                inLootrun = true;
                continue;
            }
            if (!inLootrun) continue;
            if (line.equals("choose a beacon!")) choosing = true;
            if (line.equals("collect your rewards!")) collecting = true;
            if (line.equals("warping back to camp!")) ending = true;
            var time = TIME_LEFT.matcher(line);
            if (time.matches()) {
                int minutes = parseInt(time.group(1));
                if (minutes >= 0 && minutes <= 999) timer = 60 * minutes + parseInt(time.group(2));
            }
            var challenges = CHALLENGES.matcher(line);
            if (challenges.matches()) {
                int done = parseInt(challenges.group(1)), cap = parseInt(challenges.group(2));
                if (done >= 0 && cap >= done && cap <= 100) {
                    completed = done;
                    remaining = cap - done;
                }
            }
        }
        return inLootrun ? new RunSidebar(choosing, collecting, ending, completed, remaining, timer) : RunSidebar.UNKNOWN;
    }

    static String beaconChatLine(String raw) {
        return Catalog.normalize(BEACON_SPACING.matcher(raw).replaceAll(""));
    }

    static Map<State.Beacon, Boolean> beaconChatRow(String line) {
        var matcher = BEACON_NAME.matcher(line);
        var colors = new EnumMap<State.Beacon, Boolean>(State.Beacon.class);
        int end = 0;
        while (matcher.find()) {
            var color = State.Beacon.valueOf(matcher.group(2).toUpperCase(Locale.ROOT)
                    .replace("GRAY", "GREY").replace(' ', '_'));
            colors.merge(color, matcher.group(1) != null, Boolean::logicalOr);
            end = matcher.end();
        }
        return end == line.length() ? colors : Map.of();
    }

    static Menu menu(String title, List<Row> rows, boolean runActive, boolean rewardContainer) {
        String name = Catalog.normalize(title);
        if (name.equals("challenge rewards")) return runActive ? Menu.CHALLENGE_REWARDS : Menu.EMPTY;
        boolean remove = name.matches(".*\\b(?:remove|delete|discard|abandon)\\b.*\\bmissions?\\b.*")
                || name.contains("mission removal");
        Kind kind = rewardContainer || name.matches(".*lootrun rewards?.*|.*end reward chest.*") ? REWARD
                : remove ? REMOVE
                : name.matches(".*\\b(?:pick|choose|select)\\b.*\\bmissions?\\b.*|lootrun missions?") ? MISSION
                : name.matches(".*\\b(?:pick|choose|select)\\b.*\\btrials?\\b.*|lootrun trials?") ? TRIAL
                : name.matches(".*\\b(?:pick|choose|select)\\b.*\\bboons?\\b.*|lootrun boons?") ? BOON : UNKNOWN;
        Set<String> actions = new LinkedHashSet<>();
        boolean target = false, explicitContents = false;
        int pulls = -1, sacrifices = -1;
        var choices = new LinkedHashMap<String, Choice>();
        for (Row row : rows) {
            String label = Catalog.normalize(row.name());
            String notes = Catalog.normalize(String.join(" ", row.lore()));
            if (label.matches("(?:end )?reward rerolls?|reroll(?: (?:the )?(?:loot|rewards?|chest))?(?:\\s*\\([0-9]+\\))?")) {
                if (available(notes)) actions.add("reroll");
            } else if (label.matches("(?:end )?reward sacrifices?|sacrifice(?: (?:the )?(?:loot|rewards?|pulls))?(?:\\s*\\([0-9]+\\))?")) {
                if (available(notes)) actions.add("sacrifice");
                sacrifices = firstNumber(label + " " + notes, "([0-9,]+)\\s+(?:end )?(?:reward )?sacrifices?\\b");
            } else if (label.matches("claim(?: (?:the )?(?:loot|rewards?))?|close chest|take rewards")) {
                if (available(notes)) actions.add("claim");
            }
            int count = firstNumber(label + " " + notes, "([0-9,]+)\\s+(?:reward )?pulls?\\b");
            if (count >= 0) pulls = count;
            if (label.contains("mission") && (label.startsWith("remove ") || label.startsWith("delete "))) remove = true;
            if (label.equals("no rewards") || label.equals("no items remaining")) explicitContents = true;
            if (row.mythic() || MYTHIC.matcher(label + " " + notes).find() || PRIORITY_ITEMS.contains(label)) target = true;
            Catalog.Entry entry = choiceEntry(row.name());
            if (entry != null && available(notes)) {
                int amount = firstNumber(notes, POTENCY.pattern());
                choices.merge(entry.name(), new Choice(entry.name(), notes, row.slot(), amount), VisibleText::mergeCard);
            }
        }
        if (kind == REWARD) {
            // Unknown item layouts can hide target loot. Claiming a confirmed target is still useful.
            return new Menu(kind, List.of(), Set.copyOf(actions), target,
                    target || explicitContents && !actions.isEmpty(), pulls, sacrifices);
        }
        if (!runActive) return Menu.EMPTY;
        if (kind == UNKNOWN && !choices.isEmpty()) {
            Catalog.Entry first = Catalog.find(choices.firstEntry().getKey());
            if (choices.keySet().stream().allMatch(card -> Catalog.find(card).kind() == first.kind())) {
                kind = first.kind();
            }
        }
        if (remove && kind == MISSION) kind = REMOVE;
        if (kind == UNKNOWN) return Menu.EMPTY;
        Kind cardKind = kind == REMOVE ? MISSION : kind;
        var cards = choices.values().stream().filter(card -> Catalog.find(card.name()).kind() == cardKind).toList();
        return new Menu(kind, cards, Set.of(), false, false, -1, -1);
    }

    private static Choice mergeCard(Choice first, Choice next) {
        boolean firstClickable = selectPrompt(first.description()), nextClickable = selectPrompt(next.description());
        boolean preferNext = nextClickable && !firstClickable
                || nextClickable == firstClickable && next.description().length() > first.description().length();
        Choice primary = preferNext ? next : first;
        Choice secondary = preferNext ? first : next;
        int amount = primary.amount() >= 0 ? primary.amount() : secondary.amount();
        var slots = new ArrayList<>(first.cardSlots());
        slots.addAll(next.cardSlots());
        return new Choice(primary.name(), primary.description(), primary.slot(), amount, List.copyOf(slots));
    }

    private static boolean selectPrompt(String lore) {
        return lore.contains("click to choose") || lore.contains("click to select") || lore.contains("click to remove");
    }

    static Catalog.Entry choiceEntry(String name) {
        Catalog.Entry entry = Catalog.find(name);
        if (entry != null) return entry;
        String label = Catalog.normalize(name);
        for (String prefix : List.of("remove ", "choose ", "select ")) {
            if (label.startsWith(prefix)) return Catalog.find(label.substring(prefix.length()));
        }
        return null;
    }

    private static boolean available(String lore) {
        return !lore.matches(".*\\b(?:unavailable|no (?:rerolls|sacrifices) (?:left|remaining)|0 (?:reward )?(?:rerolls|sacrifices) remaining|cannot (?:reroll|sacrifice)|already completed|already selected)\\b.*");
    }

    static int firstNumber(String text, String regex) {
        var m = Pattern.compile(regex).matcher(text);
        return m.find() ? parseInt(m.group(1)) : -1;
    }
    static int parseInt(String text) {
        try {
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    static State.Objective objective(String effect, Catalog.Entry entry) {
        String line = Catalog.normalize(effect);
        Target target = target(line, entry.name());
        int remaining = firstNumber(line, "(?:remaining|left)\\s*[:=]\\s*([0-9,]+)");
        if (remaining < 0) remaining = firstNumber(line, "([0-9,]+)\\s+[^0-9]{0,22}\\b(?:left|remaining)\\b");
        var fraction = FRACTION.matcher(line);
        if (remaining < 0 && fraction.find()) {
            String currentUnit = fraction.group(2), totalUnit = fraction.group(4);
            if (target != Target.TIME || currentUnit != null || totalUnit != null) {
                double current = Double.parseDouble(fraction.group(1).replace(",", ""));
                double cap = Double.parseDouble(fraction.group(3).replace(",", ""));
                if (target == Target.TIME) {
                    current *= seconds(currentUnit == null ? totalUnit : currentUnit);
                    cap *= seconds(totalUnit == null ? currentUnit : totalUnit);
                }
                if (Double.isFinite(current) && cap >= current && cap - current <= Integer.MAX_VALUE) {
                    remaining = (int) Math.ceil(cap - current);
                }
            }
        }
        return new State.Objective(entry.name(), entry.kind(), target, remaining);
    }

    private static int seconds(String unit) {
        return unit != null && unit.startsWith("m") ? 60 : 1;
    }

    static List<State.Objective> sidebarObjectives(List<String> lines) {
        List<State.Objective> objectives = new ArrayList<>();
        Catalog.Entry heading = null;
        for (String raw : lines) {
            String line = Catalog.normalize(raw);
            if (line.endsWith(":")) {
                heading = Catalog.find(line.substring(0, line.length() - 1));
                continue;
            }
            if (heading != null && (heading.kind() == MISSION || heading.kind() == TRIAL)
                    && (line.startsWith("- ") || line.startsWith("— "))) {
                State.Objective objective = objective(line, heading);
                if (objective.left() >= 0) {
                    objectives.add(objective);
                    heading = null;
                }
            } else if (!line.isBlank()) {
                heading = null;
            }
        }
        return List.copyOf(objectives);
    }

    record RerollChat(boolean prompt, int count) {}

    static RerollChat beaconRerollChat(String line, boolean awaitingCount) {
        String text = Catalog.normalize(line);
        var prompt = REROLL_PROMPT.matcher(text);
        boolean shown = prompt.find();
        if (!shown && !awaitingCount) return new RerollChat(false, -1);
        String detail = shown ? text.substring(prompt.end()).strip() : text;
        var count = REROLL_COUNT.matcher(detail);
        return new RerollChat(shown, count.matches() ? parseInt(count.group(1)) : -1);
    }

    private static Target target(String line, String name) {
        return switch (name) {
            case "Dying Light" -> Target.POTENCY;
            case "Treasury Bill", "Adrenaline Junkie", "Imperitia", "Warmth Devourer", "Monochromokopia" -> Target.PULLS;
            case "All In", "Chronotrigger", "Hubris", "Ultimate Sacrifice" -> Target.CHALLENGES;
            case "Side Hustle" -> Target.CHESTS;
            case "Lights Out" -> Target.RADIANT_MOBS;
            case "Sprezzatura" -> Target.CURSES;
            default -> {
                if (line.contains("radiant") && line.contains("mob")) yield Target.RADIANT_MOBS;
                if (line.contains("chest")) yield Target.CHESTS;
                if (line.contains("blue") && line.contains("beacon")) yield Target.BLUE;
                if (line.contains("offered") && line.contains("beacon")) yield Target.BEACON_OFFERS;
                if (line.contains("curse")) yield Target.CURSES;
                if (line.contains("pull")) yield Target.PULLS;
                if (line.contains("time") || line.contains("seconds") || line.contains("minutes")) yield Target.TIME;
                if (line.contains("mob")) yield Target.MOBS;
                if (line.contains("challenge")) yield Target.CHALLENGES;
                yield Target.UNKNOWN;
            }
        };
    }
}

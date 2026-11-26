package dev.wynnlootrun.advisor;

import static dev.wynnlootrun.advisor.State.Beacon.*;
import static dev.wynnlootrun.advisor.State.Kind.*;

import com.wynntils.core.components.Models;
import com.wynntils.handlers.chat.event.ChatMessageEvent;
import com.wynntils.handlers.chat.type.RecipientType;
import com.wynntils.models.containers.containers.LootrunRewardChestContainer;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.lootrun.beacons.LootrunBeaconKind;
import com.wynntils.models.lootrun.event.LootrunFinishedEvent;
import com.wynntils.models.lootrun.type.LootrunningState;
import com.wynntils.utils.colors.CustomColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;

import dev.wynnlootrun.advisor.State.Beacon;
import dev.wynnlootrun.advisor.State.Camp;
import dev.wynnlootrun.advisor.State.Goal;
import dev.wynnlootrun.advisor.State.Kind;
import dev.wynnlootrun.advisor.State.Menu;
import dev.wynnlootrun.advisor.State.Objective;
import dev.wynnlootrun.advisor.State.Offer;

public final class LiveReader {
    private static final Pattern OBJECTIVE = Pattern.compile("^(?:[-—] )?(?:(active|completed|removed|failed) )?(mission|trial)(?: (started|completed|removed|failed))?\\s*[:—-]\\s*(.+)$");
    private static final Pattern EXPLICIT_OFFER = Pattern.compile(
            "^(?:[-—] )?(vibrant )?(white|rainbow|crimson|gr[ae]y|dark gr[ae]y|orange|aqua|red|violet|purple|pink|blue|green|yellow) beacon(?: \\(tier ([0-3])\\))?(?: [—-] (.+))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKER_LABEL = Pattern.compile(
            "^\\uDAFF\\uDFF8[\\uE010-\\uE014]\\uDAFF\\uDFDE\\uE000\\uDAFF\\uDFE6([\\uE00B-\\uE00F\\uE020-\\uE024])\\n(\\d+)m(?: .*)?$");
    private static final FontDescription MARKER_FONT = new FontDescription.Resource(Identifier.withDefaultNamespace("marker"));
    private static final Pattern READY = Pattern.compile("^(?:lootrun started!?|you started (?:a|the) lootrun[!.]?)$");

    private final Set<String> missions = new HashSet<>();
    private final Set<String> trials = new HashSet<>();
    private final Set<String> removed = new HashSet<>();
    private final Map<Kind, Objective> objectives = new EnumMap<>(Kind.class);
    private final Map<String, Integer> boons = new HashMap<>();
    private final Map<String, Integer> metrics = new HashMap<>();
    private final Map<String, Integer> visibleMetrics = new HashMap<>();
    private final Set<String> seenOffers = new HashSet<>();
    private final Set<Beacon> interludeColors = new HashSet<>();
    private final Map<Beacon, Boolean> chatBeaconOffers = new EnumMap<>(Beacon.class);
    private int beaconChatWindow;
    private Set<Beacon> priorInterlude = Set.of();
    private List<Offer> previousOffers = List.of();
    private LootrunningState lastState = LootrunningState.NOT_RUNNING;
    private Camp inferredCamp = Camp.UNKNOWN;
    private Kind pendingKind = UNKNOWN;
    private String pendingStatus = "";
    private int pendingAge;
    private int rerollChatCount = -1;
    private int rerollPromptAge;
    private int rewardPulls = -1, rewardSacrifices = -1;
    private boolean rewardSummary;
    private boolean enabled = true;
    private int aqua = -1, failures = -1, bluePurpleOffers;
    private boolean bankSafe, exhausted, knownMission, knownTrial, historyKnown, runInitialized;
    private long requiemCompletedAt;

    void reset() {
        resetRun(false);
        lastState = LootrunningState.NOT_RUNNING;
        rewardPulls = -1;
        rewardSacrifices = -1;
        rewardSummary = false;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void resetRun(boolean fresh) {
        missions.clear();
        trials.clear();
        removed.clear();
        objectives.clear();
        boons.clear();
        metrics.clear();
        visibleMetrics.clear();
        seenOffers.clear();
        interludeColors.clear();
        chatBeaconOffers.clear();
        beaconChatWindow = 0;
        priorInterlude = Set.of();
        previousOffers = List.of();
        inferredCamp = Camp.UNKNOWN;
        pendingKind = UNKNOWN;
        pendingStatus = "";
        pendingAge = 0;
        rerollChatCount = -1;
        rerollPromptAge = 0;
        aqua = fresh ? 0 : -1;
        failures = fresh ? 0 : -1;
        bluePurpleOffers = 0;
        bankSafe = false;
        exhausted = false;
        knownMission = fresh;
        knownTrial = fresh;
        historyKnown = fresh;
        runInitialized = fresh;
        requiemCompletedAt = 0;
        if (fresh) {
            rewardPulls = -1;
            rewardSacrifices = -1;
            rewardSummary = false;
        }
    }

    @SubscribeEvent
    public void onLootrunFinished(LootrunFinishedEvent.Completed event) {
        Minecraft.getInstance().execute(() -> {
            if (!enabled) return;
            rewardSummary = true;
            rewardPulls = event.getRewardPulls();
            rewardSacrifices = event.getRewardSacrifices();
        });
    }

    void gameMessage(Component message) {
        String line = Catalog.normalize(message.getString());
        if (READY.matcher(line).matches()) {
            resetRun(true);
            return;
        }
        if (line.matches("lootrun completed[!.]?")) {
            rewardSummary = true;
            rewardPulls = -1;
            rewardSacrifices = -1;
        }
        if (rewardSummary) {
            int pulls = VisibleText.firstNumber(line, "^([0-9,]+) reward pulls?$");
            int sacrifices = VisibleText.firstNumber(line, "^([0-9,]+) reward sacrifices?$");
            if (pulls >= 0) rewardPulls = pulls;
            if (sacrifices >= 0) rewardSacrifices = sacrifices;
        }
        if (!runInitialized && !Models.Lootrun.getState().isRunning()) return;
        if (line.matches("choose a beacon[!.]?(?: .*)?|beacons? rerolled[!.]?|challenge (?:completed|failed)[!.]?(?: .*)?")) {
            seenOffers.clear();
            rerollChatCount = -1;
            rerollPromptAge = 0;
        }
        var reroll = VisibleText.beaconRerollChat(line, rerollPromptAge > 0);
        if (reroll.count() >= 0) {
            rerollChatCount = reroll.count();
            rerollPromptAge = 0;
        } else if (reroll.prompt()) {
            rerollChatCount = -1;
            rerollPromptAge = 3;
        } else if (rerollPromptAge > 0) {
            rerollPromptAge--;
        }
        if (line.matches("challenge failed[!.]?")) {
            if (failures >= 0) failures++;
            aqua = 0;
        }
        readLine(line, true);
    }

    @SubscribeEvent
    public void onWynntilsChat(ChatMessageEvent.Match event) {
        if (event.getRecipientType() == RecipientType.INFO) {
            String message = event.getMessage().getStringWithoutFormatting();
            Minecraft.getInstance().execute(() -> {
                if (enabled) readBeaconMessage(message);
            });
        }
    }

    void readBeaconMessage(String message) {
        for (String raw : message.split("\\R")) {
            String line = VisibleText.beaconChatLine(raw);
            if (line.equals("choose a beacon!") || line.equals("choose a beacon")) {
                chatBeaconOffers.clear();
                beaconChatWindow = 12;
            } else if (line.matches("beacons? rerolled[!.]?|challenge (?:completed|failed)[!.]?(?: .*)?")) {
                chatBeaconOffers.clear();
                beaconChatWindow = 0;
            } else if (beaconChatWindow > 0) {
                beaconChatWindow--;
                VisibleText.beaconChatRow(line).forEach((color, vibrant) ->
                        chatBeaconOffers.merge(color, vibrant, Boolean::logicalOr));
            }
        }
    }

    private void readLine(String input, boolean fromChat) {
        String line = input.replaceFirst("^[-—]\\s*", "");
        if (line.equals("no active mission") || line.equals("active mission: none")) {
            knownMission = true;
            objectives.remove(MISSION);
            return;
        }
        if (line.equals("no active trial") || line.equals("active trial: none")) {
            knownTrial = true;
            objectives.remove(TRIAL);
            return;
        }
        if (line.matches("(?:mission|trial) (?:started|completed|removed|failed)[!.]?")) {
            pendingKind = line.startsWith("mission") ? MISSION : TRIAL;
            pendingStatus = line.substring(line.indexOf(' ') + 1).replaceAll("[!.]+$", "");
            pendingAge = 3;
            if (pendingStatus.equals("started")) {
                if (pendingKind == MISSION) knownMission = false;
                else knownTrial = false;
            }
            return;
        }
        Matcher objective = OBJECTIVE.matcher(line);
        if (objective.matches()) {
            Kind kind = objective.group(2).equals("mission") ? MISSION : TRIAL;
            String status = objective.group(1) != null ? objective.group(1)
                    : objective.group(3) != null ? objective.group(3) : "active";
            Catalog.Entry entry = namedPrefix(objective.group(4), kind);
            if (entry != null) updateObjective(entry, status, objective.group(4));
            return;
        }
        if (fromChat && pendingKind != UNKNOWN && pendingAge-- > 0) {
            Catalog.Entry entry = namedPrefix(line, pendingKind);
            if (entry != null) {
                updateObjective(entry, pendingStatus, line);
                pendingKind = UNKNOWN;
                return;
            }
        }
        if (!fromChat) {
            for (Objective current : List.copyOf(objectives.values())) {
                if (line.startsWith(Catalog.normalize(current.name()))) {
                    Catalog.Entry entry = Catalog.find(current.name());
                    Objective parsed = VisibleText.objective(line, entry);
                    if (parsed.left() >= 0) objectives.put(current.kind(), parsed);
                }
            }
        }
        if (line.equals("no missions or trials remain in the pool")) exhausted = true;
        if (line.equals("no banked pulls at this camp")) bankSafe = true;
        Set<String> keys = Set.of("reward pulls", "current pulls", "banked pulls", "beacon rerolls", "beacon choices",
                "radiant power", "radiant chance curses", "health curses", "damage resistance curses",
                "total curses", "curses", "active boon potency", "aqua stacks", "failed challenges",
                "training arc pulls", "interest scheme chests", "equilibrium potency", "choices");
        for (String key : keys) {
            if (!line.startsWith(key + ":")) continue;
            Matcher count = Pattern.compile("^([0-9,]+)").matcher(line.substring(key.length() + 1).stripLeading());
            if (count.find()) {
                int value = VisibleText.parseInt(count.group(1));
                if (value >= 0) (fromChat ? metrics : visibleMetrics).put(key, value);
            }
            break;
        }
        if (line.startsWith("boon gained: ") || line.startsWith("boon acquired: ")) {
            Catalog.Entry boon = namedPrefix(line.substring(line.indexOf(':') + 2), BOON);
            if (boon != null && fromChat) boons.merge(boon.name(), 1, Integer::sum);
        }
    }

    private void updateObjective(Catalog.Entry entry, String status, String description) {
        Kind kind = entry.kind();
        if (kind == MISSION) knownMission = true;
        if (kind == TRIAL) knownTrial = true;
        Set<String> completed = kind == MISSION ? missions : trials;
        switch (status) {
            case "completed" -> {
                boolean firstCompletion = completed.add(entry.name());
                if (firstCompletion && entry.name().equals("Requiem")) requiemCompletedAt = System.nanoTime();
                objectives.computeIfPresent(kind, (key, value) -> value.name().equals(entry.name()) ? null : value);
            }
            case "removed" -> {
                completed.remove(entry.name());
                removed.add(entry.name());
                objectives.computeIfPresent(kind, (key, value) -> value.name().equals(entry.name()) ? null : value);
            }
            case "failed" -> objectives.computeIfPresent(kind,
                    (key, value) -> value.name().equals(entry.name()) ? null : value);
            default -> {
                if (!completed.contains(entry.name())) {
                    recordActiveObjective(VisibleText.objective(description, entry));
                }
            }
        }
    }

    private void recordActiveObjective(Objective next) {
        Kind kind = next.kind();
        if (kind == MISSION) knownMission = true;
        if (kind == TRIAL) knownTrial = true;
        if ((kind == MISSION ? missions : trials).contains(next.name())) return;
        Objective previous = objectives.get(kind);
        if (previous == null || !previous.name().equals(next.name()) || next.left() >= 0) objectives.put(kind, next);
    }

    private static Catalog.Entry namedPrefix(String line, Kind kind) {
        return Catalog.ENTRIES.stream().filter(entry -> entry.kind() == kind).filter(entry -> {
            String name = Catalog.normalize(entry.name());
            return line.equals(name) || line.startsWith(name + " ") || line.startsWith(name + ":");
        }).findFirst().orElse(null);
    }

    record Snapshot(State state, boolean running, boolean choosingBeacon, boolean collectingRewards) {}

    Snapshot snapshot(Minecraft client, Goal goal, Camp configuredCamp) {
        var model = Models.Lootrun;
        var beaconModel = Models.LootrunBeacon;
        LootrunningState modelPhase = model.getState();
        List<String> sidebar = scoreboard(client);
        var shown = VisibleText.lootrunSidebar(sidebar);
        LootrunningState phase = shown.ending() ? LootrunningState.NOT_RUNNING
                : shown.choosing() ? LootrunningState.CHOOSING_BEACON
                : shown.running() ? LootrunningState.IN_TASK : modelPhase;
        var challenges = model.getChallenges();
        int completed = shown.completed() >= 0 ? shown.completed() : challenges.max() > 0 ? challenges.current() : -1;
        int remaining = shown.remaining() >= 0 ? shown.remaining() : challenges.max() > 0 ? challenges.getRemaining() : -1;
        if (phase.isRunning()) runInitialized = true;
        if (!phase.isRunning() && lastState.isRunning()) resetRun(false);
        visibleMetrics.clear();
        if (phase.isRunning()) {
            for (String line : sidebar) readLine(Catalog.normalize(line), false);
            readVisibleHotbar(client);
            var location = model.getLootrunLocation();
            Camp reported = location == null ? Camp.UNKNOWN : modelCamp(location.name());
            if (reported != Camp.UNKNOWN) inferredCamp = reported;
            else if (completed <= 1 && inferredCamp == Camp.UNKNOWN) inferredCamp = nearbyCamp(client);
            for (int i = 0; i < 12; i++) {
                Catalog.Entry mission = Catalog.find(Models.Mission.getMissionStatus(i, false));
                if (mission == null || mission.kind() != MISSION || removed.contains(mission.name())) continue;
                // Wynntils adds these from completion or a message emitted by an enabled passive.
                missions.add(mission.name());
                objectives.computeIfPresent(MISSION, (key, value) -> value.name().equals(mission.name()) ? null : value);
            }
            List<Objective> sidebarObjectives = VisibleText.sidebarObjectives(sidebar);
            sidebarObjectives.forEach(this::recordActiveObjective);
            if (sidebarObjectives.stream().noneMatch(objective -> objective.kind() == MISSION)) {
                readModelObjective(Models.Mission.getCurrentMission(false), MISSION,
                        Models.Mission::getCurrentMissionObjective, Models.Mission::getCurrentMissionProgress);
            }
            if (sidebarObjectives.stream().noneMatch(objective -> objective.kind() == TRIAL)) {
                readModelObjective(Models.Trial.getCurrentTrial(), TRIAL,
                        Models.Trial::getCurrentTrialObjective, Models.Trial::getCurrentTrialProgress);
            }
        }
        Map<Beacon, Integer> used = new EnumMap<>(Beacon.class);
        List<Offer> offers = new ArrayList<>();
        int visibleChoices = visibleMetrics.getOrDefault("beacon choices", visibleMetrics.getOrDefault("choices", -1));
        if (phase.isRunning()) {
            for (LootrunBeaconKind color : LootrunBeaconKind.values()) {
                Beacon beacon = color(color.name());
                if (beacon != null) {
                    int count = beaconModel.getBeaconCount(color);
                    if (count > 0 || historyKnown) used.put(beacon, count);
                }
            }
        }
        if (phase == LootrunningState.CHOOSING_BEACON) {
            beaconModel.getBeacons().forEach((color, prediction) -> {
                Beacon beacon = color(color.name());
                if (beacon == null || prediction == null || prediction.beacon() == null) return;
                var location = prediction.taskLocation();
                offers.add(new Offer(beacon, location == null ? "" : location.name(),
                        prediction.lootrunMarker() == null ? "" : prediction.lootrunMarker().getTaskType().name(),
                        prediction.distance(), beaconModel.isBeaconVibrant(color), -1));
            });
            markerOffers(client).forEach(marker -> {
                if (offers.stream().noneMatch(offer -> offer.beacon() == marker.beacon())) offers.add(marker);
            });
            for (String line : sidebar) {
                Matcher match = EXPLICIT_OFFER.matcher(Catalog.clean(line));
                if (!match.matches()) continue;
                Beacon color = color(match.group(2));
                int tier = match.group(3) == null ? -1 : Integer.parseInt(match.group(3));
                if (offers.stream().noneMatch(o -> o.beacon() == color)) {
                    offers.add(new Offer(color, match.group(4) == null ? "" : match.group(4), "", -1,
                            match.group(1) != null, tier));
                } else if (tier >= 0) {
                    offers.replaceAll(o -> o.beacon() == color ? new Offer(color, o.task(), o.type(),
                            o.distance(), o.vibrant(), tier) : o);
                }
            }
            chatBeaconOffers.forEach((beacon, vibrant) -> {
                if (offers.stream().noneMatch(offer -> offer.beacon() == beacon)) {
                    offers.add(new Offer(beacon, "", "", -1, vibrant, -1));
                } else if (vibrant) {
                    offers.replaceAll(offer -> offer.beacon() == beacon && !offer.vibrant()
                            ? new Offer(beacon, offer.task(), offer.type(), offer.distance(), true, offer.shownTier())
                            : offer);
                }
            });
            for (Offer offer : offers) {
                interludeColors.add(offer.beacon());
                if ((offer.beacon() == BLUE || offer.beacon() == PURPLE) && seenOffers.add(offer.beacon().name())) {
                    bluePurpleOffers++;
                }
            }
        }
        if (lastState == LootrunningState.CHOOSING_BEACON && phase == LootrunningState.IN_TASK) {
            var last = beaconModel.getLastTaskBeaconColor();
            if (last == null) aqua = -1;
            else if (last == LootrunBeaconKind.AQUA && aqua >= 0) {
                var seen = previousOffers.stream().filter(o -> o.beacon() == AQUA).findFirst().orElse(null);
                if (seen == null) aqua = -1;
                else {
                    int bonus = seen.shownTier() >= 0 ? seen.shownTier()
                            : seen.vibrant() || beaconModel.wasLastBeaconVibrant() ? 1 : 0;
                    aqua = Math.min(3, aqua + Math.min(3, bonus + 1));
                }
            } else if (last != LootrunBeaconKind.AQUA) aqua = 0;
            seenOffers.clear();
            rerollChatCount = -1;
            rerollPromptAge = 0;
            priorInterlude = Set.copyOf(interludeColors);
            interludeColors.clear();
            chatBeaconOffers.clear();
            beaconChatWindow = 0;
        }
        if (phase == LootrunningState.CHOOSING_BEACON) previousOffers = List.copyOf(offers);
        lastState = phase;
        Camp camp = configuredCamp == Camp.UNKNOWN ? inferredCamp : configuredCamp;
        int banked = metric("banked pulls");
        int timer = shown.timer() >= 0 ? shown.timer()
                : modelPhase.isRunning() && challenges.max() > 0 ? model.getCurrentTime() : -1;
        boolean provenExhaustion = exhausted || historyKnown && knownMission && knownTrial && completed > 70
                && missions.size() >= 4 && used.getOrDefault(DARK_GREY, -1) >= (trials.contains(Engine.MONO) ? 3 : 2);
        int shownFailures = metric("failed challenges");
        int shownAqua = metric("aqua stacks");
        int pulls = metric("current pulls");
        int curses = metric("total curses");
        State state = new State(goal, camp, completed, remaining, shownFailures >= 0 ? shownFailures : failures, timer,
                visibleChoices, beaconModel.getChallengesTillNextOrangeExpires(), beaconModel.getActiveOrangeBeacons(),
                beaconModel.getActiveRainbowBeacons(),
                beaconModel.getRedBeaconTaskCount(), shownAqua >= 0 ? shownAqua : aqua,
                visibleMetrics.getOrDefault("beacon rerolls", phase == LootrunningState.CHOOSING_BEACON ? rerollChatCount : -1),
                pulls >= 0 ? pulls : metric("reward pulls"), banked,
                modelPhase.isRunning() ? model.getRerolls() : -1, modelPhase.isRunning() ? model.getSacrifices() : -1,
                curses >= 0 ? curses : metric("curses"), metric("radiant power"),
                metric("health curses"), metric("damage resistance curses"), metric("radiant chance curses"),
                metric("active boon potency"), metric("training arc pulls"), metric("interest scheme chests"),
                metric("equilibrium potency"), bluePurpleOffers,
                Map.copyOf(boons), Map.copyOf(used), Set.copyOf(missions), Set.copyOf(trials), Set.copyOf(removed),
                List.copyOf(objectives.values()), knownMission && knownTrial, historyKnown, provenExhaustion,
                bankSafe || banked == 0 || completed >= 0 && completed < 4,
                requiemCompletedAt != 0 && System.nanoTime() - requiemCompletedAt < TimeUnit.MINUTES.toNanos(15),
                priorInterlude, List.copyOf(offers));
        return new Snapshot(state, phase.isRunning(), phase == LootrunningState.CHOOSING_BEACON, shown.collecting());
    }

    private void readModelObjective(String name, Kind kind, java.util.function.IntFunction<String> description,
                                    java.util.function.IntFunction<com.wynntils.utils.type.CappedValue> progress) {
        if (name == null) return;
        Catalog.Entry entry = Catalog.find(name);
        if (entry == null || entry.kind() != kind || (kind == MISSION ? missions : trials).contains(entry.name())) return;
        for (int i = 0; i < 4; i++) {
            String text = description.apply(i);
            var count = progress.apply(i);
            if (text.isBlank() && count.max() <= 0) continue;
            updateObjective(entry, "active", count.max() > 0 ? text + " " + count.current() + "/" + count.max() : text);
            return;
        }
        updateObjective(entry, "active", "");
    }

    private static List<Offer> markerOffers(Minecraft client) {
        if (client.level == null || client.player == null) return List.of();
        var camera = client.getCameraEntity();
        if (camera == null) return List.of();
        List<Offer> offers = new ArrayList<>();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Display.TextDisplay display) || display.isInvisibleTo(client.player)
                    || !display.shouldRender(camera.getX(), camera.getEyeY(), camera.getZ())) continue;
            Component label = display.getText();
            var parts = MARKER_LABEL.matcher(label.getString());
            if (!parts.matches()) continue;
            var tint = label.visit((style, glyphs) -> glyphs.indexOf('\uE000') >= 0 && MARKER_FONT.equals(style.getFont())
                    ? java.util.Optional.ofNullable(style.getColor()) : java.util.Optional.empty(), Style.EMPTY);
            LootrunBeaconKind mapped = tint.map(color -> LootrunBeaconKind.fromColor(CustomColor.fromInt(color.getValue())))
                    .orElse(null);
            if (mapped == null) continue;
            int meters = VisibleText.parseInt(parts.group(2));
            String task = switch (parts.group(1).charAt(0)) {
                case '\uE00B', '\uE020' -> "SLAY";
                case '\uE00C', '\uE021' -> "TARGET";
                case '\uE00D', '\uE022' -> "DEFEND";
                case '\uE00E', '\uE023' -> "LOOT";
                default -> "DESTROY";
            };
            if (meters >= 0) offers.add(new Offer(color(mapped.name()), "", task, meters,
                    Models.LootrunBeacon.isBeaconVibrant(mapped), -1));
        }
        offers.sort(java.util.Comparator.comparingInt(Offer::distance).thenComparing(Offer::type));
        return offers;
    }

    private static Camp modelCamp(String name) {
        return switch (name) {
            case "SILENT_EXPANSE" -> Camp.SILENT_EXPANSE;
            case "CORKUS" -> Camp.CORKUS;
            case "MOLTEN_HEIGHTS_HIKE" -> Camp.MOLTEN_HEIGHTS;
            case "SKY_ISLANDS_EXPLORATION" -> Camp.SKY_ISLANDS;
            case "CANYON_OF_THE_LOST_EXCURSION" -> Camp.CANYON_SOUTH;
            case "CANYON_OF_THE_LOST_EXCURSION_NORTH" -> Camp.CANYON_NORTH;
            case "THE_FRUMA_FORAY_WEST" -> Camp.FRUMA_WEST;
            case "THE_FRUMA_FORAY_EAST" -> Camp.FRUMA_EAST;
            default -> Camp.UNKNOWN;
        };
    }

    private int metric(String name) { return visibleMetrics.getOrDefault(name, metrics.getOrDefault(name, -1)); }

    private void readVisibleHotbar(Minecraft client) {
        if (client.player == null || visibleMetrics.containsKey("beacon rerolls")) return;
        var inventory = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var stack = inventory.getItem(i);
            String name = Catalog.normalize(stack.getHoverName().getString());
            if (!name.contains("beacon reroll")) continue;
            var notes = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
            for (Component note : notes) {
                String value = Catalog.normalize(note.getString());
                int count = VisibleText.firstNumber(value, "([0-9,]+) (?:beacon )?rerolls? (?:remaining|left)");
                if (count >= 0) visibleMetrics.put("beacon rerolls", count);
            }
        }
    }

    private static Beacon color(String input) {
        try {
            return Beacon.valueOf(input.toUpperCase(java.util.Locale.ROOT).replace("GRAY", "GREY").replace(' ', '_'));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Camp nearbyCamp(Minecraft client) {
        if (client.player == null) return Camp.UNKNOWN;
        for (Camp camp : Camp.values()) {
            if (camp == Camp.UNKNOWN || camp == Camp.CANYON_NORTH) continue;
            double x = client.player.getX() - camp.x, z = client.player.getZ() - camp.z;
            if (x * x + z * z <= 120 * 120) return camp;
        }
        return Camp.UNKNOWN;
    }

    private static List<String> scoreboard(Minecraft client) {
        if (client.level == null) return List.of();
        var board = client.level.getScoreboard();
        var sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return List.of();
        List<String> lines = new ArrayList<>(16);
        lines.add(sidebar.getDisplayName().getString());
        board.listPlayerScores(sidebar).stream().filter(entry -> !entry.isHidden())
                .sorted(java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value)
                        .reversed().thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner)).limit(15)
                .map(entry -> PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName()).getString())
                .forEach(lines::add);
        return lines;
    }

    Menu menu(Minecraft client, boolean running) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return Menu.EMPTY;
        var handler = screen.getMenu();
        boolean reward = Models.Container.getCurrentContainer() instanceof LootrunRewardChestContainer
                || screen.getTitle().getString().equals("\uDAFF\uDFF2\uE00A");
        List<VisibleText.Row> rows = new ArrayList<>();
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            if (client.player == null || slot.container == client.player.getInventory() || !slot.isActive()) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            List<String> lore = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()
                    .stream().map(Component::getString).toList();
            boolean mythic = reward && Models.Item.asWynnItem(item, GearItem.class)
                    .map(gear -> gear.getGearTier() == GearTier.MYTHIC).orElse(false);
            rows.add(new VisibleText.Row(i, item.getHoverName().getString(), lore, mythic));
        }
        String title = screen.getTitle().getString();
        if (!reward && Models.LootrunBeacon.getLastTaskBeaconColor() == LootrunBeaconKind.DARK_GRAY
                && running
                && Catalog.normalize(title).isBlank()
                && rows.stream().map(VisibleText.Row::name).map(Catalog::find)
                        .anyMatch(entry -> entry != null && entry.kind() == MISSION)) {
            title = "Remove Mission";
        }
        Menu menu = VisibleText.menu(title, rows, running, reward);
        if (menu.kind() == REWARD && rewardSummary) {
            return new Menu(menu.kind(), menu.choices(), menu.actions(), menu.targetLoot(), menu.rewardsReadable(),
                    menu.pulls() >= 0 ? menu.pulls() : rewardPulls,
                    menu.sacrifices() >= 0 ? menu.sacrifices() : rewardSacrifices);
        }
        return menu;
    }
}

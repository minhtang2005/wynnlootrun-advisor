package dev.wynnlootrun.advisor;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Managers;
import com.wynntils.core.events.MixinHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.screens.overlays.placement.OverlayManagementScreen;
import dev.wynnlootrun.advisor.AdvisorHudFeature.AdvisorOverlay;
import dev.wynnlootrun.advisor.mixin.ContainerScreenAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public final class AdvisorClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("WynnLootrun Advisor");
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("wynnlootrun-advisor.properties");
    private static final int FOREGROUND = 0xFFF4F8FF, MUTED = 0xFFADBBD0, ACCENT = 0xFF74E2BB, WARNING = 0xFFFFCC88;
    private static final int BACKGROUND = 0xED121B2A;
    private static AdvisorClient instance;

    private final LiveReader reader = new LiveReader();
    private final double[] multipliers = {1, 1, 1, 1, 1, 1, 1, 1};
    private State.Goal goal = State.Goal.CASHOUT;
    private State.Camp camp = State.Camp.UNKNOWN;
    private boolean enabled = true, faultLogged;
    private int offsetX = 10, offsetY = 52, tick;
    private float scale = 1;
    private Engine engine;
    private State state;
    private State.Menu menu = State.Menu.EMPTY;
    private Engine.Advice advice;
    private State.InputStatus inputStatus;
    private boolean choosingBeacon;
    private Object screenIdentity;
    private HudLayout layout;
    private KeyMapping openAdvisor;

    private record HudLayout(Engine.Advice advice, int width, Font font, List<FormattedCharSequence> action,
                             List<FormattedCharSequence> reason, List<Engine.Rating> ratings) {}

    @Override
    public void onInitializeClient() {
        load();
        instance = this;
        reader.setEnabled(enabled);
        engine = new Engine(multipliers);
        openAdvisor = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.wynnlootrun_advisor.open",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("wynnlootrun_advisor", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                WynntilsMod.registerEventListener(reader);
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("Wynntils events are unavailable. Using Minecraft messages.", error);
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!enabled) return;
            try {
                reader.gameMessage(message);
                if (!overlay) reader.readBeaconMessage(message.getString());
            } catch (RuntimeException | LinkageError error) {
                integrationError(error);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("wynnlootrun_advisor", "hud"), (graphics, tickCounter) -> {
            if (useWynntilsRendering()) return;
            var overlay = AdvisorHudFeature.overlay();
            if (overlay != null && !Managers.Overlay.isEnabled(overlay)) return;
            if (showWorldHud()) draw(graphics, false, overlay, false);
            if (overlay != null && Minecraft.getInstance().screen instanceof OverlayManagementScreen screen
                    && screen.showPreview() && (screen.shouldRenderAllOverlays() || screen.getSelectedOverlay() == overlay)) {
                draw(graphics, false, overlay, true);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> container) {
                ScreenEvents.afterRender(screen).register((rendered, graphics, mouseX, mouseY, delta) -> {
                    if (client.screen == screen && screen == screenIdentity && menu.kind() != State.Kind.UNKNOWN) {
                        draw(graphics, true, null, false);
                        drawChoiceOutline(client, container, graphics);
                    }
                });
            }
        });
        registerCommands();
    }

    static AdvisorClient instance() { return instance; }
    static int defaultHudX() { return instance == null ? 10 : instance.offsetX; }
    static int defaultHudY() { return instance == null ? 52 : instance.offsetY; }
    static float defaultHudScale() { return instance == null ? 1 : instance.scale; }

    private static boolean useWynntilsRendering() {
        return MixinHelper.onWynncraft() && MixinHelper.isCompatibleVersion();
    }

    boolean showWorldHud() {
        return enabled && Minecraft.getInstance().screen == null && menu.kind() == State.Kind.UNKNOWN;
    }

    private void tick(Minecraft client) {
        boolean openRequested = false;
        while (openAdvisor.consumeClick()) openRequested = true;
        if (openRequested && client.player != null && client.level != null && client.screen == null) {
            client.setScreen(new AdvisorScreen(openAdvisor, goal, this::selectGoal));
        }
        if (!enabled || client.player == null || client.level == null) return;
        if (client.screen == screenIdentity && ++tick % 4 != 0) return;
        screenIdentity = client.screen;
        try {
            var snapshot = reader.snapshot(client, goal, camp);
            State nextState = snapshot.state();
            State.Menu nextMenu = reader.menu(client, snapshot.running());
            if (snapshot.running() && snapshot.collectingRewards() && nextMenu.kind() == State.Kind.UNKNOWN
                    && client.screen == null) nextMenu = State.Menu.CHALLENGE_REWARDS;
            if (!snapshot.running() && nextMenu.kind() == State.Kind.UNKNOWN) {
                state = null;
                menu = State.Menu.EMPTY;
                advice = null;
                inputStatus = null;
                layout = null;
                return;
            }
            if (advice != null && nextState.equals(state) && nextMenu.equals(menu)
                    && snapshot.choosingBeacon() == choosingBeacon) return;
            state = nextState;
            menu = nextMenu;
            choosingBeacon = snapshot.choosingBeacon();
            inputStatus = state.inputStatus(menu, choosingBeacon);
            advice = engine.advise(state, menu);
        } catch (RuntimeException | LinkageError error) {
            integrationError(error);
        }
    }

    private void integrationError(Throwable error) {
        if (!faultLogged) {
            LOGGER.warn("Lootrun input could not be read. Advice is suspended until a valid snapshot arrives.", error);
            faultLogged = true;
        }
        state = null;
        menu = State.Menu.EMPTY;
        advice = null;
        inputStatus = null;
        layout = null;
    }

    private void reset() {
        reader.reset();
        screenIdentity = null;
        advice = null;
        state = null;
        menu = State.Menu.EMPTY;
        inputStatus = null;
        layout = null;
        faultLogged = false;
    }

    private void drawChoiceOutline(Minecraft client, AbstractContainerScreen<?> container, GuiGraphics graphics) {
        if (!enabled || client.options.hideGui || advice == null || advice.recommended() == null) return;
        Engine.Rating recommendation = advice.recommended();
        int index = recommendation.slot();
        var slots = container.getMenu().slots;
        if (index < 0 || index >= slots.size()) return;
        var slot = slots.get(index);
        if (!slot.isActive() || !slot.hasItem()) return;
        var choice = VisibleText.choiceEntry(slot.getItem().getHoverName().getString());
        if (choice == null || !choice.name().equals(recommendation.label())) return;

        int top = slot.y, bottom = slot.y + 16;
        var card = menu.choices().stream().filter(option -> option.slot() == index).findFirst().orElse(null);
        if (card != null) {
            for (int member : card.cardSlots()) {
                if (member < 0 || member >= slots.size() || member == index) continue;
                var part = slots.get(member);
                if (!part.isActive() || !part.hasItem() || part.x != slot.x || Math.abs(part.y - slot.y) > 36) continue;
                var partChoice = VisibleText.choiceEntry(part.getItem().getHoverName().getString());
                if (partChoice == null || !partChoice.name().equals(recommendation.label())) continue;
                top = Math.min(top, part.y);
                bottom = Math.max(bottom, part.y + 16);
            }
        }
        var origin = (ContainerScreenAccess) container;
        int x = origin.advisorLeftPos() + slot.x, y = origin.advisorTopPos() + top;
        int height = bottom - top;
        graphics.nextStratum();
        graphics.renderOutline(x - 2, y - 2, 20, height + 4, 0xFF0B2630);
        graphics.renderOutline(x - 1, y - 1, 18, height + 2, ACCENT);
    }

    void draw(GuiGraphics graphics, boolean inMenu, AdvisorOverlay overlay, boolean preview) {
        if (!preview && (!enabled || state == null || advice == null)) return;
        var client = Minecraft.getInstance();
        if (client.options.hideGui) return;
        if (overlay != null && Managers.Overlay.getSections().size() != 9) return;
        float drawScale = overlay == null ? scale : Math.min(overlay.getWidth() / AdvisorOverlay.WIDTH,
                graphics.guiWidth() / (float) AdvisorOverlay.WIDTH);
        if (!Float.isFinite(drawScale) || drawScale <= 0) return;
        int width = Math.min(inMenu ? 272 : AdvisorOverlay.WIDTH, (int) (graphics.guiWidth() / drawScale));
        if (inMenu) {
            int free = (graphics.guiWidth() - 176) / 2;
            if (free >= 100) width = Math.min(width, (int) ((free - 8) / scale));
        }
        if (width < 90) return;
        boolean sample = preview && advice == null;
        if (!sample && (layout == null || layout.advice != advice || layout.width != width || layout.font != client.font)) {
            layout = new HudLayout(advice, width, client.font,
                    client.font.split(Component.literal(advice.action()), width - 14),
                    client.font.split(Component.literal(advice.quickReason()), width - 14).stream().limit(2).toList(),
                    advice.visibleRatings());
        }
        String summary = sample ? "" : inputStatus.summary();
        String missing = sample || inputStatus.missing().isEmpty() ? "" : inputStatus.missing().getFirst();
        int lineCount = sample ? 3 : layout.action.size() + layout.reason.size() + layout.ratings.size()
                + (summary.isEmpty() ? 0 : 1) + (missing.isEmpty() ? 0 : 1) + (advice.warnings().isEmpty() ? 0 : 1);
        int height = 40 + 12 * lineCount;
        if (overlay != null) drawScale = Math.min(drawScale, Math.min(overlay.getHeight(), graphics.guiHeight()) / height);
        if (!Float.isFinite(drawScale) || drawScale <= 0) return;
        float x = inMenu ? 4 : offsetX;
        boolean lowerCorner = inMenu && graphics.guiWidth() < 376;
        float y = lowerCorner ? graphics.guiHeight() - height * drawScale - 4 : inMenu ? 4 : offsetY;
        if (overlay != null) {
            float extraWidth = overlay.getWidth() - width * drawScale;
            float extraHeight = overlay.getHeight() - height * drawScale;
            x = overlay.getRenderX() + switch (overlay.getPosition().getHorizontalAlignment()) {
                case LEFT -> 0;
                case CENTER -> extraWidth / 2;
                case RIGHT -> extraWidth;
            };
            y = overlay.getRenderY() + switch (overlay.getPosition().getVerticalAlignment()) {
                case TOP -> 0;
                case MIDDLE -> extraHeight / 2;
                case BOTTOM -> extraHeight;
            };
        }
        x = Math.clamp(x, 0, Math.max(0, graphics.guiWidth() - width * drawScale));
        y = Math.clamp(y, 0, Math.max(0, graphics.guiHeight() - height * drawScale));
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(drawScale, drawScale);
        try {
            graphics.fill(0, 0, width, height, BACKGROUND);
            graphics.fill(0, 0, 2, height, ACCENT);
            String title = inMenu ? "WYNNLOOTRUN" : "WYNNLOOTRUN  /  " + AdvisorScreen.goalName(goal);
            text(graphics, title, 8, ACCENT, width);
            if (sample) {
                text(graphics, "Xem trước HUD Lootrun", 21, MUTED, width);
                text(graphics, "Take BLUE / DEFEND (42m)", 34, FOREGROUND, width);
                text(graphics, "Read 2 beacons", 46, MUTED, width);
                return;
            }
            String progress = display(state.completed()) + " done   " + display(state.remaining()) + " left   "
                    + time(state.timer()) + "   BR " + display(state.beaconRerolls());
            if (menu.kind() == State.Kind.REWARD) {
                progress = display(menu.pulls()) + " pulls   " + display(menu.sacrifices()) + " sacrifices";
            }
            text(graphics, progress, 21, MUTED, width);
            int line = 34;
            for (var actionLine : layout.action) {
                graphics.drawString(client.font, actionLine, 8, line, FOREGROUND, false);
                line += 12;
            }
            for (var reasonLine : layout.reason) {
                graphics.drawString(client.font, reasonLine, 8, line, MUTED, false);
                line += 12;
            }
            if (!summary.isEmpty()) {
                text(graphics, summary, line, MUTED, width);
                line += 12;
            }
            for (var rating : layout.ratings) {
                String tier = rating.tier() >= 0 ? " · T" + rating.tier() : "";
                text(graphics, rating.badge() + tier + "  " + rating.label(), line,
                        rating.equals(advice.recommended()) ? ACCENT : FOREGROUND, width);
                line += 12;
            }
            if (!advice.warnings().isEmpty()) {
                text(graphics, advice.warnings().getFirst(), line, WARNING, width);
                line += 12;
            }
            if (!missing.isEmpty()) text(graphics, missing, line, WARNING, width);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void text(GuiGraphics graphics, String value, int y, int color, int width) {
        var font = Minecraft.getInstance().font;
        String shown = font.width(value) <= width - 14 ? value : font.plainSubstrByWidth(value, width - 24) + "…";
        graphics.drawString(font, shown, 8, y, color, false);
    }
    private static String display(int value) { return value < 0 ? "?" : Integer.toString(value); }
    private static String time(int seconds) {
        return seconds < 0 ? "?:??" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            var root = literal("wla").executes(ctx -> {
                ctx.getSource().sendFeedback(Component.literal(
                        "WynnLootrun Advisor | /wla goal, camp, weight, position, scale, toggle, details"));
                return 1;
            });
            var goals = literal("goal");
            for (State.Goal value : State.Goal.values()) {
                goals.then(literal(value.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                    goal = value;
                    return saved(ctx.getSource(), "Goal " + value.name());
                }));
            }
            var camps = literal("camp");
            for (State.Camp value : State.Camp.values()) {
                camps.then(literal(value == State.Camp.UNKNOWN ? "auto" : value.name().toLowerCase(Locale.ROOT))
                        .executes(ctx -> {
                            camp = value;
                            return saved(ctx.getSource(), "Camp " + (value == State.Camp.UNKNOWN ? "auto" : value.name()));
                        }));
            }
            root.then(goals).then(camps)
                .then(literal("toggle").executes(ctx -> {
                    enabled = !enabled;
                    reader.setEnabled(enabled);
                    reset();
                    return saved(ctx.getSource(), enabled ? "Advisor shown" : "Advisor hidden");
                }))
                .then(literal("position").then(argument("x", IntegerArgumentType.integer(0, 2000))
                        .then(argument("y", IntegerArgumentType.integer(0, 2000)).executes(ctx -> {
                            offsetX = IntegerArgumentType.getInteger(ctx, "x");
                            offsetY = IntegerArgumentType.getInteger(ctx, "y");
                            var overlay = AdvisorHudFeature.overlay();
                            if (overlay != null) {
                                overlay.setPosition(AdvisorOverlay.absolutePosition(offsetX, offsetY));
                                Managers.Config.saveConfig();
                            }
                            return saved(ctx.getSource(), "HUD position saved");
                        }))))
                .then(literal("scale").then(argument("factor", DoubleArgumentType.doubleArg(.75, 1.5)).executes(ctx -> {
                    scale = (float) DoubleArgumentType.getDouble(ctx, "factor");
                    var overlay = AdvisorHudFeature.overlay();
                    if (overlay != null) {
                        overlay.resize(scale);
                        Managers.Config.saveConfig();
                    }
                    return saved(ctx.getSource(), "HUD scale saved");
                })))
                .then(literal("weight")
                        .then(literal("reset").executes(ctx -> {
                            Arrays.fill(multipliers, 1);
                            return saved(ctx.getSource(), "Weights reset");
                        }))
                        .then(argument("axis", IntegerArgumentType.integer(1, 8))
                            .then(argument("factor", DoubleArgumentType.doubleArg(0, 2)).executes(ctx -> {
                                multipliers[IntegerArgumentType.getInteger(ctx, "axis") - 1]
                                        = DoubleArgumentType.getDouble(ctx, "factor");
                                return saved(ctx.getSource(), "Weight saved");
                            }))))
                .then(literal("details").executes(ctx -> {
                    var source = ctx.getSource();
                    if (advice == null || state == null) {
                        source.sendFeedback(Component.literal("No current Lootrun advice."));
                        return 1;
                    }
                    source.sendFeedback(Component.literal(advice.action() + " | " + advice.explanation()));
                    if (!inputStatus.summary().isEmpty()) source.sendFeedback(Component.literal(inputStatus.summary()));
                    inputStatus.missing().forEach(item -> source.sendFeedback(Component.literal(item)));
                    for (var rating : advice.ranked()) {
                        source.sendFeedback(Component.literal(String.format(Locale.ROOT, "%s  %.1f  %s",
                                rating.label(), rating.points(), rating.contributions().stream()
                                        .map(value -> String.format(Locale.ROOT, "%+.1f", value)).toList())));
                        source.sendFeedback(Component.literal(rating.reason()));
                    }
                    for (var card : menu.choices()) {
                        if (card.cardSlots().size() > 1) {
                            source.sendFeedback(Component.literal(card.name() + " | Card slots "
                                    + card.cardSlots().stream().map(slot -> slot + 1).toList()));
                        }
                    }
                    if (state.pulls() >= 0 && state.banked() >= 0 && state.endRerolls() >= 0 && state.sacrifices() >= 0) {
                        var economy = Engine.economy(state);
                        source.sendFeedback(Component.literal(String.format(Locale.ROOT,
                                "%.0f projected effective pulls | specific Mythic %.2f%% | Shiny %.2f%%",
                                economy.effectivePulls(), Engine.chance(economy.effectivePulls(), 5000) * 100,
                                Engine.chance(economy.effectivePulls(), 100000) * 100)));
                    }
                    for (var combo : engine.combos(state)) {
                        source.sendFeedback(Component.literal(combo.name() + " / " + combo.status()));
                    }
                    advice.warnings().forEach(warning -> source.sendFeedback(Component.literal(warning)));
                    return 1;
                }));
            dispatcher.register(root);
        });
    }

    private int saved(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String message) {
        engine = new Engine(multipliers);
        boolean persisted = saveSettings();
        source.sendFeedback(Component.literal(persisted ? message : "Settings changed for this session. Could not save to disk."));
        return 1;
    }

    private boolean selectGoal(State.Goal nextGoal) {
        goal = nextGoal;
        return saveSettings();
    }

    private boolean saveSettings() {
        advice = null;
        layout = null;
        Properties options = new Properties();
        options.setProperty("goal", goal.name());
        options.setProperty("camp", camp.name());
        options.setProperty("enabled", Boolean.toString(enabled));
        options.setProperty("x", Integer.toString(offsetX));
        options.setProperty("y", Integer.toString(offsetY));
        options.setProperty("scale", Float.toString(scale));
        for (int i = 0; i < multipliers.length; i++) options.setProperty("weight." + (i + 1), Double.toString(multipliers[i]));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (var output = Files.newOutputStream(CONFIG)) {
                options.store(output, "WynnLootrun Advisor");
            }
            return true;
        } catch (IOException | UncheckedIOException error) {
            LOGGER.warn("Could not save WynnLootrun Advisor settings", error);
            return false;
        }
    }

    private void load() {
        if (!Files.isRegularFile(CONFIG)) return;
        Properties options = new Properties();
        try (var input = Files.newInputStream(CONFIG)) {
            options.load(input);
            goal = State.Goal.valueOf(options.getProperty("goal", goal.name()));
            camp = State.Camp.valueOf(options.getProperty("camp", camp.name()));
            enabled = Boolean.parseBoolean(options.getProperty("enabled", "true"));
            offsetX = Math.max(0, Integer.parseInt(options.getProperty("x", "10")));
            offsetY = Math.max(0, Integer.parseInt(options.getProperty("y", "52")));
            float savedScale = Float.parseFloat(options.getProperty("scale", "1"));
            if (Float.isFinite(savedScale)) scale = Math.clamp(savedScale, .75f, 1.5f);
            for (int i = 0; i < multipliers.length; i++) {
                multipliers[i] = Double.parseDouble(options.getProperty("weight." + (i + 1), "1"));
            }
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.warn("Could not fully load WynnLootrun Advisor settings", error);
        }
    }
}

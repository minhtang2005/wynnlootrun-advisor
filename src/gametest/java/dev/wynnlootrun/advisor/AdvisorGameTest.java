package dev.wynnlootrun.advisor;

import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import com.wynntils.core.components.Managers;
import com.wynntils.screens.overlays.placement.OverlayManagementScreen;
import dev.wynnlootrun.advisor.mixin.ContainerScreenAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
import org.lwjgl.glfw.GLFW;

public final class AdvisorGameTest implements FabricClientGameTest {
    private static final String RESOURCE_PACK_TITLE = "\uE03B\uF02C";
    @Override
    public void runTest(ClientGameTestContext context) {
        Object advisor = FabricLoader.getInstance().getEntrypointContainers("client", ClientModInitializer.class)
                .stream().filter(entry -> entry.getProvider().getMetadata().getId().equals("wynnlootrun_advisor"))
                .findFirst().orElseThrow().getEntrypoint();
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("scoreboard objectives add advisor_test dummy");
            testBeaconChat(context, server, advisor);
            testOrangeChoices(context, server, advisor);
            showSidebar(server, "Choose a beacon!", "— Time Left: 8:10", "— Challenges: 0/12",
                    "Beacon Choices: 3", "Orange Beacon — Riverside Outpost",
                    "White Beacon — Broken Tower", "Pink Beacon — Abandoned Mine");
            assertAdvice(context, advisor, "Take ORANGE / Riverside Outpost");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst()
                    .sendSystemMessage(Component.literal("Click here to reroll\n(1 reroll left)")));
            context.waitFor(client -> ((State) field(advisor, "state")).beaconRerolls() == 1, 80);
            context.takeScreenshot("advisor-challenge");
            moveHud(context);

            showSidebar(server, "Choose a beacon!", "— Time Left: 8:10", "— Challenges: 1/12");
            spawnMarker(server, 0xFF9500, '\uE00D');
            assertAdvice(context, advisor, "Take ORANGE / DEFEND (42m)");
            server.runCommand("kill @e[type=minecraft:text_display]");
            if (FabricLoader.getInstance().getModContainer("wynntils").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString().equals("4.2.19")) {
                showSidebar(server, "Choose a beacon!", "— Time Left: 8:10", "— Challenges: 11/22");
                spawnMarker(server, 0xE08AFF, '\uE022');
                assertAdvice(context, advisor, "Take VIOLET / DEFEND (42m)");
                server.runCommand("kill @e[type=minecraft:text_display]");
            }

            showSidebar(server, "Collect your rewards!", "— Time Left: 8:00", "— Challenges: 4/30",
                    "Mission Completed: Porphyrophobia");
            assertAdvice(context, advisor, "Collect Challenge Rewards");
            showMenu(server, "Challenge Rewards", 11, "Chest Item");
            assertAdvice(context, advisor, "Collect Challenge Rewards");
            showMenu(server, RESOURCE_PACK_TITLE, 11, true, "Stasis", "Route Indigo", "Redemption");
            assertAdvice(context, advisor, "Choose Route Indigo");
            assertOutline(context, advisor, 13, "advisor-mission");
            var missionMenu = (State.Menu) field(advisor, "menu");
            if (missionMenu.choices().size() != 3 || !missionMenu.choices().get(1).cardSlots().equals(List.of(13, 22))) {
                throw new AssertionError("Six server slots should be three mission cards");
            }
            assertOutlineEdge(context, 22, "advisor-mission-card-bottom", true);
            var firstDecision = advice(advisor);
            context.waitTicks(12);
            if (advice(advisor) != firstDecision) throw new AssertionError("Identical menu and state recalculated advice");
            System.out.println("Advisor reused the same advice while the menu and scoreboard stayed unchanged");

            showSidebar(server, "Collect your rewards!", "— Time Left: 15:00 [+43s]", "— Challenges: 26/51",
                    "Hubris:", "— Complete 0/10 Challenges", "Opal Offering:", "— Add 12.5/18m to your timer");
            context.waitFor(client -> ((State) field(advisor, "state")).objectives().stream()
                    .anyMatch(objective -> objective.name().equals("Opal Offering") && objective.left() == 330), 80);
            showSidebar(server, "Defend for 30s!", "— Time Left: 9:20", "— Challenges: 30/60",
                    "Trial Completed: Dying Light", "Trial Completed: Hubris", "Mission Completed: Porphyrophobia");
            showMenu(server, RESOURCE_PACK_TITLE, 10, "All In", "Adrenaline Junkie");
            assertAdvice(context, advisor, "Choose All In");
            assertOutline(context, advisor, 10, "advisor-trial");

            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().closeContainer());
            context.waitFor(client -> client.screen == null, 80);
            selectGoal(context, "Nuôi Sac");
            showMenu(server, "Select a Trial", 10, "All In", "Adrenaline Junkie");
            assertAdvice(context, advisor, "Choose Adrenaline Junkie");
            assertOutline(context, advisor, 12, "advisor-trial-sacrifice");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().closeContainer());
            context.waitFor(client -> client.screen == null, 80);
            selectGoal(context, "Kiếm tiền");
            showMenu(server, "Select a Trial", 10, "All In", "Adrenaline Junkie");
            assertAdvice(context, advisor, "Choose All In");

            showSidebar(server, "Warping back to camp!", "— Time Left: 0:00", "— Challenges: 30/30");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().closeContainer());
            context.waitFor(client -> advice(advisor) == null && client.screen == null, 200);
        }
    }

    private static void testBeaconChat(ClientGameTestContext context, TestServerContext server, Object advisor) {
        showSidebar(server, "Choose a beacon!", "— Time Left: 3:39", "— Challenges: 0/12");
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.sendSystemMessage(Component.literal("\uDB00\uDC66Choose a Beacon!"));
            player.sendSystemMessage(Component.literal("Walk towards one to start a challenge"));
            player.sendSystemMessage(Component.literal("\uDB00\uDC22Purple Beacon\uDB00\uDC4BBlue Beacon"));
        });
        spawnMarker(server, 0x5C5CE6, '\uE00B');
        context.waitFor(client -> {
            var state = (State) field(advisor, "state");
            if (state == null || state.offers().size() != 2) return false;
            return state.offers().stream().anyMatch(offer -> offer.beacon() == State.Beacon.PURPLE && offer.distance() < 0)
                    && state.offers().stream().anyMatch(offer -> offer.beacon() == State.Beacon.BLUE && offer.distance() == 42)
                    && advice(advisor).ranked().size() == 2;
        }, 120);
        System.out.println("Advisor ranked Purple and Blue from the Wynncraft chat row with only a Blue marker");
        server.runCommand("kill @e[type=minecraft:text_display]");
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.sendSystemMessage(Component.literal("\uDB00\uDC66Choose a Beacon!"));
            player.sendSystemMessage(Component.literal("\uDB00\uDC22Vibrant Orange Beacon"));
        });
        context.waitFor(client -> {
            var offers = ((State) field(advisor, "state")).offers();
            return offers.size() == 1 && offers.getFirst().beacon() == State.Beacon.ORANGE && offers.getFirst().vibrant();
        }, 120);
        showSidebar(server, "Defeat the enemies!", "— Time Left: 3:30", "— Challenges: 0/12");
        context.waitFor(client -> ((State) field(advisor, "state")).offers().isEmpty(), 80);
        System.out.println("Advisor replaced the chat offers on a new prompt and cleared them on entering the challenge");
    }

    private static void moveHud(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        context.waitForScreen(AdvisorScreen.class);
        float[] moved = new float[2];
        context.runOnClient(client -> {
            var screen = (AdvisorScreen) client.screen;
            Button edit = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Di chuyển HUD")).findFirst().orElseThrow();
            screen.mouseClicked(new MouseButtonEvent(edit.getX() + 4, edit.getY() + 4, new MouseButtonInfo(0, 0)), false);
        });
        context.waitForScreen(OverlayManagementScreen.class);
        context.runOnClient(client -> {
            var editor = (OverlayManagementScreen) client.screen;
            var overlay = AdvisorHudFeature.overlay();
            if (editor.getSelectedOverlay() != overlay || !Managers.Overlay.getOverlays().contains(overlay)
                    || !overlay.getTranslatedName().equals("WynnLootrun Advisor") || !Managers.Overlay.isEnabled(overlay)) {
                throw new AssertionError("Wynntils did not register and select the Advisor HUD");
            }
            float oldX = overlay.getRenderX(), oldY = overlay.getRenderY();
            double x = oldX + 35, y = oldY + 24;
            var mouse = new MouseButtonInfo(0, 0);
            editor.mouseClicked(new MouseButtonEvent(x, y, mouse), false);
            var drop = new MouseButtonEvent(x + 40, y + 20, mouse);
            editor.mouseDragged(drop, 40, 20);
            editor.mouseReleased(drop);
            moved[0] = overlay.getRenderX();
            moved[1] = overlay.getRenderY();
            if (moved[0] < oldX + 12 || moved[1] < oldY + 5) {
                throw new AssertionError("Dragging did not move the HUD: " + moved[0] + ", " + moved[1]);
            }
            Button apply = editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().equals(
                            Component.translatable("screens.wynntils.overlayManagement.apply"))).findFirst().orElseThrow();
            editor.mouseClicked(new MouseButtonEvent(apply.getX() + 4, apply.getY() + 4, mouse), false);
        });
        context.waitForScreen(AdvisorScreen.class);
        context.runOnClient(client -> client.screen.onClose());
        context.waitFor(client -> client.screen == null, 80);
        context.runOnClient(client -> {
            var overlay = AdvisorHudFeature.overlay();
            if (overlay.getRenderX() != moved[0] || overlay.getRenderY() != moved[1]) {
                throw new AssertionError("Wynntils did not save the new position");
            }
        });
        context.takeScreenshot("advisor-dragged-hud");
        System.out.println("Advisor HUD moved in the Wynntils editor and the applied position was retained");
    }

    private static void testOrangeChoices(ClientGameTestContext context, TestServerContext server, Object advisor) {
        showSidebar(server, "Choose a beacon!", "— Time Left: 8:10", "— Challenges: 8/25",
                "Beacon Choices: 6", "Orange Beacon — Riverside Outpost", "Blue Beacon — Broken Tower");
        assertAdvice(context, advisor, "Take BLUE / Broken Tower");
        showSidebar(server, "Choose a beacon!", "— Time Left: 8:10", "— Challenges: 8/25",
                "Beacon Choices: 5", "Orange Beacon — Riverside Outpost", "Blue Beacon — Broken Tower");
        assertAdvice(context, advisor, "Take ORANGE / Riverside Outpost");
        System.out.println("Advisor deferred Orange at six choices and restored its priority at five");
    }

    private static void selectGoal(ClientGameTestContext context, String name) {
        context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        context.waitForScreen(AdvisorScreen.class);
        if (name.equals("Nuôi Sac")) context.takeScreenshot("advisor-goals");
        context.runOnClient(client -> {
            var screen = (AdvisorScreen) client.screen;
            if (screen.isPauseScreen()) throw new AssertionError("The goal screen should leave game time running");
            Button button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(choice -> choice.getMessage().getString().equals(name)).findFirst().orElseThrow();
            var mouse = new MouseButtonEvent(button.getX() + 4, button.getY() + 4, new MouseButtonInfo(0, 0));
            if (!screen.mouseClicked(mouse, false)) throw new AssertionError("Could not select " + name);
        });
        context.waitFor(client -> client.screen == null, 80);
        System.out.println("Advisor opened with F8 and selected " + name);
    }

    private static void assertAdvice(ClientGameTestContext context, Object advisor, String expected) {
        try {
            context.waitFor(client -> {
                var advice = advice(advisor);
                return advice != null && advice.action().equals(expected);
            }, 240);
        } catch (AssertionError timeout) {
            throw new AssertionError("Expected " + expected + ", got " + advice(advisor), timeout);
        }
        System.out.println("Advisor received server data and selected " + expected);
    }

    private static Engine.Advice advice(Object advisor) {
        return (Engine.Advice) field(advisor, "advice");
    }

    private static Object field(Object advisor, String name) {
        try {
            var field = advisor.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(advisor);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot check the production Advisor's " + name, error);
        }
    }

    private static void assertOutline(ClientGameTestContext context, Object advisor, int expectedSlot, String screenshot) {
        if (advice(advisor).recommended().slot() != expectedSlot) throw new AssertionError("Wrong recommended slot");
        assertOutlineEdge(context, expectedSlot, screenshot, false);
    }

    private static void assertOutlineEdge(ClientGameTestContext context, int expectedSlot,
                                           String screenshot, boolean bottom) {
        int[] position = new int[2];
        context.runOnClient(client -> {
            var screen = (AbstractContainerScreen<?>) client.screen;
            var origin = (ContainerScreenAccess) screen;
            var slot = screen.getMenu().slots.get(expectedSlot);
            int scale = client.getWindow().getGuiScale();
            position[0] = (origin.advisorLeftPos() + slot.x + 8) * scale;
            position[1] = (origin.advisorTopPos() + slot.y + (bottom ? 16 : -1)) * scale;
        });
        try {
            var image = ImageIO.read(context.takeScreenshot(screenshot).toFile());
            for (int y = position[1] - 3; y <= position[1] + 3; y++) {
                if ((image.getRGB(position[0], y) & 0xFFFFFF) == 0x74E2BB) {
                    System.out.println("Advisor outlined the recommended " + screenshot + " slot");
                    return;
                }
            }
            throw new AssertionError("No outline at recommended " + screenshot + " slot");
        } catch (IOException error) {
            throw new AssertionError("Cannot inspect the menu screenshot", error);
        }
    }

    private static void showSidebar(TestServerContext server, String... rows) {
        server.runOnServer(mc -> {
            var scoreboard = mc.getScoreboard();
            var objective = scoreboard.getObjective("advisor_test");
            objective.setDisplayName(Component.literal("Lootrun:"));
            for (int i = 0; i < 10; i++) {
                var holder = ScoreHolder.forNameOnly("advisor_row_" + i);
                if (i >= rows.length) {
                    scoreboard.resetSinglePlayerScore(holder, objective);
                    continue;
                }
                var score = scoreboard.getOrCreatePlayerScore(holder, objective);
                score.set(rows.length - i);
                score.display(Component.literal(rows[i]));
            }
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        });
    }

    private static void spawnMarker(TestServerContext server, int color, char task) {
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            var level = mc.overworld();
            var marker = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            var font = new FontDescription.Resource(Identifier.withDefaultNamespace("marker"));
            var label = Component.literal("\uDAFF\uDFF8\uE010\uDAFF\uDFDE").setStyle(Style.EMPTY.withFont(font))
                    .append(Component.literal("\uE000").withColor(color))
                    .append(Component.literal("\uDAFF\uDFE6" + task + "\n42m "));
            marker.setText(label);
            marker.setPos(player.getX() + 3, player.getY() + 2, player.getZ());
            level.addFreshEntity(marker);
        });
    }

    private static void showMenu(TestServerContext server, String title, int firstSlot, String... options) {
        showMenu(server, title, firstSlot, false, options);
    }

    private static void showMenu(TestServerContext server, String title, int firstSlot, boolean duplicateCards,
                                 String... options) {
        server.runOnServer(mc -> {
            ServerPlayer player = mc.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var entries = new SimpleContainer(27);
            for (int i = 0; i < options.length; i++) {
                ItemStack option = new ItemStack(Items.PAPER);
                option.set(DataComponents.CUSTOM_NAME, Component.literal(options[i]));
                entries.setItem(firstSlot + i * 2, option);
                if (duplicateCards) entries.setItem(firstSlot + i * 2 + 9, option.copy());
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> ChestMenu.threeRows(id, inventory, entries),
                    Component.literal(title)));
        });
    }
}

package dev.wynnlootrun.advisor;

import java.util.function.Predicate;

import com.wynntils.core.components.Managers;
import com.wynntils.screens.overlays.placement.OverlayManagementScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class AdvisorScreen extends Screen {
    private static final int FOREGROUND = 0xFFF4F8FF, MUTED = 0xFFADBBD0, ACCENT = 0xFF74E2BB;
    private final KeyMapping shortcut;
    private final Predicate<State.Goal> select;
    private State.Goal activeGoal;
    private Button cashout, sacrifice;
    private boolean saveFailed;

    AdvisorScreen(KeyMapping shortcut, State.Goal activeGoal, Predicate<State.Goal> select) {
        super(Component.literal("WynnLootrun Advisor"));
        this.shortcut = shortcut;
        this.activeGoal = activeGoal;
        this.select = select;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(204, width - 28);
        int left = (width - buttonWidth) / 2;
        int top = height / 2 - 89;
        cashout = addRenderableWidget(Button.builder(Component.literal("Kiếm tiền"),
                button -> choose(State.Goal.CASHOUT)).bounds(left, top + 43, buttonWidth, 20).build());
        sacrifice = addRenderableWidget(Button.builder(Component.literal("Nuôi Sac"),
                button -> choose(State.Goal.SACRIFICE_STACK)).bounds(left, top + 84, buttonWidth, 20).build());
        var overlay = AdvisorHudFeature.overlay();
        Button editHud = addRenderableWidget(Button.builder(Component.literal("Di chuyển HUD"),
                button -> minecraft.setScreen(OverlayManagementScreen.create(this, overlay)))
                .bounds(left, top + 127, buttonWidth, 20).build());
        editHud.active = overlay != null && Managers.Overlay.isEnabled(overlay);
        addRenderableWidget(Button.builder(Component.literal("Quay lại"),
                button -> onClose()).bounds(width / 2 - 42, top + 153, 84, 20).build());
        markActive();
    }

    private void choose(State.Goal goal) {
        if (select.test(goal)) {
            onClose();
        } else {
            activeGoal = goal;
            saveFailed = true;
            markActive();
        }
    }

    private void markActive() {
        cashout.setMessage(Component.literal(activeGoal == State.Goal.CASHOUT ? "✓ Kiếm tiền" : "Kiếm tiền"));
        sacrifice.setMessage(Component.literal(activeGoal == State.Goal.SACRIFICE_STACK ? "✓ Nuôi Sac" : "Nuôi Sac"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = height / 2 - 89, center = width / 2;
        int half = Math.min(116, (width - 8) / 2);
        graphics.fill(center - half, top - 9, center + half, top + 180, 0xF2121B2A);
        graphics.fill(center - half, top - 9, center + half, top - 8, ACCENT);
        graphics.drawCenteredString(font, title, center, top + 6, FOREGROUND);
        graphics.drawCenteredString(font, "Đang dùng: " + goalName(activeGoal), center, top + 22, ACCENT);
        graphics.drawCenteredString(font, "Xả kho và ưu tiên End Reward Reroll", center, top + 68, MUTED);
        graphics.drawCenteredString(font, "Tích Pulls qua nhiều run, khóa All In", center, top + 109, MUTED);
        if (saveFailed) graphics.drawCenteredString(font, "Chỉ lưu cho phiên này", center, top + 119, 0xFFFFCC88);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (shortcut.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    static String goalName(State.Goal goal) {
        return switch (goal) {
            case CASHOUT -> "Kiếm tiền";
            case SACRIFICE_STACK -> "Nuôi Sac";
            case MAX_PULL -> "Tối đa Pulls";
            case FLYING_CHEST -> "Flying Chest";
        };
    }
}

package dev.wynnlootrun.advisor;

import com.mojang.blaze3d.platform.Window;
import com.wynntils.core.components.Managers;
import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.consumers.features.ProfileDefault;
import com.wynntils.core.consumers.overlays.Overlay;
import com.wynntils.core.consumers.overlays.OverlayPosition;
import com.wynntils.core.consumers.overlays.annotations.RegisterOverlay;
import com.wynntils.core.persisted.config.Category;
import com.wynntils.core.persisted.config.ConfigCategory;
import com.wynntils.utils.render.type.HorizontalAlignment;
import com.wynntils.utils.render.type.VerticalAlignment;
import com.wynntils.utils.type.RenderElementType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

@ConfigCategory(Category.OVERLAYS)
public final class AdvisorHudFeature extends Feature {
    @RegisterOverlay(renderType = RenderElementType.GUI_POST)
    private final AdvisorOverlay overlay = new AdvisorOverlay();

    public AdvisorHudFeature() {
        super(ProfileDefault.ENABLED);
    }

    static AdvisorOverlay overlay() {
        AdvisorHudFeature feature = Managers.Feature.getFeatureInstance(AdvisorHudFeature.class);
        return feature == null ? null : feature.overlay;
    }

    static final class AdvisorOverlay extends Overlay {
        static final int WIDTH = 212;
        static final int HEIGHT = 156;

        AdvisorOverlay() {
            super(absolutePosition(AdvisorClient.defaultHudX(), AdvisorClient.defaultHudY()),
                    WIDTH * AdvisorClient.defaultHudScale(), HEIGHT * AdvisorClient.defaultHudScale());
        }

        static OverlayPosition absolutePosition(int x, int y) {
            return new OverlayPosition(y, x, VerticalAlignment.TOP, HorizontalAlignment.LEFT,
                    OverlayPosition.AnchorSection.TOP_LEFT);
        }

        void resize(float scale) {
            setWidth(WIDTH * scale);
            setHeight(HEIGHT * scale);
        }

        @Override
        protected boolean isVisible() {
            var advisor = AdvisorClient.instance();
            return advisor != null && advisor.showWorldHud();
        }

        @Override
        public void render(GuiGraphics graphics, DeltaTracker delta, Window window) {
            var advisor = AdvisorClient.instance();
            if (advisor != null && advisor.showWorldHud()) advisor.draw(graphics, false, this, false);
        }

        @Override
        public void renderPreview(GuiGraphics graphics, DeltaTracker delta, Window window) {
            var advisor = AdvisorClient.instance();
            if (advisor != null) advisor.draw(graphics, false, this, true);
        }
    }
}

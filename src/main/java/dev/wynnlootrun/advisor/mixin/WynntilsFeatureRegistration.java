package dev.wynnlootrun.advisor.mixin;

import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.consumers.features.FeatureManager;
import dev.wynnlootrun.advisor.AdvisorHudFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FeatureManager.class, remap = false)
public abstract class WynntilsFeatureRegistration {
    @Invoker("registerFeature")
    abstract void advisorRegisterFeature(Feature feature);

    @Inject(method = "init", at = @At("RETURN"))
    private void advisorAddHud(CallbackInfo callback) {
        advisorRegisterFeature(new AdvisorHudFeature());
    }
}

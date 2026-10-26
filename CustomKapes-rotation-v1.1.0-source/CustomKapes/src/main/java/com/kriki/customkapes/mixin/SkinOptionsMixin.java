package com.kriki.customkapes.mixin;

import com.kriki.customkapes.CustomKapesClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptionsScreen.class)
public abstract class SkinOptionsMixin extends Screen {
    protected SkinOptionsMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void customKapes$addToggle(CallbackInfo ci) {
        if (!((Object) this instanceof SkinOptionsScreen)) return;
        ButtonWidget button = ButtonWidget.builder(label(), b -> {
            CustomKapesClient.toggle();
            b.setMessage(label());
        }).dimensions(this.width / 2 - 100, this.height - 52, 200, 20).build();
        this.addDrawableChild(button);
    }

    private static Text label() {
        return Text.literal("Kape Toggle: " + (CustomKapesClient.isEnabled() ? "ON" : "OFF"));
    }
}

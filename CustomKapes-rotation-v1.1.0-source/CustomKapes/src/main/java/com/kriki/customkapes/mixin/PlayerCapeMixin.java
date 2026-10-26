package com.kriki.customkapes.mixin;

import com.kriki.customkapes.CustomKapesClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.authlib.GameProfile;

@Mixin(PlayerListEntry.class)
public abstract class PlayerCapeMixin {
    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void customKapes$replaceCape(CallbackInfoReturnable<SkinTextures> cir) {
        if (!CustomKapesClient.hasCape()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !getProfile().id().equals(client.player.getUuid())) return;
        SkinTextures original = cir.getReturnValue();
        if (original == null) return;
        AssetInfo.TextureAsset custom = new AssetInfo.TextureAssetInfo(CustomKapesClient.capeId());
        cir.setReturnValue(new SkinTextures(original.body(), custom, original.elytra(), original.model(), original.secure()));
    }
}

package com.yourname.elytraslot.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourname.elytraslot.ElytraRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WingsLayer 原本读取 chestEquipment 判断是否渲染鞘翅。
 * 现在 chestEquipment 已是真实胸甲，这里改为读取单独存好的鞘翅字段。
 * WingsLayer 是 AvatarRenderer 里最后添加的层（护甲→披风→翅膀），
 * 因此临时改写 chestEquipment 不会影响已渲染的胸甲。
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
        at = @At("HEAD")
    )
    private void elytraslot$useElytra(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float partialTick, float ageInTicks, CallbackInfo ci) {
        ItemStack elytra = ((ElytraRenderStateAccess) state).elytraslot$getElytra();
        if (!elytra.isEmpty()) {
            state.chestEquipment = elytra;
        }
    }
}

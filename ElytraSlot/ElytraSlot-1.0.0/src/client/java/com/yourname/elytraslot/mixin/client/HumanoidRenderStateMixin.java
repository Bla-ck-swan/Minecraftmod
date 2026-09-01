package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraRenderStateAccess;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements ElytraRenderStateAccess {

    @Unique private ItemStack elytraslot$elytra = ItemStack.EMPTY;

    @Override
    public ItemStack elytraslot$getElytra() {
        return elytraslot$elytra == null ? ItemStack.EMPTY : elytraslot$elytra;
    }

    @Override
    public void elytraslot$setElytra(ItemStack stack) {
        elytraslot$elytra = stack == null ? ItemStack.EMPTY : stack;
    }
}

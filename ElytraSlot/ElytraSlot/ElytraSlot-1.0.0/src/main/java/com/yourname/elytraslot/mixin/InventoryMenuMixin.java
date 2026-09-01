package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift+点击鞘翅 → 快速穿戴到自定义鞘翅槽。
 * 原版 quickMoveStack 会把鞘翅移向胸甲槽，被 isEquippableInSlot 拦截后卡住；
 * 这里在开头拦截，直接把鞘翅放进自定义槽。
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void elytraslot$handleQuickMoveElytra(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        // slots 字段定义在父类 AbstractContainerMenu 中，直接访问 public 字段
        NonNullList<Slot> slots = ((AbstractContainerMenu) (Object) this).slots;
        if (slotIndex < 0 || slotIndex >= slots.size()) return;

        Slot slot = slots.get(slotIndex);
        ItemStack stack = slot.getItem();
        if (stack.getItem() != Items.ELYTRA) return; // 只处理鞘翅

        ElytraSlotAccess access = (ElytraSlotAccess) player;
        if (!access.elytraslot$getStack().isEmpty()) return; // 自定义槽非空 → 交给原版逻辑

        // 鞘翅移入自定义槽，清空原槽
        access.elytraslot$setStack(stack.copy());
        slot.set(ItemStack.EMPTY);
        cir.setReturnValue(ItemStack.EMPTY);
    }
}

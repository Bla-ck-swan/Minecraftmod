package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复：右键穿戴鞘翅时卸掉胸甲。
 * 原版 Equippable.swapWithEquipmentSlot 会先 getItemBySlot(CHEST) 拿到胸甲，
 * 再 copyAndClear() 把胸甲槽清空、胸甲塞进手，最后才 setItemSlot(CHEST, 鞘翅)。
 * 我们之前只拦截了 setItemSlot，导致胸甲被 swap 逻辑卸下。
 *
 * 这里在方法开头直接拦截鞘翅，与「自定义鞘翅槽」互换，全程不碰胸甲槽。
 * 逻辑与原版 swap 一一对应，仅把 setItemSlot(CHEST) 换成自定义槽写入。
 */
@Mixin(Equippable.class)
public abstract class EquippableMixin {

    @Inject(method = "swapWithEquipmentSlot", at = @At("HEAD"), cancellable = true)
    private void elytraslot$swapWithCustomSlot(ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (stack.getItem() != Items.ELYTRA) return;

        ElytraSlotAccess access = (ElytraSlotAccess) player;
        ItemStack current = access.elytraslot$getStack();

        // 相同物品不重复装备
        if (ItemStack.isSameItemSameComponents(stack, current)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 统计（仅服务端）
        if (!player.level().isClientSide()) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        if (stack.getCount() <= 1) {
            // toHand：旧鞘翅（或空）进手；toSlot：新鞘翅进自定义槽
            ItemStack toHand = current.isEmpty() ? stack : current.copy();
            ItemStack toSlot = player.isCreative() ? stack.copy() : stack.copyAndClear();
            access.elytraslot$setStack(toSlot);
            cir.setReturnValue(InteractionResult.SUCCESS.heldItemTransformedTo(toHand));
        } else {
            // 理论上鞘翅不可堆叠，此处仅为完整性保留
            ItemStack toHand = current.copy();
            ItemStack toSlot = stack.consumeAndReturn(1, player);
            access.elytraslot$setStack(toSlot);
            if (!player.getInventory().add(toHand)) {
                player.drop(toHand, false);
            }
            cir.setReturnValue(InteractionResult.SUCCESS.heldItemTransformedTo(stack));
        }
    }
}

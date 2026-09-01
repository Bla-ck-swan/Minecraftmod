package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraCreativeSlot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 创造模式：禁止把自定义鞘翅槽作为「拖拽目标」。
 *
 * 原因：创造 INVENTORY 标签页的拖拽（QUICK_CRAFT）由原版 slotClicked 分发到
 * 客户端 inventoryMenu 的 quickcraft 机制，期间会对槽做 SlotWrapper 强转
 * （slotClicked 440 行分支），自定义槽不是 SlotWrapper 会崩溃；且单槽拖拽结束会
 * 递归 doClick(slot.index, PICKUP) 越界。禁止收集为拖拽目标后：
 * - 从自定义槽拾取到光标、再拖拽分发到其他槽：正常（PICKUP + 原版分发）
 * - 点击把光标鞘翅放入自定义槽：正常（PICKUP 放置）
 * - 拖拽「途经」自定义槽：跳过该槽，不会崩溃
 */
@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class ItemPickerMenuMixin {

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void elytraslot$blockDragToCustomSlot(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (slot instanceof ElytraCreativeSlot) {
            cir.setReturnValue(false);
        }
    }
}

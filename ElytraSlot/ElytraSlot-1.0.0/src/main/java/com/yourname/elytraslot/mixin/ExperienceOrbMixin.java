package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验修补（Mending）的同步补丁。
 *
 * Mending 的"找装备"走 EnchantmentHelper.getRandomItemWith，它遍历
 * EquipmentSlot.VALUES 并调用 getItemBySlot —— 而本 mod 劫持了 getItemBySlot(CHEST)
 * 返回自定义槽鞘翅，所以服务端耐久实际上已经被原版逻辑正确修复。
 *
 * 但修复用的是 ItemStack.setDamageValue（改对象内部状态），DataWatcher 的引用没变，
 * 不会自动同步，导致客户端耐久显示滞后。这里在 repairPlayerItems 返回时强制同步一次。
 *
 * repairPlayerItems 是递归方法（修完一件还有剩余经验会递归修下一件），
 * RETURN 注入会触发多次，但 forceSync 是幂等的、且经验修补本身是低频事件，无碍。
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    @Inject(method = "repairPlayerItems", at = @At("RETURN"))
    private void elytraslot$syncAfterMending(ServerPlayer player, int repairAmount, CallbackInfoReturnable<Integer> cir) {
        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (!elytra.isEmpty()) {
            ((ElytraSlotAccess) player).elytraslot$forceSync();
        }
    }
}

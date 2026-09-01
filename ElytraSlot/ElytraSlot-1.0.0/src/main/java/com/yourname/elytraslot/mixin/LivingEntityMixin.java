package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlot;
import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 核心思路：把原版「胸甲槽 = 鞘翅」的判断，重定向到「自定义槽 = 鞘翅」。
 * 通过覆写 getItemBySlot(CHEST)，让原版 canGlide / updateFallFlying 自动处理
 * 飞行、耐久、附魔、粒子，第三人称渲染和鞘翅尾迹模组也能正确识别。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, Level level) { super(type, level); }

    @Shadow protected EntityEquipment equipment;

    @Shadow protected int fallFlyTicks;

    /* ──────────────── getItemBySlot(CHEST)：自定义槽有鞘翅 → 始终返回鞘翅 ────────────────
     * 无论胸甲槽是否放了胸甲，只要自定义槽有鞘翅，原版读 CHEST 槽时都返回鞘翅。
     * 这样 canGlide / updateFallFlying 耐久 / 第三人称渲染 / 鞘翅尾迹都自动识别鞘翅，
     * 胸甲 + 鞘翅同时穿戴也能正常飞行。
     * 护甲值、死亡掉落、装备同步都走 equipment.items（真实胸甲），不受此影响。 */
    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void elytraslot$getItemBySlot(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != EquipmentSlot.CHEST) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;

        // 自定义槽有鞘翅 → 始终返回鞘翅（胸甲槽是否放胸甲不影响）
        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (!elytra.isEmpty() && elytra.getItem() == Items.ELYTRA) {
            cir.setReturnValue(elytra);
        }
    }

    /* ──────────────── 装备同步：CHEST 槽用真实胸甲，避免鞘翅被误同步 ──────────────── */
    @Redirect(
        method = "collectEquipmentChanges",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack elytraslot$redirectCollectItemBySlot(LivingEntity instance, EquipmentSlot slot) {
        if (slot == EquipmentSlot.CHEST && instance instanceof Player) {
            return this.equipment.get(EquipmentSlot.CHEST);
        }
        return instance.getItemBySlot(slot);
    }

    /* ──────────────── 阻止胸甲槽放鞘翅（UI 拖拽） ──────────────── */
    @Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
    private void elytraslot$blockChestElytra(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
        if (slot == EquipmentSlot.CHEST && stack.getItem() == Items.ELYTRA) {
            cir.setReturnValue(false);
        }
    }

    /* ──────────────── 右键装备鞘翅 → 重定向到自定义槽 ────────────────
     * 右键装备走 SlotAccess → setItemSlot(CHEST)，绕过了 isEquippableInSlot，
     * 所以这里直接把要进胸甲槽的鞘翅改放到自定义槽。 */
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void elytraslot$redirectChestElytra(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (slot != EquipmentSlot.CHEST || stack.getItem() != Items.ELYTRA) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;

        // 客户端：只阻止鞘翅进入胸甲槽，槽位内容以服务端 sync 为准（避免乐观更新产生"幽灵鞘翅"）
        if (player.level().isClientSide()) {
            ci.cancel();
            return;
        }

        // 服务端：把鞘翅放进自定义槽
        ElytraSlotAccess access = (ElytraSlotAccess) player;
        ItemStack current = access.elytraslot$getStack();
        if (!current.isEmpty()) {
            // 自定义槽已有鞘翅：把旧的放回背包，避免丢失
            player.getInventory().add(current.copy());
        }
        access.elytraslot$setStack(stack.copy());
        ci.cancel(); // 阻止鞘翅真正进入胸甲槽
    }

    /* ──────────────── 飞行时同步鞘翅耐久到客户端 ────────────────
     * updateFallFlying 里原版 hurtAndBreak 消耗的是自定义槽鞘翅的耐久（服务端），
     * 但自定义槽不在 equipment 里，装备同步不会覆盖它，客户端耐久会滞后。
     * 所以每 20 tick（耐久消耗间隔，fallFlyTicks 为 20 的倍数时）同步一次。 */
    @Inject(method = "updateFallFlying", at = @At("TAIL"))
    private void elytraslot$syncDurability(CallbackInfo ci) {
        if (fallFlyTicks % 20 != 0) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        ItemStack elytra = ((ElytraSlotAccess) sp).elytraslot$getStack();
        if (!elytra.isEmpty()) {
            ((ElytraSlotAccess) sp).elytraslot$forceSync();
        }
    }

    /* ──────────────── 死亡：按 keepInventory 规则处理自定义鞘翅 ──────────────── */
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void elytraslot$handleDeath(ServerLevel level, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (elytra.isEmpty()) return;

        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            // keepInventory 开：暂存，重生后由 COPY_FROM 恢复
            ElytraSlot.stashForDeath(player);
        } else {
            // keepInventory 关：掉落为物品
            ((ElytraSlotAccess) player).elytraslot$setStack(ItemStack.EMPTY);
            player.drop(elytra.copy(), true);
        }
    }
}

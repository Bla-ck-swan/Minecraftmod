package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerEntityMixin extends LivingEntity implements ElytraSlotAccess {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }

    /*
     * 鞘翅槽改用 DataWatcher（SynchedEntityData）存储：
     * 服务端 set 会自动同步给所有追踪该玩家的客户端（包括其他玩家），
     * 从而解决「别人视角看不到鞘翅」的问题。DataWatcher 是瞬态数据，
     * 仍通过 addAdditionalSaveData/readAdditionalSaveData 持久化。
     */
    @Unique
    private static final EntityDataAccessor<ItemStack> ELYTRA_SLOT =
        SynchedEntityData.defineId(Player.class, EntityDataSerializers.ITEM_STACK);

    @Override
    public ItemStack elytraslot$getStack() {
        ItemStack s = this.getEntityData().get(ELYTRA_SLOT);
        return s == null ? ItemStack.EMPTY : s;
    }

    @Override
    public void elytraslot$setStack(ItemStack s) {
        this.getEntityData().set(ELYTRA_SLOT, s == null ? ItemStack.EMPTY : s);
    }

    @Override
    public void elytraslot$forceSync() {
        ItemStack current = this.getEntityData().get(ELYTRA_SLOT);
        this.getEntityData().set(ELYTRA_SLOT, current == null ? ItemStack.EMPTY : current, true);
    }

    @Override
    public ItemStack elytraslot$getRealChest() {
        return this.equipment.get(EquipmentSlot.CHEST);
    }

    /* ──────────────── 注册 DataWatcher 字段 ──────────────── */
    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void elytraslot$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(ELYTRA_SLOT, ItemStack.EMPTY);
    }

    /* ──────────────── 持久化：退出游戏保存 / 进入游戏加载 ──────────────── */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void elytraslot$save(ValueOutput output, CallbackInfo ci) {
        ItemStack elytra = elytraslot$getStack();
        if (!elytra.isEmpty()) {
            output.store("elytraslot", ItemStack.CODEC, elytra);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void elytraslot$load(ValueInput input, CallbackInfo ci) {
        input.read("elytraslot", ItemStack.CODEC).ifPresent(stack -> {
            if (!stack.isEmpty() && stack.getItem() == Items.ELYTRA) {
                elytraslot$setStack(stack);
            }
        });
    }
}

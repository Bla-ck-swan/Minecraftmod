package com.yourname.elytraslot;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 创造模式「物品栏」标签页的鞘翅槽：作为真实 Slot 加入 ItemPickerMenu.slots，
 * 让原版点击/拖拽（QUICK_CRAFT）/快速移动机制直接作用于它。
 *
 * 数据仍存于 DataWatcher（ElytraSlotAccess），每次 set 通过自定义包同步到服务端；
 * 服务端更新 DataWatcher 后再广播回所有视角。容器只是占位（用于区分创造物品列表槽，
 * 使 canDragTo=true、isCreativeSlot=false）。
 */
public class ElytraCreativeSlot extends Slot {

    /** 槽位位置（相对界面左上角，与 ElytraSlotRenderer 的 SLOT_X/Y 一致） */
    public static final int SLOT_X = 127;
    public static final int SLOT_Y = 20;

    private final Player player;

    public ElytraCreativeSlot(Player player) {
        super(new SimpleContainer(1), 0, SLOT_X, SLOT_Y);
        this.player = player;
    }

    @Override
    public ItemStack getItem() {
        return ((ElytraSlotAccess) player).elytraslot$getStack();
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() == Items.ELYTRA;
    }

    @Override
    public boolean mayPickup(Player p) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void set(ItemStack stack) {
        apply(stack);
    }

    @Override
    public void setByPlayer(ItemStack stack) {
        apply(stack);
    }

    @Override
    public void setByPlayer(ItemStack oldStack, ItemStack newStack) {
        apply(newStack);
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack cur = getItem();
        if (cur.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = cur.split(amount);
        apply(cur);
        return removed;
    }

    /* ── 本地更新 DataWatcher + 通知服务端更新槽内容 ── */
    private void apply(ItemStack stack) {
        ItemStack s = stack == null ? ItemStack.EMPTY : stack;
        ((ElytraSlotAccess) player).elytraslot$setStack(s);
        ClientPlayNetworking.send(new ElytraSlotPacket.Click(s, false));
    }
}

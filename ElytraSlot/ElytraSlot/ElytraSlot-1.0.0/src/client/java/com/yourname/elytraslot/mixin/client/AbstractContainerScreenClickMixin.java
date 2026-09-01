package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraSlot;
import com.yourname.elytraslot.ElytraSlotAccess;
import com.yourname.elytraslot.ElytraSlotPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 生存模式鞘翅槽的交互（创造模式已由 CreativeModeInventoryScreenMixin + ElytraCreativeSlot
 * 以真实槽位方式处理，这里不再拦截创造点击）。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenClickMixin {

    /* 生存模式槽位：副手槽 (77,62) 上方一格 */
    private static final int SURVIVAL_SLOT_X = 77;
    private static final int SURVIVAL_SLOT_Y = 44;

    /**
     * 判断屏幕坐标 (x, y) 是否落在生存模式自定义鞘翅槽（16×16）上。
     * 创造模式返回 false：自定义槽是真实 Slot，交互完全交给原版流程。
     */
    private boolean elytraslot$isOverElytraSlot(double x, double y) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof CreativeModeInventoryScreen) return false;
        if (!(screen.getMenu() instanceof InventoryMenu)) return false;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
        int sx = a.getLeftPos() + SURVIVAL_SLOT_X;
        int sy = a.getTopPos() + SURVIVAL_SLOT_Y;
        return x >= sx && x < sx + 16 && y >= sy && y < sy + 16;
    }

    /**
     * 让原版在自定义槽位置「看不到」下面的槽位（生存模式该位置无真实槽，防御性隐藏，
     * 避免拖拽 quick craft 或双击收集把光标物品错误地作用到该区域）。
     */
    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void elytraslot$hideSlotUnderElytra(double x, double y, CallbackInfoReturnable<Slot> cir) {
        if (elytraslot$isOverElytraSlot(x, y)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * 拦截 mouseReleased：生存模式在自定义槽位置松开时取消原版行为
     * （否则 getHoveredSlot=null → slotId=-999 → THROW，会把光标物品丢出去）。
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void elytraslot$cancelReleaseOnElytra(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (elytraslot$isOverElytraSlot(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void handleElytraSlotClick(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        // 创造模式：交给原版流程（CreativeModeInventoryScreenMixin.slotClicked 处理自定义槽）
        if (screen instanceof CreativeModeInventoryScreen) return;

        if (!(screen.getMenu() instanceof InventoryMenu menu)) return;
        Player player = ((InventoryMenuAccessor) menu).getOwner();
        if (player == null) return;

        // 鼠标是否命中自定义槽位
        if (!elytraslot$isOverElytraSlot(event.x(), event.y())) return;

        // 原版 mouseClicked 每次都会把 skipNextRelease 置 false；我们在 HEAD 拦截会跳过这行，
        // 若残留为 true，mouseReleased 会直接 return（跳过放置的 slotClicked），
        // 导致服务端 carried 没被清空、关闭屏幕时被 removed 返还到快捷栏（复制）。这里补上重置。
        ((AbstractContainerScreenAccessor) this).setSkipNextRelease(false);

        ItemStack carried = screen.getMenu().getCarried();
        ItemStack slotStack = ((ElytraSlotAccess) player).elytraslot$getStack();
        boolean right = (event.button() == 1);
        boolean shift = event.buttonInfo().hasShiftDown();

        // Shift+点击 → 快速放回背包（除非背包满）
        if (shift && !right && !slotStack.isEmpty()) {
            ClientPlayNetworking.send(new ElytraSlotPacket.Click(ItemStack.EMPTY, true));
            cir.setReturnValue(true);
            return;
        }

        // 右键：取一半
        if (right && carried.isEmpty() && !slotStack.isEmpty()) {
            int half = (slotStack.getCount() + 1) / 2;
            ItemStack taken = slotStack.split(half);
            if (slotStack.isEmpty()) {
                ((ElytraSlotAccess) player).elytraslot$setStack(ItemStack.EMPTY);
            }
            screen.getMenu().setCarried(taken);
            ClientPlayNetworking.send(new ElytraSlotPacket.Click(slotStack, false));
            cir.setReturnValue(true);
            return;
        }

        // 左键（或其他键）：交换/放入/取出
        if (carried.isEmpty()) {
            if (!slotStack.isEmpty()) {
                screen.getMenu().setCarried(slotStack.copy());
                ((ElytraSlotAccess) player).elytraslot$setStack(ItemStack.EMPTY);
                ClientPlayNetworking.send(new ElytraSlotPacket.Click(ItemStack.EMPTY, false));
                cir.setReturnValue(true);
            }
        } else if (carried.getItem() == Items.ELYTRA) {
            if (slotStack.isEmpty()) {
                ((ElytraSlotAccess) player).elytraslot$setStack(carried.copy());
                screen.getMenu().setCarried(ItemStack.EMPTY);
                ClientPlayNetworking.send(new ElytraSlotPacket.Click(carried.copy(), false));
                cir.setReturnValue(true);
            } else if (slotStack.getItem() == Items.ELYTRA) {
                ItemStack old = slotStack.copy();
                ((ElytraSlotAccess) player).elytraslot$setStack(carried.copy());
                screen.getMenu().setCarried(old);
                ClientPlayNetworking.send(new ElytraSlotPacket.Click(carried.copy(), false));
                cir.setReturnValue(true);
            }
            // 非鞘翅光标 → 不操作
        }
    }
}

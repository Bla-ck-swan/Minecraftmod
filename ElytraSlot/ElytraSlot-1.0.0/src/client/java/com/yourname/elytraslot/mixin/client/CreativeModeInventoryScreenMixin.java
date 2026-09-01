package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraCreativeSlot;
import com.yourname.elytraslot.ElytraSlot;
import com.yourname.elytraslot.ElytraSlotAccess;
import com.yourname.elytraslot.ElytraSlotPacket;
import com.yourname.elytraslot.ElytraSlotRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow private static CreativeModeTab selectedTab;

    /** 自定义鞘翅槽（仅「物品栏」标签页加入 ItemPickerMenu.slots 的真实槽位） */
    @Unique private ElytraCreativeSlot elytraslot$creativeSlot;

    /* ──── 渲染：槽位背景 + 物品/剪影。注入在 extractRenderState 的 HEAD（super 之前，
     * 即被拖动物品渲染之前），拖动的物品会盖在本槽位之上。物品本体由原版 extractSlot
     * 再画一遍（同像素，无影响），耐久条由原版绘制在最上层。 ──── */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void renderElytraSlot(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
        int sx = a.getLeftPos() + ElytraCreativeSlot.SLOT_X;
        int sy = a.getTopPos() + ElytraCreativeSlot.SLOT_Y;
        ItemStack stack = ((ElytraSlotAccess) player).elytraslot$getStack();

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        ElytraSlotRenderer.renderSlot(g, screen.getFont(), stack, sx, sy);
    }

    /* ──── Tooltip ──── */
    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void renderTooltip(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
        int sx = a.getLeftPos() + ElytraCreativeSlot.SLOT_X;
        int sy = a.getTopPos() + ElytraCreativeSlot.SLOT_Y;

        if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
            ItemStack stack = ((ElytraSlotAccess) player).elytraslot$getStack();
            if (!stack.isEmpty()) {
                CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
                g.setTooltipForNextFrame(screen.getFont(), stack, mx, my);
            }
        }
    }

    /* ──── 进入「物品栏」标签页：把自定义槽加入菜单槽列表。
     * 原版 selectTab 会动态重建 menu.slots（SlotWrapper + destroyItemSlot），
     * 我们在 TAIL 追加自己的槽，使 getHoveredSlot / 拖拽 / quickCraftToSlots 都能命中它。
     * 切换到其他标签页时原版恢复 originalSlots，本槽随之移除，无需额外清理。 ──── */
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void elytraslot$addSlotOnInventoryTab(CreativeModeTab tab, CallbackInfo ci) {
        if (selectedTab == null || selectedTab.getType() != CreativeModeTab.Type.INVENTORY) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        AbstractContainerMenu menu = ((CreativeModeInventoryScreen) (Object) this).getMenu();
        if (menu == null) return;

        if (elytraslot$creativeSlot == null) {
            elytraslot$creativeSlot = new ElytraCreativeSlot(player);
        }
        if (menu.slots.contains(elytraslot$creativeSlot)) return; // resize 重复调用保护
        elytraslot$creativeSlot.index = menu.slots.size();
        menu.slots.add(elytraslot$creativeSlot);
    }

    /* ──── 自定义槽的点击处理。
     * QUICK_CRAFT（拖拽）直接取消：本槽已通过 ItemPickerMenuMixin.canDragTo=false
     * 禁止作为拖拽目标（原版创造 INVENTORY 标签页拖拽会对槽做 SlotWrapper 强转，
     * 且单槽拖拽结束会递归 doClick(slot.index, PICKUP) 越界）。
     * 其余输入（PICKUP / QUICK_MOVE / CLONE / SWAP / PICKUP_ALL）原版同样会对本槽
     * SlotWrapper 强转而崩溃，这里自处理。 ──── */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void elytraslot$handleSlotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (slot != elytraslot$creativeSlot) return;
        // QUICK_CRAFT（拖拽）：本槽已通过 ItemPickerMenuMixin.canDragTo=false 禁止作为
        // 拖拽目标，理论上不会收到拖拽点击；万一收到直接取消，避免原版对非 SlotWrapper 强转崩溃。
        if (input == ContainerInput.QUICK_CRAFT) {
            ci.cancel();
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        AbstractContainerMenu menu = ((CreativeModeInventoryScreen) (Object) this).getMenu();
        ItemStack carried = menu.getCarried();
        ItemStack slotStack = slot.getItem();

        if (input == ContainerInput.PICKUP) {
            if (button == 0) {
                // 左键：拾取 / 放入 / 交换
                if (carried.isEmpty()) {
                    if (!slotStack.isEmpty()) {
                        menu.setCarried(slotStack.copy());
                        slot.set(ItemStack.EMPTY);
                    }
                } else if (carried.getItem() == Items.ELYTRA) {
                    if (slotStack.isEmpty()) {
                        slot.set(carried.copy());
                        menu.setCarried(ItemStack.EMPTY);
                    } else if (slotStack.getItem() == Items.ELYTRA) {
                        ItemStack old = slotStack.copy();
                        slot.set(carried.copy());
                        menu.setCarried(old);
                    }
                }
                // 光标非鞘翅且槽有物品：不操作（槽只接受鞘翅）
            } else if (button == 1) {
                // 右键：取一半 / 放置
                if (carried.isEmpty()) {
                    if (!slotStack.isEmpty()) {
                        int half = (slotStack.getCount() + 1) / 2;
                        ItemStack taken = slotStack.split(half);
                        menu.setCarried(taken);
                        slot.set(slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
                    }
                } else if (carried.getItem() == Items.ELYTRA && slotStack.isEmpty()) {
                    slot.set(carried.copy());
                    menu.setCarried(ItemStack.EMPTY);
                }
            }
        } else if (input == ContainerInput.QUICK_MOVE) {
            // Shift+点击 → 放回背包（服务端走 quickMove 分支，把物品放进服务端背包）
            if (!slotStack.isEmpty() && ElytraSlot.tryPutIntoInventory(player, slotStack.copy())) {
                ((ElytraSlotAccess) player).elytraslot$setStack(ItemStack.EMPTY);
                ClientPlayNetworking.send(new ElytraSlotPacket.Click(ItemStack.EMPTY, true));
            }
        } else if (input == ContainerInput.CLONE) {
            // 中键：复制到光标（源保留）
            if (carried.isEmpty() && !slotStack.isEmpty()) {
                menu.setCarried(slotStack.copy());
            }
        } else if (input == ContainerInput.SWAP) {
            // 数字键 / F（副手）交换
            elytraslot$handleSwap(player, button);
        } else if (input == ContainerInput.PICKUP_ALL) {
            // 双击收集：自定义槽的鞘翅进光标
            if (carried.isEmpty() && !slotStack.isEmpty()) {
                menu.setCarried(slotStack.copy());
                slot.set(ItemStack.EMPTY);
            }
        }

        ci.cancel();
    }

    /* ── 数字键 1-9 / F 交换：与快捷栏槽（0-8）或副手槽（40）互换。
     * 自定义槽只接受鞘翅：另一侧非鞘翅时忽略；另一侧为空或鞘翅时执行互换。 ── */
    @Unique
    private void elytraslot$handleSwap(Player player, int button) {
        int otherIndex;
        if (button >= 0 && button < 9) otherIndex = button;
        else if (button == 40) otherIndex = 40;
        else return;

        ElytraCreativeSlot slot = elytraslot$creativeSlot;
        if (slot == null) return;

        Inventory inv = player.getInventory();
        ItemStack other = inv.getItem(otherIndex);
        ItemStack slotStack = slot.getItem();

        if (!other.isEmpty() && other.getItem() != Items.ELYTRA) return; // 非鞘翅不能进自定义槽

        if (!other.isEmpty()) {
            // 另一侧有鞘翅：交换
            slot.set(other.copy());
            inv.setItem(otherIndex, slotStack.copy());
        } else if (!slotStack.isEmpty()) {
            // 自定义槽鞘翅 → 另一侧
            inv.setItem(otherIndex, slotStack.copy());
            slot.set(ItemStack.EMPTY);
        }

        // 创造模式：另一侧槽位通过 creative slot 包同步到服务端
        Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(inv.getItem(otherIndex), otherIndex);
    }
}

package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraSlotAccess;
import com.yourname.elytraslot.ElytraSlotRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    /* ──── 槽位位置：副手槽 (77,62) 上方一格 ──── */
    private static final int SLOT_X = 77;
    private static final int SLOT_Y = 44;

    /* 渲染槽位 + 物品/剪影。注入在 extractRenderState 的 HEAD（super 调用之前，
     * 即被拖动物品渲染之前），这样拖动的物品会盖在本槽位之上，耐久条也不会被覆盖。 */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void renderElytraSlot(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        if (!(screen.getMenu() instanceof InventoryMenu menu)) return;
        Player player = ((InventoryMenuAccessor) menu).getOwner();
        if (player == null) return;

        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
        int sx = a.getLeftPos() + SLOT_X;
        int sy = a.getTopPos() + SLOT_Y;
        ItemStack stack = ((ElytraSlotAccess) player).elytraslot$getStack();

        ElytraSlotRenderer.renderSlot(g, screen.getFont(), stack, sx, sy);
    }

    /* ──── Tooltip ──── */
    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void renderTooltip(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        if (!(screen.getMenu() instanceof InventoryMenu menu)) return;
        Player player = ((InventoryMenuAccessor) menu).getOwner();
        if (player == null) return;

        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) this;
        int sx = a.getLeftPos() + SLOT_X;
        int sy = a.getTopPos() + SLOT_Y;

        if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
            ItemStack stack = ((ElytraSlotAccess) player).elytraslot$getStack();
            if (!stack.isEmpty()) {
                g.setTooltipForNextFrame(screen.getFont(), stack, mx, my);
            }
        }
    }
}

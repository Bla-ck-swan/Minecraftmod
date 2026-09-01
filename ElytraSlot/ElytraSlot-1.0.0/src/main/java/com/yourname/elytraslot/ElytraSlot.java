package com.yourname.elytraslot;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public class ElytraSlot implements ModInitializer {
    public static final String MOD_ID = "elytraslot";

    /** 死亡暂存：dropEquipment 先存到这里，COPY_FROM 再取出恢复 */
    private static final Map<UUID, ItemStack> DEATH_STASH = new HashMap<>();

    @Override
    public void onInitialize() {
        /* ── 注册网络包（仅保留 Click，同步交给 DataWatcher） ── */
        PayloadTypeRegistry.serverboundPlay().register(ElytraSlotPacket.Click.TYPE, ElytraSlotPacket.Click.CODEC);

        /* ── 处理槽位点击 ── */
        ServerPlayNetworking.registerGlobalReceiver(ElytraSlotPacket.Click.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ItemStack cursor = payload.cursorStack();
            boolean isQuickMove = payload.isQuickMove();
            context.server().execute(() -> handleClick(player, cursor, isQuickMove));
        });

        /* ── 玩家数据复制：区分传送门(alive=true)与死亡重生(alive=false) ── */
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                // 传送门等场景：直接复制自定义槽鞘翅（物品保留）
                ItemStack elytra = ((ElytraSlotAccess) oldPlayer).elytraslot$getStack();
                if (!elytra.isEmpty()) {
                    ((ElytraSlotAccess) newPlayer).elytraslot$setStack(elytra.copy());
                }
            } else {
                // 死亡重生：仅当 keepInventory 开（死亡时暂存过）才恢复
                ItemStack elytra = DEATH_STASH.remove(oldPlayer.getUUID());
                if (elytra != null && !elytra.isEmpty()) {
                    ((ElytraSlotAccess) newPlayer).elytraslot$setStack(elytra.copy());
                }
            }
        });
    }

    private void handleClick(ServerPlayer player, ItemStack cursor, boolean quickMove) {
        ElytraSlotAccess slot = (ElytraSlotAccess) player;
        ItemStack slotStack = slot.elytraslot$getStack();
        boolean creative = player.isCreative();

        if (quickMove) {
            // Shift+点击 / 创造模式点击卸下 → 放回背包
            if (!slotStack.isEmpty() && tryPutIntoInventory(player, slotStack.copy())) {
                slot.elytraslot$setStack(ItemStack.EMPTY);
            }
            return;
        }

        // 正常点击：交替交换
        if (creative) {
            /* 创造模式：光标物品是纯客户端状态（ItemPickerMenu 转发到客户端 InventoryMenu），
             * 服务端只维护自定义槽内容即可——包里的 cursor 即「槽位的新内容」：
             * 空 → 槽清空（鞘翅在客户端光标上）；鞘翅 → 槽=光标（交换时旧的留在客户端光标）。
             * 不碰 player.containerMenu.carried，避免服务端同步回去覆盖客户端的创造光标。 */
            if (cursor.isEmpty()) {
                if (!slotStack.isEmpty()) {
                    slot.elytraslot$setStack(ItemStack.EMPTY);
                }
            } else if (cursor.getItem() == Items.ELYTRA) {
                slot.elytraslot$setStack(cursor.copy());
            }
            return;
        }

        if (cursor.isEmpty()) {
            if (!slotStack.isEmpty()) {
                player.containerMenu.setCarried(slotStack.copy());
                slot.elytraslot$setStack(ItemStack.EMPTY);
            }
        } else if (cursor.getItem() == Items.ELYTRA) {
            if (slotStack.isEmpty()) {
                slot.elytraslot$setStack(cursor.copy());
                player.containerMenu.setCarried(ItemStack.EMPTY);
            } else if (slotStack.getItem() == Items.ELYTRA) {
                ItemStack old = slotStack.copy();
                slot.elytraslot$setStack(cursor.copy());
                player.containerMenu.setCarried(old);
            }
            // 光标不是鞘翅且槽有物品：不操作（槽只接受鞘翅）
        }
    }

    /* ── 优先放入背包（Inventory 槽位 9..35），背包满再放 hotbar（0..8）。
     *    与原版护甲槽 shift+点击的 moveItemStackTo(stack, 9, 45, false) 行为一致。 ── */
    public static boolean tryPutIntoInventory(Player player, ItemStack stack) {
        Inventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return true;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return true;
            }
        }
        return false;
    }

    /* ── 死亡前暂存（由 LivingEntityMixin.dropEquipment 调用） ── */
    public static void stashForDeath(ServerPlayer player) {
        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (!elytra.isEmpty()) {
            DEATH_STASH.put(player.getUUID(), elytra.copy());
        }
    }
}

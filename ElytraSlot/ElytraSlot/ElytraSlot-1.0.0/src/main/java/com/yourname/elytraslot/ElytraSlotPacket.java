package com.yourname.elytraslot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class ElytraSlotPacket {

    /** 客户端→服务端：点击鞘翅槽 */
    public record Click(ItemStack cursorStack, boolean isQuickMove) implements CustomPacketPayload {
        public static final Type<Click> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ElytraSlot.MOD_ID, "slot_click"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = StreamCodec.of(
            (buf, p) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.cursorStack);
                buf.writeBoolean(p.isQuickMove);
            },
            buf -> new Click(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readBoolean()
            )
        );

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /*
     * 注意：鞘翅槽的数据同步已完全交给 DataWatcher（SynchedEntityData），
     * 它会自动同步给所有追踪该玩家的客户端（自己 + 队友），无需手动 Sync 包。
     * 因此这里不再有 Sync / RequestSync 两个包，避免与 DataWatcher 双轨同步冲突。
     */
}

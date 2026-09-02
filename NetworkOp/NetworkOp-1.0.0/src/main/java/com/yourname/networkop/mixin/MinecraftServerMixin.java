package com.yourname.networkop.mixin;

import com.yourname.networkop.NetworkOpConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * zstd接管压缩：自动禁用原版 zlib压缩（仅 zstd=true 时，仅服务端加载）
 *
 * 原理：26.2 的 Connection.setupCompression(int threshold, boolean) 开头有
 *  "threshold < 0 → 直接return"（不添加 compress/decompress handler），
 * 这正是原版 network-compression-threshold=-1 的官方禁用路径。
 * ServerCommonPacketListenerImpl 构造和发给客户端的压缩配置都从
 * MinecraftServer.getCompressionThreshold() 取值——这里改成 -1，
 * 服务端不配压缩、客户端跟随服务器也不配 → 双端裸发 → 自洽。
 *
 * 效果：
 *  - zstd=true：原版 zlib 完全禁用，攒批帧 = 原始编码字节，
 *    BatchBuffer 检测到 pipeline 无 CompressionEncoder 自动走"原始数据"路径，
 *    zstd 直接压原始（实测 ~12%），玩家无需改 server.properties
 *  - zstd=false：返回原值，原版 zlib 照常工作（模组零干预）
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "getCompressionThreshold", at = @At("HEAD"), cancellable = true)
    private void networkop$forceDisableVanillaCompression(CallbackInfoReturnable<Integer> cir) {
        if (NetworkOpConfig.zstd) {
            cir.setReturnValue(-1); // 模拟 network-compression-threshold=-1
        }
    }
}

package com.yourname.elytraslot;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** 鞘翅槽的渲染工具：槽位背景 + 物品/剪影 + tooltip 判断。生存/创造界面共用。 */
public final class ElytraSlotRenderer {

    private ElytraSlotRenderer() {}

    /** 渲染槽位：原版立体背景 + 物品（带耐久条）或空槽剪影 */
    public static void renderSlot(GuiGraphicsExtractor g, Font font, ItemStack stack, int sx, int sy) {
        // 原版槽位背景（18×18，含 1px 边框）：左上深灰、右下白、内部浅灰的立体凹陷效果
        int dark  = 0xFF373737;  // 深灰（左上边框 + 左上角）
        int white = 0xFFFFFFFF;  // 白  （右下边框 + 右下角）
        int light = 0xFF8B8B8B;  // 浅灰（内部 + 右上角 + 左下角）

        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, light);   // 主体先全涂浅灰
        g.fill(sx - 1, sy - 1, sx + 16, sy, dark);         // 上边框深灰
        g.fill(sx - 1, sy - 1, sx, sy + 16, dark);         // 左边框深灰
        g.fill(sx + 16, sy, sx + 17, sy + 17, white);      // 右边框白
        g.fill(sx, sy + 16, sx + 17, sy + 17, white);      // 下边框白

        if (stack.isEmpty()) {
            drawElytraSilhouette(g, sx, sy);
        } else {
            g.item(stack, sx, sy);
            g.itemDecorations(font, stack, sx, sy);
        }
    }

    /* ──── 鞘翅剪影（16x16 线框轮廓，加深色与头盔剪影一致） ──── */
    private static void drawElytraSilhouette(GuiGraphicsExtractor g, int sx, int sy) {
        int dark = 0xFF5F5F5F;   // 剪影色（淡一档，比原 3F3F3F 浅）

        // 16x16 掩码：1 = 加深，0 = 保持槽位背景浅灰
        int[][] m = {
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // y=0
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // y=1
            {0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0}, // y=2
            {0,0,1,1,0,0,1,1,1,1,0,0,1,1,0,0}, // y=3
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=4
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=5
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=6
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=7
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=8
            {0,1,0,0,0,0,1,0,0,1,0,0,0,0,1,0}, // y=9
            {0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0}, // y=10
            {0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0}, // y=11
            {0,0,0,1,0,1,0,0,0,0,1,0,1,0,0,0}, // y=12
            {0,0,0,1,1,0,0,0,0,0,0,1,1,0,0,0}, // y=13
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // y=14
            {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // y=15
        };

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (m[y][x] == 1) {
                    g.fill(sx + x, sy + y, sx + x + 1, sy + y + 1, dark);
                }
            }
        }
    }
}

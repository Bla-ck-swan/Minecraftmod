package com.yourname.elytraslot;

import net.fabricmc.api.ClientModInitializer;

public class ElytraSlotClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        /*
         * 鞘翅槽的数据同步完全由 DataWatcher（SynchedEntityData）处理，
         * 它会自动同步给所有视角（自己 + 队友），无需任何客户端网络处理。
         */
    }
}

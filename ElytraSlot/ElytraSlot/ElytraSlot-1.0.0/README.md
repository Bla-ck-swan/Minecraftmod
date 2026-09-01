# ElytraSlot — 鞘翅占位槽

在物品栏添加一个**鞘翅专用槽位**，让你可以穿着胸甲的同时使用鞘翅飞行。

> **适用版本**: Minecraft26.2 (Fabric)  
> **安装方式**: 客户端 + 服务端双端安装（单人可用，直接放 mods 即可）

## 功能

- 🔹 物品栏界面新增一个鞘翅槽位（位于护甲槽旁边）
- 🔹 槽位**仅接受鞘翅**，拒绝其他物品
- 🔹 鞘翅放入槽位后，即使穿着胸甲也能正常飞行
- 🔹 飞行时耐久消耗作用于槽位中的鞘翅
- 🔹 物品保存在玩家数据中，退出重进不丢失

## 构建

### 前提条件

- JDK 21+
- 已构建过一次 ModWhitelist（会自动配置好 Gradle Wrapper 和依赖缓存）

### 构建步骤（示例）

1. 打开终端，进入项目目录：
```cmd
cd "D:\我的世界\ElytraSlot"
```

2. 运行构建：
```cmd
.\gradlew.bat build
```

3. 构建产物位于 `build/libs/elytraslot-1.0.0.jar`

4. 或者直接运行一键脚本：
```cmd
build.bat
```

### 部署（示例）

构建完成后运行：
```powershell
.\deploy.ps1
```
会自动复制 jar 到服务端和客户端 mods 文件夹。

或手动复制：
- 服务端：`D:\我的世界\MinecraftServer\mods\`
- 客户端：`D:\我的世界\26.2-Fabric\mods\`
- 单人用：只放客户端 mods 文件夹即可

## 使用说明

1. 安装模组后进入游戏
2. 按 E 打开物品栏
3. 在副手槽上方会看到一个带鞘翅图标的专用槽位
4. 将鞘翅拖入该槽位（或左键点击放入）
5. 穿上胸甲，跳起来按空格即可飞行！

## 技术架构

| 组件 | 说明 |
|------|------|
| `ElytraSlot.java` | 服务端主入口，注册网络包和处理器 |
| `ElytraSlotClient.java` | 客户端入口，接收同步 |
| `ElytraSlotAccess.java` | 数据访问接口 |
| `ElytraSlotPacket.java` | 网络包：Click / Sync / RequestSync |
| `PlayerEntityMixin.java` | 注入鞘翅槽位 NBT 存储 |
| `LivingEntityMixin.java` | 修改飞行检测和耐久消耗逻辑 |
| `InventoryScreenMixin.java` | 在物品栏 GUI 渲染槽位并处理点击 |

## 调试

如果构建失败：
- **方法找不到**: MC26.2 的 API 可能与其他版本不同，请根据编译错误调整 Mixin 中的 `method = "..."` 参数
- **Gradle Wrapper 缺失**: 运行 `build.bat` 会自动从 ModWhitelist 复制
- **JDK 版本**: 需要 JDK 21+，确保 JAVA_HOME 正确设置

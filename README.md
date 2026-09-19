# Create: MaxCraft

面向 **NeoForge 1.21.1** 的 **Create 6.0.10**（机械动力）附属模组（`net.neoforged.moddev` 2.0.78、
NeoForge 21.1.248、Parchment 2024.11.17、Java 21）。它拆掉了 Create 在包裹、工厂仪表面板和动力合成器阵列上的
硬性上限：多大的包裹都能装、多大的合成网格都能用，机器也能把一整批原料按正确的形状铺进合成器阵列。

**mod id 保持 `maxcraft`**，mod 列表里显示的名字是 **Create: MaxCraft**。

## 依赖

| 模组 | 关系 | 来源 |
| --- | --- | --- |
| Create 6.0.10-231（机械动力） | **必需** | Gradle 依赖，来自 `maven.createmod.net` |
| Create: Cyber Goggles 1.21.1-8.6.1（机械动力：赛博护目镜） | **可选**（软依赖），仅客户端 | `run/mods/` 里的 jar |

Create 自己的库（Flywheel、Ponder、Catnip、Registrate、Vanillin）是随它传入的，属于 Create 而非额外模组。

### 仅供开发运行的模组（不是构建依赖）

只放在 `run/mods/`，不在编译类路径、也不在任何 Gradle 配置里，随时可以删：

| 模组 | 中文名 | 版本 |
| --- | --- | --- |
| Just Enough Items | JEI | 19.51.0.418 |
| Just Enough Characters | 通用拼音搜索 | 4.5.29 |
| Sodium | — | 0.8.13+mc1.21.1 |
| Lithium | — | 0.15.4+mc1.21.1 |

Sodium、JEI、通用拼音搜索都是纯客户端模组，所以**服务端单独使用游戏目录** `run-server/`。

## 新增内容

| 内容 | 制作方式 |
| --- | --- |
| 大型包裹构件 | **序列组装**：坚固板 → 用精密构件部署 → 冲压 |
| 大型打包机 | 打包机 + 构件；或手持构件右键打包机 |
| 大型理包机 | 理包机 + 构件；或手持构件右键理包机 |
| 扩展仓储发报机 | 仓储发报机 + 构件；或手持构件右键仓储发报机 |
| 扩展工厂仪表 | 工厂仪表 + 构件（它是一个**物品**：Create 的仪表带着自己的网格尺寸） |

大型打包机与大型理包机还能在工作台上互相转换，对应 Create 自己的 `repackager_from_conversion` 配方。
原地升级会整份搬运方块实体的 NBT，所以物流网络、设置和夹着的包裹都不会丢。

## 包裹系统

Create 把一个包裹限制在 9 组，并用原版 `ItemContainerContents`（上限 256 格）存内容。本模组把这两个上限都
拿掉了，同时**不动请求/打包的限制**：

| 文件 | 作用 |
| --- | --- |
| `logistics/PackageContents.java` | 任意大小的包裹内容读写，并保留一份可读的镜像 |
| `registry/MaxcraftDataComponents.java` | `maxcraft:package_bulk_contents`，不限长度的 `List<ItemStack>` |
| `mixin/PackageItemMixin.java` | `getContents` / `containing` 走尺寸无关的实现 |
| `mixin/PackageRepackageHelperMixin.java` | 合并后每个订单**只有一个**包裹，无论多少组 |
| `mixin/RepackagerBlockEntityMixin.java` | 理包机先把整个库存收齐再合并 |
| `mixin/PackagerBlockEntityMixin.java` | 大型打包机每个周期装 `maxPackageStacks` 组 |

**任何情况下都不吞物品**：解包、掉落、锯开、合并全都走打过补丁的 `getContents`，内容完整传递。

### 投递与拾取

| 路径 | 行为 |
| --- | --- |
| 大型打包机 → 动力合成器 | 由 pattern 决定摆法（见下） |
| 大型理包机 | 等**整个**订单到齐，合并成**一个**包裹 |
| 挖掉有 ≥2 个面板的仪表 | 拆掉一个面板，掉落扩展仪表物品 |
| 扳手拆面板 | 同上 |
| 只剩一个面板时挖掉整块 | 走战利品表掉落，同样是扩展仪表 |

掉落/拆下来的扩展仪表是**光板**：只带尺寸和升级标记，别的什么都没有。和刚从工作台拿到的仪表一样，必须先用
它右键仓储发报机绑定网络才能放置。

## 扩展工厂仪表

就是 Create 的仪表，只不过**合成网格尺寸存在面板自己的数据里** —— 没有第二个方块。

* **每格独立。** 一块仪表的四个面板是共用方块的四个机器，各自记自己的尺寸；升级其中一个永远不会动到另外三个。
* **升级方式。** 手持大型包裹构件右键某个面板，或使用扩展仪表物品。**已经有面板的格子会直接拒绝** —— 不改尺寸、
  也不消耗物品。
* **物品形态。** 扩展仪表物品只带**一个尺寸**（`maxcraft:gauge_grid_sizes`，单值），所以同尺寸的仪表就是同一个物品、
  可以堆叠；尺寸具体落到哪一格是**使用时**决定的。尺寸放在本模组自己的数据组件里，Create 的调谐和 tag 重写都碰不到它。
* **世界内显示。** 使用大网格的面板会画成黄铜色。面板本体是 Create 自己的动态模型，所以模型数据里带上"哪些格子是
  扩展的"，再用 Create 模型的黄铜副本摆放 —— 形状和位置仍然是 Create 的。
* **面板界面。** 合成模式下显示**材料列表**（超过 9 个用 ‹ › 翻页），鼠标停在上面会弹出**配方自身形状**的预览，
  而不再是补出来的整块大网格。普通仪表的样子和行为完全没变。

## 更大的配方

* **Pattern。** 比面板网格小的配方会铺在网格的**左上角**、其余是空格，这样机器收到的是"它自己尺寸"的 pattern；
  装不下的配方保持自己的形状，不被压扁。
* **排布。** Create 是按行把合成器交给我们的；本模组从阵列本身读出"一行有几台"，再按这个宽度排版，所以 10×10 的
  配方落到 12×12 的阵列上是一个正方的 10×10 块，而不是错位。
* **批量。** 一个装了"好几批原料"的包裹，会按轮次往 pattern 要求的那些格子里填（每轮每格一个），多余的原料平均铺开，
  而不是全塞进第一台合成器。

## 配置

`maxcraft-server.toml`（单人游戏在 `<存档>/serverconfig/`，专用服务器在 `config/`）：

```toml
# 单个包裹最多能装多少组。大型打包机一次装这么多组，理包机合并出来的包裹也最多这么大。
maxPackageStacks = 64
```

## 常用命令

本项目使用系统 Gradle 9.7.0（和隔壁 `../m8s` 用同一个），没有 wrapper。

```bash
gradle build          # 编译 + 打包
gradle runClient      # 启动客户端（需要 DISPLAY），游戏目录：run/
gradle runServer      # 启动专用服务器（不加载纯客户端模组），游戏目录：run-server/
gradle runData        # 运行数据生成器
```

在没有完整桌面会话的环境里启动客户端需要带上会话变量，例如：

```bash
DISPLAY=:1 WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000 \
  XAUTHORITY=/run/user/1000/xauth_XXXXXX gradle runClient
```

## 自测

`PackageSystemSelfTest` 会在真实服务端里跑一遍补丁：包裹存储往返、理包机合并、逐面板的网格尺寸、
每一条拾取与放置路径，以及 pattern 的排布：

```bash
MAXCRAFT_SELFTEST=1 gradle runServer
```

它会打印 `SELFTEST PASSED` / `SELFTEST FAILED` 和检查项数量，然后停服。不带这个环境变量时完全不生效。

## 目录结构

```
build.gradle                                 moddev 2.0.78 + Create 依赖
gradle.properties                            版本（NeoForge 21.1.248、Create 6.0.10-231 等）
src/main/java/dev/maxcraft/Maxcraft.java     入口（@Mod("maxcraft")）
src/main/java/dev/maxcraft/content/          本模组新增的机器
src/main/java/dev/maxcraft/logistics/        包裹内容、pattern 排布、尺寸存取
src/main/java/dev/maxcraft/mixin/            对 Create 的补丁
src/main/resources/maxcraft.mixins.json      mixin 配置
run/                                         客户端开发目录（存档、日志、配置、mods）
run-server/                                  专用服务器目录
```

Gradle / NeoForge / Minecraft 的构建缓存与 `../m8s` 共用 `~/.gradle`。

## 许可

**GNU Lesser General Public License v3.0 or later**，见 `LICENSE`（FSF 发布的官方全文）。
mod 列表里显示的许可来自 `gradle.properties` 的 `mod_license`。

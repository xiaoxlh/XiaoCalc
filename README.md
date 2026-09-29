# XiaoCalc 小计算

面向 **安卓圆屏幕手表** 的离线计算器。Jetpack Compose + Material 3，单 APK、零网络权限。

**当前状态**：Kotlin 2.0.21 / Compose BOM 2024.09.03 / AGP 8.2.2；
74 个用例全部通过（含 13 个渲染截图）；release APK 1.58 MB（R8 + 资源压缩）；
编译无告警。

---

## 快速开始

```bash
# 构建
./gradlew :app:assembleDebug

# 单元测试（解析器 / 格式化 / 圆屏几何 / 输入状态机，纯 JVM）
./gradlew :app:testDebugUnitTest

# 安装到已连接的手表
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 无设备 UI 截图（layoutlib 真实渲染，产物在 app/src/test/snapshots/）
./gradlew :app:recordPaparazziDebug    # 重新录制
./gradlew :app:verifyPaparazziDebug    # 与基线比对，可接入 CI
```

需要 JDK 17。`local.properties` 里的 `sdk.dir` 指向 Android SDK。

---

## 截图复核

本项目没有可用的手表/模拟器，因此用 **Paparazzi + layoutlib 在 JVM 上真实渲染 Compose**，
按用户实机参数（480×480 @ 320dpi → 240dp 圆屏）产出 PNG：

| 快照 | 覆盖点 |
|---|---|
| `calculator_idle` / `calculator_result` / `calculator_long_result` | 空态、正常结果、超长表达式自动缩字号与科学计数法 |
| `calculator_function_page` | 函数页 16 键全部在圆内 |
| `calculator_error` | 错误态文案与配色 |
| `history` / `history_empty` | 列表、空态、弧线滚动条 |
| `settings` | 卡片、MD3 开关、分组 |
| `welcome_intro` | 引导页构图与圆形主按钮 |
| `calculator_square` / `settings_square` | 方形屏分支 |

这些截图**不是装饰**：本次修复的 6 个界面缺陷（千分位逗号被裁成圆点、时钟被裁成 `10:2`、
`C` 键因默认内容色为黑而不可见、主按钮被挤成椭圆、副标题与按钮重叠、方形屏布局溢出）
全部是先由截图发现、再回头改代码的。

`app/src/test/snapshots/images/` 已入库，可直接作为视觉回归基线；
`verifyPaparazziDebug` 会在渲染结果偏离基线时失败。
注意 layoutlib 的渲染在不同平台/版本间可能有细微差异，换机后需要重新录制。

### 构建脚本里的两处"非显然"配置

- `app.cash.paparazzi`：Paparazzi 1.3.3 与 AGP 8.2.x 匹配；它对 Gradle 版本不挑。
- `constraints { testImplementation("com.google.guava:guava") { attributes { … STANDARD_JVM } } }`：
  layoutlib 需要 Guava 的标准 JVM 变体，而 Android 工程默认解析到 `-android` 变体，
  缺少 `Sets#toImmutableEnumSet` 会抛 `IllegalAccessError`。这是 Paparazzi 官方 changelog
  记录的已知问题，约束只作用于 `testImplementation`，不影响 APK 依赖。

---

## 实机验证

在真机（小米 **M2505W1 / grasslte**，Android 14 / SDK 34）上完成过一轮完整验证，
证据留档于 `docs/device-validation/`。

实机屏幕参数与设计假设**逐项吻合**：

| 项 | 实机值 | 设计假设 |
|---|---|---|
| 分辨率 | 480 × 480 | 480 × 480 |
| 密度 | 320 dpi | 320 dpi |
| 逻辑尺寸 | 240 dp | 240 dp |
| 圆屏 | `FLAG_ROUND`，`radius=240px, center=(240,240)` | `Disc.RADIUS = 192du`（240px ÷ 1.25 = 192du） |

`docs/device-validation/before_calculator.png` 是**改造前的真实截图**：
`^`、`C`、`(`、`)`、`⌫`、`√`、`-` 这些边角键被圆形表盘切掉——
这正是"逐页魔数内收"无法根治的问题；`after_*.png` 是同一台设备上的新版。

### 升级安装验证（版本 1.0.1 → 1.1.0，原地升级）

| 检查项 | 结果 |
|---|---|
| 引导标记 `welcome_completed` | 保留，启动**直接进计算器**，未重弹欢迎页 |
| `root` / `scientific` / `symbols` | 三项全部保留（截图对照旧版设置页） |
| 新增项 | `group_digits` 取默认 ON，`angle_unit` / `haptics` 取默认值 |
| 计算功能 | 键盘输入 `12+3=` → `15`；用户实算 `9999999÷6666` → `1,500.149865` |
| 崩溃 | `logcat` 无 `AndroidRuntime` / `FATAL` 记录 |

### 实机验证发现并修掉的一个真 bug

`docs/device-validation/` 之外，这轮实机检查还暴露了**我自己引入的迁移缺陷**：

旧版的引导标记存在 `MainActivity.xml` 里，而我在 `Stores.kt` 中写成了读
`MainActivityPreferences` —— 文件名错误，会让老用户升级后被重新弹一次欢迎页。

原因是 `Activity.getPreferences(MODE_PRIVATE)` 等价于
`getSharedPreferences(getLocalClassName(), MODE_PRIVATE)`，而 `getLocalClassName()`
返回的是**去掉包名的类名**（`MainActivity`），不是类名加 "Preferences"。
这个文件名是在真机上 `run-as` 读取 `/data/data/.../shared_prefs/` 时确认的
（目录里实际是 `MainActivity.xml` 与 `calculator_settings.xml`），代码里已补上说明。

**结论：凡是"靠命名约定猜出来的"兼容逻辑，都必须拿真机的实际文件核对一遍。**

---

## 架构

```
app/src/main/java/com/example/xiaocalc/
├── MainActivity.kt            单 Activity：全屏沉浸 + 页面切换动画 + 时钟
│
├── adaptive/                  屏幕几何层（纯 Kotlin，可单测）
│   ├── Disc.kt                圆盘弦长数学：chord / chordInBand / inscribedSquare
│   ├── WatchLayout.kt         把表盘切成 header / display / keypad / indicator / content 五段
│   └── WatchFrame.kt          根容器：形状探测、设计单位换算、CompositionLocal 注入
│
├── design/                    Material 3 设计系统
│   ├── Color.kt               暗色方案（品牌蓝 #2C56FF / 红 #FF5454 映射到 MD3 角色）
│   ├── Type.kt                MiSans 静态四字重 + 数字排版特性
│   ├── Motion.kt              M3 动效令牌（emphasized / standard 曲线与时长档位）
│   └── Theme.kt               主题装配 + 系统字号缩放上限
│
├── calc/                      计算内核（纯 Kotlin，无 Android 依赖，可单测）
│   ├── Expression.kt          词法分析 + 递归下降求值器
│   ├── CalcEngine.kt          求值入口 + 括号自动补全
│   ├── NumberFormatter.kt     精度 / 千分位 / 科学计数法 / 根号与符号化
│   ├── CalcError.kt           强类型错误枚举
│   ├── CalcKey.kt             按键模型 + 两页键盘布局（数据驱动）
│   ├── CalcSettings.kt        设置项
│   ├── CalculatorState.kt     输入状态机（前导零、小数点、运算符替换、退格…）
│   ├── HistoryEntry.kt        结构化历史 + 无依赖编解码
│   └── Stores.kt              设置/历史/引导的持久化（含内存实现）
│
└── ui/                        Compose 界面
    ├── AppPage.kt             页面枚举
    ├── CalculatorScreen.kt    计算器主页面
    ├── HistoryScreen.kt       历史记录
    ├── SettingsScreen.kt      设置
    ├── WelcomeScreen.kt       首次运行引导（两步）
    └── components/
        ├── AutoSizeText.kt          按可用宽度二分缩字号
        ├── CalcDisplay.kt           时钟 / 表达式 / 结果
        ├── Keypad.kt                键盘（逐行贴合弦长 + 分页切换 + 按压缩放 + 触感）
        ├── ActionBar.kt             底部操作行：C / ⌫ / 123 / fx
        ├── SettingRow.kt            设置卡片 + MD3 Switch
        ├── WatchCommon.kt           顶栏、圆形图标/文字按钮、触感
        ├── WatchScrollIndicator.kt  圆屏弧形滚动指示
        └── Icons.kt                 自绘矢量图标（退格键）
```

**分层原则**：`calc` 与 `adaptive` 不依赖 Android/Compose 运行时之外的任何东西，
因此核心逻辑与圆屏几何可以在 JVM 上完整回归；`ui` 只做渲染与事件转发。

---

## 圆屏适配：为什么不再需要逐页写魔数

### 旧做法的问题

旧实现把屏幕短边映射为 384 的方形画布，圆形屏再整体缩到 0.95，然后**逐页内收**：

```kotlin
// 旧代码里真实存在的写法
.padding(horizontal = scaled(if (round) 62f else 16f))   // 设置页
.padding(horizontal = scaled(if (round) 54f else 16f))   // 历史页
.padding(end = scaled(if (round) 108f else 6f))          // 菜单
.padding(start = scaled(if (round) 100f else 40f))       // 函数弹窗
.offset(x = scaled(if (round) -28f else 0f))             // 返回键
```

这些数字是靠截图反复试出来的，彼此不一致，且**无法保证**内容不出圆——
键盘四角仍然是按矩形铺满的，旧代码注释里也承认"角键轻微出圆可接受"。

### 现在的做法

把"能不能放下"变成一条可验证的不等式。圆上距顶端 `y` 处的可用宽度（弦长）是解析的：

```kotlin
chord(y) = 2·√(R² − (y − R)²)          // R = 192，圆心在 y = 192
```

`Disc.chordInBand(top, bottom)` 返回一段纵向区间内**处处可用**的最窄弦长。
`WatchLayout` 用它在布局期推导每一段的最大宽度，键盘的按键边长取
"高度放得下"与"宽度放得下"的较小值——于是**结构上不可能出圆**。

`DiscTest` 把这个不变量固化成测试：对每个分区的四个角断言
`(maxWidth/2)² + (y − R)² ≤ R²`。同时保留一条反向断言，
证明"满宽矩形在圆屏上必然切角"，以说明魔数内收为何是必然的（也是不必要的）。

### 与旧做法相关的其它改动

| 项 | 旧 | 新 |
|---|---|---|
| 圆形屏 | 整体缩 0.95 再逐页内收 | 1:1，宽度全部交给弦长约束 |
| 系统栏 inset | 圆屏也用矩形 inset，把圆心挤偏 | 圆屏不叠加 inset（内接区本就远离表盘边缘） |
| 键盘 | 7 列 × 44u，四角出圆 | 4 列，**逐行贴合弦长**，整块落在圆内 |
| `C` / `⌫` | 挤在 7 列键盘里 | 下移到表盘底部的操作行，与分页 `123` / `fx` 同排 |
| 形状判断 | 散落在每个页面 | 只在 `WatchLayout.of(round)` 一处 |

---

## 本次重构修掉的问题

### 功能缺陷（有回归测试覆盖）

1. **`deg` 键完全不可用**——它往表达式里插入字符串 `deg`，而解析器读到未知函数名后直接抛错，
   表现为按一下就显示"错误"。现在角度单位是**显式设置项**，不再兼职。
2. **旧版用"保留特殊符号"开关控制三角函数是角度还是弧度**——两个无关概念被耦合，
   导致开关一开，`sin(30)` 的结果就变了。现已拆分为独立的"弧度制"开关。
3. **出错状态会污染后续运算**——失败被写进 `result` 字符串（`"错误"` / `"NaN"`），
   接着按运算符时这个字符串会被当成有效操作数回填。现在失败是 `CalcError` 枚举。
4. **续算靠反解析显示串**——结果带千分位逗号与上标指数（`1.2345×10⁹`），
   旧实现把显示串去掉逗号后塞回表达式。现在用**数值**续算。
5. **`÷ 0` 返回 `Infinity`**，阶乘/开方无定义域校验。现在分别给出明确错误。
6. **历史记录只存在内存里**，退出即清空，且存的是拼好的字符串。
   现在结构化 + SharedPreferences 持久化，点击条目可回填表达式。
7. **前导零叠加**：按 `0` 再按 `5` 得到 `05`。
8. **连续按运算符被静默忽略**，没有任何反馈（现在替换上一个运算符）。
9. **结果精度损失**：`"%.6f"` 截断。现在用 `BigDecimal` 做 10 位有效数字舍入。
10. **`⌫` 字形缺失**：MiSans 不含 U+232B，旧版靠系统字体回退"碰巧"显示。
    现在退格键是自绘矢量，并加了字体字形覆盖校验（见下）。

### 界面问题（截图复核发现）

11. **副标题与下一步按钮重叠**——旧版用 `offset(y = -16)` 硬顶，没有布局约束。
12. **函数弹窗盖住键盘**，且弹窗区域与按键命中区重叠。
13. **按钮/图标用 `LocalContentColor` 默认值**（在无 `Surface` 的树下是黑色），
    在纯黑背景上不可见。
14. **千分位逗号被裁成圆点**：`horizontalScroll` 会裁剪到容器边界，
    而显示区行高小于 MiSans 的真实行高（1.326em），逗号的下伸部分被切掉，
    视觉上与小数点无法区分。
15. **时钟被裁成 `10:2`**：固定宽度放不下 `88:88`。
16. **滚动条与内容脱节**：旧版在收缩过的内容盒里画弧，却用未收缩的表盘半径算半径。

### 工程卫生

17. 仓库根目录堆了 47 张截图、8 份构建日志、31 个一次性测量脚本、25MB 字体源文件
    → 全部清理出库，并补全 `.gitignore`。
18. 未引用的 drawable（3 个矢量 + 1 个 png）已删除。
19. Kotlin 1.9.22 / Compose 1.6.7 → **Kotlin 2.0.21 + Compose BOM 2024.09.03**
    （material3 1.3.0），并用上 `kotlin.plugin.compose` 插件。

---

## 字体子集化

`tools/subset_font.py` 扫描源码里的字符串字面量得到"UI 实际会渲染的字符"，
再用 fontTools 裁掉其余字形：

```
misans_regular.ttf: 7932.0 KB -> 99.4 KB (缩减 98.7%)
misans_medium.ttf:  7913.9 KB -> 99.5 KB (缩减 98.7%)
misans_bold.ttf:    7821.6 KB -> 98.6 KB (缩减 98.7%)
misans_heavy.ttf:   7793.3 KB -> 98.5 KB (缩减 98.7%)
```

脚本还会**回读产物 cmap 逐字校验**，缺字直接以非零状态退出——
`⌫` 缺失就是这样被发现的（见问题 10）。

```bash
py -B tools/subset_font.py          # 生成 + 校验
py -B tools/subset_font.py --check  # 只校验
```

---

## 数据兼容

设置文件与 key 沿用旧版，升级不丢配置：

| key | 文件 | 说明 |
|---|---|---|
| `root` | `calculator_settings` | 保留根号 |
| `scientific` | `calculator_settings` | 科学计数法 |
| `symbols` | `calculator_settings` | 保留特殊符号 |
| `welcome_completed` | `MainActivityPreferences` | 首次引导已完成 |
| `angle_unit` / `group_digits` / `haptics` | `calculator_settings` | 本次新增，有默认值 |
| `entries` | `calculator_history` | 历史记录（新增，旧版无持久化） |

历史记录格式是自定义的行编码（不引 JSON 库以控制包体），对分隔符与反斜杠做转义。

---

## 键盘：逐行贴合弦长

第一版把整块键盘塞进一个**统一宽度的正方形**——圆屏上正方形受内接约束（R·√2），
于是每一行都被最窄的那一行拖累，左右留下大片空白，键盘缩成中间一小块，
观感上"没有利用圆"。真机截图确认后改为**逐行贴合弦长**：

```
每一行的宽度 = Disc.chordInBand(该行 top, 该行 bottom) × 0.96
每一行的键宽 = (该行宽度 − (列数−1) × 键距) / 列数
```

结果是一块贴合圆形的梯形键盘：中间两行最宽（约 366du）、向下逐行收窄（约 300du），
两侧空白被真正利用起来。数值上由 `DiscTest` 固化：

- 每一行四角必须落在圆内（`x² + (y−R)² ≤ R²`）；
- 每一行的填充率 ≥ 90%（"尽量铺满"是可断言的，不是感觉）；
- 键宽/行宽自洽，行与行不重叠。

键面因此不再是正方形，圆角改为**按短边的百分比**取——
原来的圆形裁剪会把宽键压成椭圆。

`C` / `⌫` 从顶栏下移到表盘底部的操作行，与分页胶囊 `123` / `fx` 同排（4 个等宽项）。
顶栏只剩**居中的时钟**与右侧溢出菜单：顶栏位于表盘最顶端，y=8 处弦长仅约 105du，
居中的时钟右侧只剩约 26du，放不下菜单命中区——这也是顶栏从 y=8 下移到 y=16 的原因。

---

## 已知取舍

- **按键偏宽偏矮**：逐行铺满意味着键宽由该行弦长决定（约 71–88du），
  而键高被纵向预算压到约 39du。触摸面积充足，但观感是横向的"宽键"而非方键。
  若要更方的键，只能牺牲结果字号或时钟行高。
- **键盘固定 4 列**：`(` `)` 等仍放在函数页。表盘再大也不增加列数，
  保持一致的肌肉记忆。
- **仅保留重构后的实现**：重构前的源码与当时的规划文档未随仓库发布，
  旧做法的问题已在上一节逐条说明。

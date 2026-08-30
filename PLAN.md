# 墨水屏悬浮球（InkFloatBall）实施计划

> 状态：**已实施**。

## 1. 需求边界（不扩展）

| 功能 | 实现手段 |
|---|---|
| 上一页 | AccessibilityService.dispatchGesture 派发点击/滑动 |
| 下一页 | 同上 |
| 返回桌面 | performGlobalAction(GLOBAL_ACTION_HOME) |
| 打开常用 app | PackageManager 启动 LAUNCHER Intent（可配 1~6 个） |
| 设置入口 | 必需，否则无法配置翻页参数与常用 app |

**明确不做**：返回键、最近任务、截屏、亮度调节、手势自定义脚本。

## 2. 已确认的设计决策

- 目标设备：**多台混用**，走最保守兜底路线（悬浮窗类型降级 + 前台服务保活 + 全参数可调）
- 菜单形态：**竖直条**（沿屏幕边缘展开一列大方块按钮，墨水屏局部刷新面积最小）
- 默认翻页动作：**点击屏幕左右区域**（左 20% / 右 80%，纵向 50%），滑动方式在设置中可切换

## 3. 技术选型

| 项 | 值 | 理由 |
|---|---|---|
| 语言 | Kotlin | — |
| minSdk | 24 | dispatchGesture 起始 API；覆盖老电纸书 |
| targetSdk | 30 | 精确匹配 Android 11 行为，避免 12+ 新限制干扰 |
| compileSdk | 34 | 工具链要求 |
| UI | 纯 View + XML | Compose 体积大、动画多，墨水屏残影严重 |
| 存储 | SharedPreferences | 配置量极小，不引入 DataStore/Room |
| 第三方依赖 | **零** | APK 体积与低端 CPU 启动速度 |

## 4. 关键技术难点与对策

### 4.1 悬浮窗承载体（不用普通 Service）
悬浮窗由 **AccessibilityService 直接持有**，窗口类型三级降级：

1. `TYPE_ACCESSIBILITY_OVERLAY` —— **无需 SYSTEM_ALERT_WINDOW 权限**，首选
2. addView 抛异常 → `TYPE_APPLICATION_OVERLAY`（API 26+，需 canDrawOverlays）
3. API < 26 → `TYPE_PHONE`
4. 全失败 → 通知栏提示去开悬浮窗权限

### 4.2 手势自遮挡（最大的坑）
派发点击到屏幕右侧时，若悬浮球/菜单正压在该坐标上，事件会被自己的窗口吃掉。两层保险：

- **保险 A**：派发前 `updateViewLayout` 给窗口加 `FLAG_NOT_TOUCHABLE`（触摸穿透），派发完成回调后移除。不改变可见性 → 不触发墨水屏重绘。
- **保险 B**：`avoidOverlap()` —— 计算悬浮球/菜单实际矩形，若目标点落在矩形内，把目标点 Y 偏移到矩形外（上方或下方 25% 屏高处）。

`dispatchGesture` 的 `GestureResultCallback` 可能不回调（ROM bug），加 800ms Handler 超时兜底恢复 flag，避免永久穿透。

### 4.3 Android 11 包可见性
targetSdk 30 起 `queryIntentActivities` 被过滤。对策：`QUERY_ALL_PACKAGES` 权限 + manifest `<queries>` 声明 LAUNCHER intent。
（副作用：无法上架 Google Play，侧载安装无影响。）

### 4.4 墨水屏专项优化
- 配色只有 `#000000` / `#FFFFFF`，2dp 纯黑描边，**无 elevation 阴影、无渐变、无圆角渐进**
- 全局关闭动画：不用 alpha/translate 动画，不用 Ripple，按下反馈= 黑白瞬时反色
- 拖动节流 60ms（默认），另提供"低刷新模式"：拖动过程不更新，松手才落位
- app 图标经 `ColorMatrix(saturation=0)` 灰度化，避免彩色图标在墨水屏上变脏灰块
- 大字号 + bold，按钮最小 48dp

### 4.5 交互细节
- 点击 / 拖动判定：`ViewConfiguration.scaledTouchSlop`
- 松手贴边吸附至最近的左/右边缘，坐标存 Prefs
- 球在左半屏 → 菜单向右展开；右半屏 → 向左展开
- 菜单窗口带 `FLAG_NOT_FOCUSABLE`（不抢输入法焦点）；收起靠"再点球"+"5s 无操作自动收起"，**不使用全屏遮罩**（会引发全屏刷新）

### 4.6 常驻通知（保活 + 快捷回桌面）
AccessibilityService 由系统绑定、优先级高，但国产 ROM 仍可能回收。附带一个前台服务（`IMPORTANCE_MIN` 通知渠道，默认开启，设置里可关），承担两个职责：

1. **保活**：提升进程优先级
2. **点击通知直接返回桌面**：`contentIntent` 走 `ACTION_MAIN + CATEGORY_HOME + FLAG_ACTIVITY_NEW_TASK`，不经过无障碍服务。这条路径比 `performGlobalAction(GLOBAL_ACTION_HOME)` 更可靠——通知点击属于用户操作，不受 Android 10+ 后台启动限制，即使无障碍服务被 ROM 杀掉也仍然可用。

## 5. 文件清单

```
xfqiu/
├── .github/workflows/build.yml          CI：debug APK + 可选签名 release
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/xfqiu/floatball/
        │   ├── MainActivity.kt              权限引导 + 服务开关入口
        │   ├── SettingsActivity.kt          翻页方式/参数/球外观/保活开关
        │   ├── AppPickerActivity.kt         常用 app 选择（灰度图标列表）
        │   ├── core/
        │   │   ├── BallAction.kt            动作枚举（PREV/NEXT/HOME/APP/SETTINGS）
        │   │   ├── PageTurnMode.kt          TAP / SWIPE_HORIZONTAL / SWIPE_VERTICAL
        │   │   ├── Prefs.kt                 SharedPreferences 封装（唯一配置出口）
        │   │   ├── AppShortcut.kt           快捷项数据类 + 灰度图标加载
        │   │   └── GestureFactory.kt        由模式+参数构造 GestureDescription
        │   ├── service/
        │   │   ├── FloatBallService.kt      AccessibilityService：派发手势/全局动作
        │   │   ├── OverlayController.kt     悬浮窗创建/降级/销毁/穿透 flag 切换
        │   │   └── KeepAliveService.kt      可选前台服务
        │   └── ui/
        │       ├── FloatBallView.kt         折叠态：拖动、吸附、点击展开
        │       └── MenuPanelView.kt         展开态竖直条 + 自动收起计时
        └── res/
            ├── layout/                      3 个 Activity 布局 + 列表项
            ├── drawable/                    shape XML（球、按钮、描边、图标）
            ├── values/{strings,colors,styles}.xml
            └── xml/accessibility_config.xml
```

## 6. CI 方案（.github/workflows/build.yml）

- 触发：`push` / `pull_request` / `workflow_dispatch` / `tag v*`
- JDK 17 (temurin) + `android-actions/setup-android@v3` + `gradle/actions/setup-gradle@v4`
- **不提交 gradle-wrapper.jar**（二进制无法文本生成）→ CI 用 `setup-gradle` 指定 `gradle-version: 8.7` 直接跑 `gradle assembleDebug`；保留 `gradle-wrapper.properties`，本地执行一次 `gradle wrapper` 即可生成 jar
- 产物：`app-debug.apk` 上传 artifact（可直接安装）
- `versionCode` 由 `GITHUB_RUN_NUMBER` 注入，默认 1
- 打 tag 时额外创建 GitHub Release 并附加 APK
- 若仓库配置了 `KEYSTORE_BASE64` / `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` secrets，则额外产出签名 release APK；未配置则该步骤跳过（不报错）

## 7. 风险与限制（必读）

1. **本地无 JDK / Gradle / Android SDK，无法本地编译**。首轮编译错误只能由 GitHub Actions 暴露，可能需要 1~2 轮修正。
2. `dispatchGesture` 在少数深度定制 ROM 上会被拦截或被 app 反作弊逻辑忽略 → 已提供 3 种翻页模式 + 坐标/时长全可调作为对冲。
3. 无障碍服务需**用户手动在系统设置中开启**；部分 ROM 重启后自动关闭，属系统行为，app 侧无法规避（MainActivity 会检测并提示）。
4. `QUERY_ALL_PACKAGES` 导致无法上架 Google Play。
5. targetSdk 30 在 Android 12+ 设备上可安装可运行，但会有系统兼容性提示。

## 8. 实施顺序

1. Gradle 骨架 + manifest + CI workflow（先让 CI 能跑通空壳编译）
2. core 层（Prefs / 枚举 / GestureFactory / AppShortcut）
3. service 层（FloatBallService / OverlayController / KeepAliveService）
4. ui 层（FloatBallView / MenuPanelView）
5. 三个 Activity + 资源文件
6. 推 CI 编译，按报错修正至绿

---

# 增补方案 A（2026-08-27）：全局隐藏 + 返回/前进

> 状态：**已实施**。

## A.1 需求变更（覆盖第 1 节）

用户实际用法：阅读软件自带翻页，不需要注入翻页；真正需要的是**返回**、**前进（回到刚才那个应用）**、**返回桌面**。日常只用通知回桌面即可，因此悬浮球要能整体关掉。

| 功能 | 变更 | 实现手段 |
|---|---|---|
| 返回 | **新增** | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| 前进 | **新增** | 记录前台应用历史，重新拉起目标应用 |
| 上一页 / 下一页 | **保留，默认关闭** | 开关打开后才出现在菜单里 |
| 返回桌面 | 不变 | `GLOBAL_ACTION_HOME` + 通知点击 |
| 打开常用 app | 不变 | LAUNCHER Intent |
| 全局隐藏悬浮球 | **新增** | 开关，只保留常驻通知 |

第 1 节「明确不做：返回键、最近任务」作废——返回键改为明确要做；最近任务仍然不做。

## A.2 「前进」的语义（Android 没有 Forward 全局动作）

新增 `core/ForegroundTracker.kt`，靠已声明的 `typeWindowStateChanged` 事件记录三个字段：

- `foreground`：最近一次窗口变化的包名（可能是系统 UI、输入法等不可启动的包）
- `currentApp` / `previousApp`：最近两个**可启动**应用（有 LAUNCHER 入口）

前进目标：`foreground == currentApp ? previousApp : currentApp`。

推导出的行为：

| 场景 | 结果 |
|---|---|
| 阅读软件 → 回桌面 → 前进 | 回到阅读软件（桌面通常没有 LAUNCHER 入口，不进历史，靠 `foreground != currentApp` 判定） |
| 阅读软件 → 浏览器 → 前进 | 回到阅读软件 |
| 再按一次前进 | 回到浏览器，形成两应用来回切 |
| 刚开机没有历史 | Toast 提示 `forward_unavailable` |

自身包名一律不记录，打开设置页不会污染历史。`notificationTimeout` 由 500ms 降到 100ms，避免快速切换被事件合并吞掉中间状态。

## A.3 改动清单

| 文件 | 改动 |
|---|---|
| `core/ForegroundTracker.kt` | **新增**，见 A.2 |
| `core/BallAction.kt` | 新增 `Back` / `Forward` 两个 object |
| `core/Prefs.kt` | 新增 `ballHidden`（默认 false）、`pageTurnEnabled`（默认 **false**） |
| `service/FloatBallService.kt` | `onAccessibilityEvent` 喂 tracker；`execute` 加两个分支；`goForward()`；`teardown` 清 tracker |
| `service/OverlayController.kt` | `show()` 在 `ballHidden` 时直接返回；`reload()` 去掉 `!attached` 早退（否则关掉隐藏后无法恢复显示）；`keepOpenActions` 加入 `Back`（连按返回不必反复展开收起，墨水屏少刷几次） |
| `ui/MenuPanelView.kt` | 菜单项改为 返回/前进/[上页/下页]/桌面/常用 app/设置 |
| `SettingsActivity.kt` | `fillSwitches` 改为与 `fillSliders` 同形的 `(containerId, specs)`；翻页卡片顶部加「显示翻页按钮」开关，外观卡片加「隐藏悬浮球」开关 |
| `res/layout/activity_settings.xml` | 翻页卡片新增空容器 `page_turn_switch_container` |
| `res/values/strings.xml` | 新增 `menu_back`/`menu_forward`/`forward_unavailable`/`switch_hide_ball`/`switch_page_turn`；改写 `service_description`、`usage_hint` |
| `res/xml/accessibility_config.xml` | `notificationTimeout` 500→100，注释说明用途 |

`AndroidManifest.xml` 不需要改：`QUERY_ALL_PACKAGES` + `<queries>` MAIN/LAUNCHER 已经满足 `getLaunchIntentForPackage` 的包可见性要求。

## A.4 下游完整性

`BallAction` 是 sealed class，`FloatBallService.execute` 的 `when` 是穷尽式——不加分支直接编译失败，不存在漏改。全部引用点已确认只有 3 处：`FloatBallService:75-79`、`OverlayController:239`、`MenuPanelView:56-75`。

## A.5 设置入口不依赖悬浮球

已经满足，无需改动：桌面图标 → `MainActivity` → 「悬浮球设置」按钮。悬浮球隐藏后设置页依然可达。

## A.6 风险

1. **隐藏悬浮球后，返回/前进就没有入口了**——通知按你的选择只保留「点击回桌面」。要用返回/前进需先在设置里关掉隐藏。
2. 「前进」依赖前台应用历史，服务被 ROM 杀掉重连后历史清空，第一次按会提示无目标。
3. 少数 ROM 的桌面带 LAUNCHER 入口（会被当成普通应用记入历史），此时「阅读软件→回桌面→前进」仍然正确（`foreground == currentApp` 走 `previousApp`），行为一致。
4. `GLOBAL_ACTION_BACK` 在个别深度定制 ROM 上会被拦截，无法规避。

---

# 增补方案 B（2026-08-29）：墨水屏触摸兼容与后台恢复

> 状态：**已实施，等待 CI 与目标电纸书实机验证**。

## B.1 产品边界

本应用只面向墨水屏电纸书。默认设计按刷新慢、残影明显、触控噪声大、侧边系统触控区宽、后台清理严格处理，不为普通手机动画或贴边手势做折中。

## B.2 悬浮球修复

- 点击/拖动改为独立状态机，触摸阈值至少 12dp；`ACTION_UP` 最终坐标同样参与判断。
- `ACTION_CANCEL` 与多指异常只恢复原位，不再吸边或持久化一次失败拖动。
- 初始位置、拖动、吸附、旋转和旧坐标统一避让系统 Insets，并额外保留默认 16dp 电纸书侧边安全距离。
- 低刷新拖动第一次移动立即显示，后续约 240ms 更新一次，松手必定提交最终位置。
- 自动窗口模式在拥有悬浮窗权限时优先 `TYPE_APPLICATION_OVERLAY`，再回退无障碍覆盖层；另提供强制模式。
- `updateViewLayout` 失败后受控重挂载，触摸穿透只有系统窗口更新成功后才算生效。

## B.3 后台恢复与授权

- 保活开关直接启停 `KeepAliveService`，不再依赖无障碍服务静态实例。
- 无障碍服务解绑不再无条件停止用户已开启的保活服务。
- 新增 `StartupReceiver`，在 `BOOT_COMPLETED` 与 `MY_PACKAGE_REPLACED` 后按配置恢复前台服务。
- 后台引导按两阶段持久化执行：Android 原生电池优化豁免 → 隐藏自启动组件。
- 隐藏组件链不按品牌提前过滤：已知小米组件、MIUI 官方权限编辑 action、运行时扫描已导出的系统自启动 Activity、Android 11 高耗电白名单、应用详情依次兜底。
- 主界面分别展示无障碍配置、实际连接、保活服务实际运行、通知权限/渠道和电池优化状态。

## B.4 验证

- 新增纯 JVM 测试：触摸抖动/拖动/取消/最终坐标、墨水屏刷新策略、安全几何、窗口模式策略、后台阶段持久化、自启动目标识别和 Manifest 启动恢复声明。
- CI 在构建 APK 前执行 `testDebugUnitTest` 与 `lintDebug`。
- 标准 Android force-stop 仍会把包置为 stopped，任何 Receiver/Service 都不能绕过；必须由用户授权设备自带白名单或再次显式打开应用。

# C 悬浮球「点不开 + 拉不动」第二轮修复

> 状态：**待批准**。B.2 那一轮修复没有解决问题，本轮不再做增量微调，直接改掉吞掉手势的三处逻辑。

## C.1 根因定位

窗口层已排除：`BASE_FLAGS` 不含 `FLAG_NOT_TOUCHABLE`，翻页默认关闭所以穿透分支根本进不去；
`OverlayModePolicy` 在有悬浮窗权限时已优先 `TYPE_APPLICATION_OVERLAY`。事件能到窗口，是被手势层吃掉的。

1. **幻影多指直接毁掉整个手势**（`FloatBallView.kt:109`）
   `ACTION_POINTER_DOWN -> cancelTouch()`。电纸书电容层噪声大，会在真实触点附近报告瞬时第二指针。
   - 手势处于 PRESSED：`onCancel()` 返回 `NONE`，**点击被静默丢弃**，菜单不展开。
   - 手势处于 DRAGGING：`dragCancelled` → `cancelDrag()` 把球弹回原位，**看起来完全拖不动**。
   一行代码同时解释了两个症状，而且正是 B.2 引入的。

2. **DRAGGING 是锁存态，一个噪声尖峰就永久吞掉点击**（`FloatBallGestureClassifier.kt:43-53`）
   单个坏采样越过 12dp 后状态锁死在 DRAGGING，即使手指回到原点，`onUp` 也只会给 `dragFinished`。
   `endDrag()` 走 `dock()` 把 X 吸回原来那条边 → 菜单没开、球也没动。

3. **低刷新拖动 240ms 太慢**（`OverlayController.kt:496`）
   首次移动后每 240ms 才更新一次窗口位置，叠加墨水屏残影，主观就是「拉不动」。

## C.2 修改清单

- `FloatBallView.kt`
  - 删除 `ACTION_POINTER_DOWN -> cancelTouch()`：已按 `activePointerId` 跟踪，多余指针直接忽略。
  - `ACTION_POINTER_UP` 命中活跃指针时按「抬手提交」处理，不再当取消。
  - 仅对异常路径（活跃指针丢失）保留 `Log.w`，符合「只在异常时输出日志」。
- `FloatBallGestureClassifier.kt`
  - `onUp` 增加净位移复核：DRAGGING 但相对按下点的净位移小于阈值时，判为 `tapped` 并附 `dragCancelled` 复位，
    噪声尖峰不再偷走点击。真实拖动（净位移达阈值）行为不变。
- `OverlayController.kt`
  - `LOW_REFRESH_DRAG_MS` 240 → 120，拖动可感知，仍远低于普通手机刷新频率。
  - `cancelDrag()` 在坐标未变化时跳过 `updateViewLayout`，避免「复位 + 展开菜单」造成两次墨水屏刷新。
- `FloatBallGestureClassifierTest.kt`
  - 补一条噪声尖峰后手指回到原点仍判点击的用例。现有 5 条用例语义不变（净位移 16px/40px 均达阈值）。

## C.3 风险与边界

- **行为变更**：手指绕一圈回到原点会判成点击并展开菜单。属于可接受取舍，优先保证点击不丢。
- `dock()` 横向吸边保留，横向拖动松手仍回吸到边缘，这是既有设计不是缺陷；纵向位置正常保留。
- 无公共 API、权限、Manifest 变更；`GestureDecision` 字段不增删，无下游调用方需要同步。
- **未能本地验证**：本机 `java` 不在 PATH，`Program Files/Java` 与 Android Studio JBR 均不存在，
  仓库也没有 `gradlew` 脚本，按「不额外安装环境」的要求不自行装 JDK。编译与单测只能由 GitHub Actions 执行。

## C.4 窗口类型链条（同轮一并做掉）

手势层修完仍可能是「事件根本没进窗口」。本轮把窗口类型这条链也补成可观测、不会静默失败的：

1. **`APPLICATION` 模式丢权限时不再让球消失**（`OverlayModePolicy.candidates` 现在返回空列表）
   权限被回收后候选为空 → 只弹一个 Toast，球彻底不出现，用户无法自救。
   改为始终保留 `ACCESSIBILITY` 兜底，并回报「已降级」，球至少还在。
2. **把实际生效的窗口类型显示出来**
   `candidates` 改成 `attachPlan(mode, canDrawOverlays)`，返回候选顺序 + `degradedFromApplication`；
   `OverlayController.activeWindowKind()` 暴露真正 `addView` 成功的类型，
   引导页新增一行「悬浮窗实际类型：普通悬浮窗 / 无障碍覆盖层 / 未挂载」。
   这样"球点不动"时能一眼确认当前是哪种窗口，不必再靠猜。
3. **降级提示只在用户显式选了 `APPLICATION` 时弹**
   `AUTO` 走到 `ACCESSIBILITY` 是正常兜底，`notifySettingsChanged()` 每次 `onResume` 都会调用，
   若无条件弹 Toast 会变成骚扰；`AUTO` 的降级由上面那行状态文字体现。

`attachPlan` 替换 `candidates`，下游只有 `OverlayController` 与 `OverlayModePolicyTest` 两处，同步改完。

## C.5 验证方式

- CI 跑 `testDebugUnitTest` 与 `lintDebug`。
- 实机需确认三点：轻点必展开菜单、纵向拖动跟手、拖到另一侧松手后吸到对边。
- 若仍点不动：看引导页「悬浮窗实际类型」。
  显示无障碍覆盖层 → 去设置页选「普通悬浮窗（电纸书兼容）」；已经是普通悬浮窗仍失效 → 是 ROM 拦截整类悬浮窗触摸。

## D 强制模式「禁止回退」开关

### D.1 为什么要这个开关

C.4 给 `APPLICATION` 模式加了无障碍覆盖层兜底，代价是**强制模式变弱了**：
选了「普通悬浮窗」但 `addView` 失败时会静默退回无障碍覆盖层。
如果用户正是因为无障碍覆盖层不分发触摸才强制切过去的，这一退就回到了坏状态 —— 球在，但点不动。

严格与兜底各有各的坏处，靠推理定不下来，交给用户在实机上试：
- **关（默认）**：`[APPLICATION, ACCESSIBILITY]`，球一定在，但可能是不收触摸的那种窗口。
- **开**：`[APPLICATION]`，普通悬浮窗建不起来就让球不出现 —— **球消失本身就是结论**，
  说明 ROM 拒绝了整类普通悬浮窗，而不是手势层的问题。

### D.2 语义（只动一个格子）

`strictApplication` 只在「显式选了 `APPLICATION`」且「权限已给」时才去掉兜底：

| 模式 | 权限 | 开关关 | 开关开 |
| --- | --- | --- | --- |
| `ACCESSIBILITY` | - | `[ACCESSIBILITY]` | `[ACCESSIBILITY]`（开关无效） |
| `AUTO` | 有 | `[APPLICATION, ACCESSIBILITY]` | 同左（开关无效） |
| `AUTO` | 无 | `[ACCESSIBILITY]` | 同左（开关无效） |
| `APPLICATION` | 有 | `[APPLICATION, ACCESSIBILITY]` | **`[APPLICATION]`** |
| `APPLICATION` | 无 | `[ACCESSIBILITY]` | 同左（开关无效） |

两处刻意不生效：
- **`AUTO`**：它的定义就是自动兜底，让开关改它的语义等于两个控件抢一件事。
- **权限缺失**：这种失败用户能自己去授权修好，把唯一候选也砍掉只是让球白消失，换不到任何信息。

候选列表因此永远非空，C.4 修掉的那个硬失败不会以另一种形式回来。

### D.3 改动清单

- `Prefs.kt`：加 `strictApplicationOverlay`（`KEY_STRICT_APPLICATION_OVERLAY`，默认 `false` 保持现有行为）。
- `OverlayMode.kt`：`attachPlan` 加第三参 `strictApplication`；
  `OverlayAttachPlan` 加 `accessibilityFallbackDisabled`，让调用方能区分失败原因。
- `OverlayController.kt`：传入 `prefs.strictApplicationOverlay`；
  全部候选失败时按 `accessibilityFallbackDisabled` 选提示语 ——
  严格模式下失败原因是 ROM 拒绝普通悬浮窗，再提示「请确认无障碍服务已开启」是误导。
- `activity_settings.xml`：单选组下方加 `overlay_mode_switch_container` + 一行说明。
  放在单选组旁边而不是页尾的通用开关区，因为它修饰的就是上面那个选项。
- `SettingsActivity.kt`：加 `overlayModeSwitches()`，走现有 `fillSwitches` 机制；
  监听里已有的 `notifyService()` → `reload()` 会 remove/add 窗口，开关即时生效。
- `strings.xml`：开关标签、说明、严格模式失败提示。
- `OverlayModePolicyTest.kt`：`attachPlan` 签名变了，4 处调用必须同步（P0 下游完整性）；
  另加 3 条用例把上表里「开关生效」和「开关刻意无效」两种格子都钉住。

### D.4 风险与边界

- **开关打开且 ROM 拒绝普通悬浮窗时，悬浮球不出现**。这是开关的设计意图，不是缺陷。
  自救路径已确认：桌面图标 → 引导页 → 设置页关掉开关（`MainActivity` 是 LAUNCHER，
  `MainActivity.kt:100` 可进设置页），不依赖悬浮球本身。
- 默认 `false`，老用户升级后行为不变。
- `attachPlan` 是 `internal` 级策略函数，调用方只有 `OverlayController` 和测试两处，已全部同步。
- **同样没有本地编译**：本机无 JDK 且不自行安装，编译与单测仍只能由 CI 执行。

### D.5 实机测试步骤

1. 设置页选「普通悬浮窗（电纸书兼容）」，授权悬浮窗权限。
2. 打开「禁止回退到无障碍覆盖层」。
3. 回引导页看「悬浮窗实际类型」：
   - 显示**普通悬浮窗** → 窗口类型没问题，点不动就是手势层，看 C 段。
   - 显示**未挂载**（球消失） → ROM 拒绝普通悬浮窗，这条路走不通，关掉开关退回无障碍覆盖层。

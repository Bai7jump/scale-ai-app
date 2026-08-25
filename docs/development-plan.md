# 蚂蚁阿福体脂秤 AI 分析 App — 开发计划 v1.1

> **For Hermes:** 按本计划分阶段执行，每阶段完成后子 agent 评审，通过后进入下一阶段。
> **v1.1 变更:** 采纳子 agent 评审 21 项意见（B1 公式经复核为误报，仍采纳"单测锁定公式"建议；其余 B2/B3/I1-I8/S1-S10 全部落实）。

**Goal:** 开发一款 Android App，称重后自动从蚂蚁阿福体脂秤（AFU-WL-TZ-A1）采集数据，计算体成分，用 DeepSeek AI 生成个性化建议，支持双人档案、历史趋势和 AI 对话。

**Architecture:** 原生 Android（Kotlin + Jetpack Compose + MVVM）。BLE 模块负责连接秤并解析原始数据包；Room 数据库存档案/测量/对话；DeepSeek API（OpenAI 兼容格式）生成报告与对话；称重页/AI教练页/历史页三个 Tab + 档案管理页。

**Tech Stack:**
- Kotlin 2.0 + Jetpack Compose（Material3）
- Android 官方 BLE API（BluetoothLeScanner + BluetoothGatt）
- Room（SQLite）+ DataStore（设置/API Key）
- MPAndroidChart（趋势图，Compose 用 AndroidView 包装）
- OkHttp + kotlinx.serialization（DeepSeek API）
- Gradle 插件：org.jetbrains.kotlin.plugin.compose + com.google.devtools.ksp + kotlinx-serialization（Kotlin 2.0 必需）；compileSdk/targetSdk=36，minSdk=26，AGP ≥ 8.11

**设备/协议事实（已验证，勿改）：**
- 秤 MAC: `D0:5C:00:32:47:A9`，设备名 `AFU-WL-TZ-A1`
- GATT: notify 特征 `ffb2` 和 `1531`；write 特征 `ffb1`/`1531`/`1532`
- 握手命令: 连接后向 write 特征写 `FD37000000000000`（response=false），秤才开始推数据
- 数据包: 首字节 `0xAC`
  - type `0x00`/`0x80`（重量包）: `data[3:6]` 24位大端体重raw，`体重kg = (raw - 6815744) / 1000`（仅作 UI 实时显示）
  - type `0x02`（阻抗完成包）: `data[6:8]` = 阻抗Ω（直接值），`data[10:13]` = 体重raw（同偏移）——**会话完成唯一数据源**
- 广播窗口: 称重后约 60 秒，超时休眠 → 必须"称完马上打开 App"

---

## Phase 0: 开发环境搭建

### Task 0.1: 安装 Android Studio 到 D 盘
**Objective:** 安装 Android Studio（含 Android SDK、JDK 17）
**Files:** 无
**Step 1:** 从 https://developer.android.com/studio 下载 Windows 安装包
**Step 2:** 安装到 `D:\Android\Android Studio`；SDK 位置 `D:\Android\Sdk`
**验证:** Android Studio 正常打开，创建空项目不报错

### Task 0.2: 配置红米 K90 Max 真机调试（HyperOS）
**Objective:** 手机开启开发者模式，USB 调试可用
**Step 1:** 设置→我的设备→全部参数→**连点 7 次 OS 版本号**（K90 Max 是 HyperOS 澎湃 OS，不是 MIUI）→ 开启开发者选项
**Step 2:** 开发者选项→开启 USB 调试 + **USB 安装（安全设置）**（小米系需此项才能免登录装 APK）
**Step 3:** USB 连接电脑，手机弹窗选"允许调试"
**验证:** Android Studio 设备列表显示 Redmi K90 Max (Android 16)

### Task 0.3: 创建项目骨架
**Objective:** Android Studio 创建空 Compose 项目
**Files:** Create: `D:\code\scale-ai-app\`，包名 `com.cmft.scaleai`，Empty Activity + Kotlin + Compose
**验证:** App 真机安装运行显示 Hello World

---

## Phase 1: 数据层（Room）

### Task 1.1: 项目配置 + 依赖 + 插件
**Files:** Modify: `app/build.gradle.kts`、`settings.gradle.kts`
**Step:** 添加插件（kotlin.plugin.compose / ksp / kotlinx-serialization）+ 依赖（room-runtime/ktx/compiler、datastore-preferences、okhttp、kotlinx-serialization-json、mpandroidchart、retrofit 可选）
**验证:** 项目同步成功

### Task 1.2: Manifest 权限（蓝牙 + 网络）
**Files:** Modify: `app/src/main/AndroidManifest.xml`
**Step:** 添加：
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.INTERNET" />  <!-- AI 必需! -->
```
**注意:** Android 12+(API31+) 扫 BLE 不需要定位权限，故只对 API 26-30 设备声明旧权限，全部加 `maxSdkVersion="30"`：
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
```
（Task 2.1 运行时请求的旧权限分支与 manifest 声明保持一致）
**验证:** 编译通过

### Task 1.3: 实体类（nullable + 基准体重）
**Files:**
- Create: `.../data/entity/UserProfile.kt`
- Create: `.../data/entity/Measurement.kt`
- Create: `.../data/entity/ChatMessage.kt`

```kotlin
// UserProfile: id, name, gender(male/female), heightCm, age,
//              targetWeightKg, targetBodyFatPct, baselineWeightKg(新增!), isActive
// Measurement: id, userId(FK级联删除), timestamp, weightKg, impedance: Double?,
//              bodyFatPct: Double?, waterPct: Double?, muscleRatePct: Double?,
//              bonePct: Double?, proteinPct: Double?, visceralFat: Double?,
//              bmrKcal: Int?, bmi: Double?, source(ble/manual), reportGenerated: Boolean
// ChatMessage: id, userId, role(user/assistant), content, timestamp
```
**要点:** 手动输入记录无体脂 → 体成分字段全部 **nullable**；`reportGenerated` 标记 AI 报告是否已生成（供重试）
**验证:** 编译通过

### Task 1.4: DAO（Flow 响应式）
**Files:**
- Create: `.../data/dao/UserProfileDao.kt`
- Create: `.../data/dao/MeasurementDao.kt`
- Create: `.../data/dao/ChatMessageDao.kt`
**内容:** 标准 CRUD；查询返回 `Flow<List<...>>`（Compose 响应式刷新）；`getLatestMeasurement(userId): Flow<Measurement?>`；`getMeasurementsBetween(userId, start, end)`；`getMeasurementsSince(userId, count)`（AI 上下文用）
**验证:** 编译通过

### Task 1.5: Database + Repository
**Files:**
- Create: `.../data/ScaleDatabase.kt`（外键 + 级联删除：Measurement.userId → UserProfile.id ON DELETE CASCADE）
- Create: `.../data/ScaleRepository.kt`（挂起函数封装 + Flow 透传）
**验证:** 编译通过

### Task 1.6: DAO 测试（instrumented，不用纯 JVM）
**Objective:** Room DAO 需要 SQLite 运行时，纯 JVM 单测跑不通
**Files:** Create: `app/src/androidTest/java/.../ScaleDaoTest.kt`
**Step:** 用 `androidx.room:room-testing` + InstrumentationRegistry 真机跑 CRUD/查询/级联删除测试
**验证:** `./gradlew connectedDebugAndroidTest` 全绿（真机）

---

## Phase 2: BLE 模块（协议移植）

### Task 2.1: 运行时权限请求（关键：不烧 60s 窗口）
**Files:** Create: `.../ble/BlePermissionHelper.kt`
**内容:**
- `ActivityResultContracts.RequestMultiplePermissions` 请求 `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`（minSdk<31 设备额外请求 ACCESS_FINE_LOCATION）
- 被拒引导："去设置→应用→权限→开启附近设备"
**设计:** **首启引导流程**：第一次打开 App 先完成权限授权 + 建档案，再进称重页——把权限消耗从称重场景剥离（否则打开 App 弹 2~3 个权限框，60s 广播窗口被吃光）
**验证:** 首次打开授权后，第二次打开直接进称重页

### Task 2.2: 扫描器
**Files:** Create: `.../ble/ScaleScanner.kt`
**内容:** BluetoothLeScanner；`SCAN_MODE_LOW_LATENCY`；过滤设备名 `AFU-WL-TZ-A1` 或 MAC `D0:5C:00:32:47:A9`；发现即 `stopScan`；按 MAC 去重；处理 adapter 关闭
**验证:** 踩秤后 App 能扫到（真机）

### Task 2.3: GATT 连接 + 订阅 + 握手
**Files:** Create: `.../ble/ScaleConnection.kt`
**内容:**
1. 连接 → onServicesDiscovered
2. 订阅 notify（`ffb2` + `1531`）：`setCharacteristicNotification(uuid, true)` **+ 写 CCCD descriptor(0x2902)=0x01**（漏写则无数据！）
3. 握手：向 write 特征写 `FD37000000000000`（response=false），**回退链**：优先 `ffb1` → 失败延时 200ms 重发 → 再失败试 `1531`/`1532`
4. 时序：先订阅 notify 再写握手
5. `onConnectionStateChange` 处理意外断开：按"已收到阻抗/未收到"分流（收到阻抗即正常完成）
**验证:** 连接秤后收到 notify 回调，打印原始 hex 落 logcat

### Task 2.4: 数据包解析器（防越界）
**Files:** Create: `.../ble/ScalePacketParser.kt`
**内容:** 纯函数（无 Android 依赖，可单测）：
```kotlin
fun parsePacket(data: ByteArray): ScaleReading? {
    if (data.size < 6 || data[0] != 0xAC.toByte()) return null
    return when (data[2]) {
        0x00, 0x80 -> {  // 重量包: 仅UI显示用
            val raw = ((data[3].toInt() and 0xFF) shl 16) or ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val w = (raw - 6815744) / 1000.0
            if (w in 2.0..200.0) ScaleReading(weightKg = w) else null
        }
        0x02 -> {  // 阻抗完成包: 会话完成唯一数据源
            if (data.size < 13) return null  // 防越界!
            val imp = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            val raw = ((data[10].toInt() and 0xFF) shl 16) or ((data[11].toInt() and 0xFF) shl 8) or (data[12].toInt() and 0xFF)
            val w = (raw - 6815744) / 1000.0
            if (w !in 2.0..200.0) null else ScaleReading(weightKg = w, impedance = imp)
        }
        else -> null
    }
}
```
**验证:** 单测：`ac29806905b8020005400064000000000029d50f` → 67.00kg；`ac2902000266022f01806905b80000000029d601` → imp=559, w=67.00；**截断包/空包 → null 不崩溃**

### Task 2.5: 称重会话管理器
**Files:** Create: `.../ble/ScaleSessionManager.kt`
**内容:** 状态机 Idle→Scanning→Connecting→Receiving→Done/Timeout；**每次会话开始重置缓冲区**（防双人连续称重串包）；收重量包仅更新 UI 实时体重；**收到 0x02 即 Done**（带最终体重+阻抗）并停止监听；30s 无 0x02 判超时
**验证:** 真机完整流程拿到 67.x kg + 559Ω 级数据

---

## Phase 3: 体成分计算

### Task 3.1: 计算引擎（公式用单测锁定）
**Files:** Create: `.../calc/BodyCompositionCalculator.kt`
**内容:** 纯 Kotlin（输入 weight, impedance, height, age, gender → 输出）：
- BMI = weight / (h/100)^2
- LBM 系数 = (h*9.058/100)*(h/100) + weight*0.32 + 12.226 - imp*0.0068 - age*0.0542
- 体脂率 = (1 - (LBM - fat_const) * fat_coeff / weight) * 100；fat_const: 男0.8/女(age≤49?9.25:7.25)；fat_coeff: 男 weight<61→0.98，h>160→×1.03；女 weight>60→0.96，<50→1.02，h>160→×1.03
- 水分 = 先算 base=(100-fat)*0.7，base≤50 则 ×1.02，否则 ×0.98，再 clamp(35,75)
- 骨量→骨率、肌肉率、骨骼肌率、蛋白质、内脏脂肪、皮下脂肪（**估算公式见下，标注"估算值"**）、BMR(370+21.6×FFM)、体型
- **派生指标估算公式（标注估算值，UI 与 AI Prompt 标注"估算"）**：
  - 骨率: bone_mass=(0.18016894-LBM*0.05158)*-1(男)/(0.245691014-LBM*0.07158)*-1(女)；bone_mass>2.2 则+0.1 否则-0.1，clamp(0.5,∞)；bone_pct=bone_mass*0.85/weight*100
  - 肌肉率: fat_mass=fat%*weight；muscle_mass=weight-fat_mass-bone_mass*0.85；muscle_rate=muscle_mass/weight*100
  - 骨骼肌率: skeletal=muscle_rate*0.558
  - 蛋白质: protein=clamp(muscle_rate-water_pct, 5, 32)
  - 内脏脂肪: visceral=clamp(bmi*0.3-age*0.05+0.4, 1, 50)
  - 皮下脂肪: subcut=fat%*0.71
  - 体型: bmi<18.5偏瘦/<24标准/<28超重/否则肥胖
- **公式来源:** 系数基于 Omron HBF 系列公开 BIA 公式体系，经 AFU-WL-TZ-A1 实测数据校准（基准 67.00/559/176/27/male → 14.4%）
**验证（单测锁定，防止回归）:**
- (67.00, 559, 176, 27, male) → 体脂 **14.4%**（±0.1）、BMI 21.6、BMR 1608
- (55.0, 700, 162, 30, female) → 合理女性区间（体脂 25-32%）

### Task 3.2: 双人匹配逻辑（基准体重 + 置信度）
**Files:** Create: `.../calc/UserMatcher.kt`
**内容:** 输入测量值 + 两个档案 → 建议用户 + 置信度：
1. 有历史 → 用最近 5 次体重均值差，差小者优先
2. 无历史 → 用 `baselineWeightKg`（建档时填）
3. 阻抗辅助信号（弱）：>600Ω 偏女、<600Ω 偏男——仅低置信时参考
4. 置信度：|Δ1-Δ2| < 0.5kg → 低置信（弹窗默认但仍提示）
5. 无任何基准 → 默认 isActive 用户 + 低置信提示
**验证:** 单测：男基准 67kg/女基准 66.5kg（相近），输入 66.6 → 低置信（|Δ|=0.4/0.1，差0.3<0.5）；男基准 67/女基准 55（差异大），输入 66.8 → 高置信男

---

## Phase 4: UI 基础 + 档案管理

### Task 4.1: 三 Tab 导航
**Files:** Create: `.../ui/MainActivity.kt`、`.../ui/navigation/AppNavHost.kt`
**内容:** Scaffold + NavigationBar：称重 / AI教练 / 历史
**验证:** 真机三 Tab 可切换

### Task 4.2: 首启引导（权限 + 建档案）
**Files:** Create: `.../ui/onboarding/OnboardingScreen.kt`
**内容:** 首次启动流程：① 请求 BLE 权限 → ② 创建默认男档案（姓名/性别/身高/年龄/目标体重/目标体脂/基准体重）→ ③ 提示"可稍后添加家人档案" → 进入主界面
**验证:** 全新安装后走完引导，能进称重页

### Task 4.3: 档案管理页（含 API Key 设置）
**Files:** Create: `.../ui/profile/ProfileScreen.kt` + ViewModel
**内容:**
- 档案列表（你/她），新建/编辑/删除（删除级联清测量）
- 编辑字段：姓名、性别、身高、年龄、目标体重、目标体脂、基准体重
- **API Key 设置区**：DeepSeek API Key 输入（存 DataStore）+ "测试连接"按钮（发一条最小请求验证 200）
**验证:** 能建出女档案、改身高年龄、填 API Key 测试通过

### Task 4.4: 主题 + 称重页骨架
**Files:** Create: `.../ui/theme/*`、`.../ui/scale/ScaleScreen.kt`
**内容:** Material3 主题；称重页：同步按钮 + 手动输入入口 + 最近一次结果卡片 + 右上角档案入口
**验证:** 真机显示正常

---

## Phase 5: 称重页

### Task 5.1: 同步流程 ViewModel（确认→计算→保存→报告 顺序）
**Files:** Create: `.../ui/scale/ScaleViewModel.kt`
**内容:** 状态机（Idle/Scanning/Connecting/Receiving/Confirming/Result/Timeout）+ **关键流程顺序**：
```
BLE会话完成(0x02包) → UserMatcher匹配建议 → [弹窗确认人选] → 
按确认人选计算体成分 → 保存Measurement → 触发AI报告(异步,失败可重试)
```
**为什么先确认再计算:** 体成分公式按性别计算，先入库再改选会导致数据错误且不可恢复
**验证:** 真机踩秤：同步→确认→出结果→入库

### Task 5.2: 双人确认弹窗
**Files:** Modify: `.../ui/scale/ScaleScreen.kt`
**内容:** 测量成功弹 Dialog"本次是谁？[你/她]"，默认高亮匹配建议，可改选；低置信时附加提示
**验证:** 真机弹窗正常，选人后数据归档正确

### Task 5.3: 手动输入兜底
**Files:** Modify: `.../ui/scale/ScaleScreen.kt` + ViewModel
**内容:** 手动输入体重 + 选人 → 存 Measurement(source=manual, 体成分字段=null) → 触发 AI 报告（Prompt 注明无体脂数据）
**验证:** 手动输入 66.5kg 男档案 → 历史页出现记录，AI 报告正常生成

---

## Phase 6: AI 集成（DeepSeek）

### Task 6.1: API Client（完整闭环）
**Files:** Create: `.../ai/DeepSeekClient.kt`
**内容:**
- OkHttp POST `https://api.deepseek.com/chat/completions`，模型 `deepseek-chat`
- **超时配置：connectTimeout 15s / readTimeout 60s**（长报告可能 30s+，默认 10s 会超时）
- 错误映射：401（Key 错）→ 提示检查 Key；429（限流）→ 提示稍后重试；5xx → 服务端错误
**验证:** 调试：发 "你好" 返回正常文本；错 Key 返回 401 提示

### Task 6.2: Prompt 模板 + System Prompt
**Files:** Create: `.../ai/PromptBuilder.kt`
**内容:**
- System Prompt 固定：角色="专业健身教练+营养师"，中文输出，语气鼓励务实
- 报告 Prompt：输入（用户档案 + 本次测量 + 上次测量 + 最近 N 条趋势摘要）→ 输出 5 段：a.本次解读 b.对比+趋势 c.饮食建议（按目标） d.训练建议（结合推拉腿计划） e.目标进度
- **上下文裁剪：报告类消息只保留最近 2 份 + 趋势摘要，对话保留最近 20 条**（防超 DeepSeek 上下文）
**验证:** 单测：给定 mock 数据，Prompt 含全部 5 项要求且带上下文

### Task 6.3: 报告生成 + 对话（记住上下文）
**Files:** Create: `.../ai/AiCoachRepository.kt`
**内容:**
- `generateReport(userId, measurementId)`：拉历史→构建 Prompt→调 API→存 assistant 消息→置 `reportGenerated=true`
- `regenerateReport(userId, measurementId)`：报告失败后重试入口
- `chat(userId, message)`：拉最近 20 条 + 最新测量→调 API→追加回复
**验证:** 真机：称重后自动生成报告；AI 失败→点重试成功；对话 3 轮 AI 记得前文

---

## Phase 7: AI 教练页

### Task 7.1: 对话 UI
**Files:** Create: `.../ui/coach/AiCoachScreen.kt` + ViewModel
**内容:** 报告卡片（纯文本/简单 Markdown）+ 对话气泡列表 + 输入框 + loading 态 + **报告卡片"重试生成"按钮**（reportGenerated=false 时显示）
**验证:** 真机：报告显示、对话流畅、重启后历史仍在

---

## Phase 8: 历史页

### Task 8.1: 趋势图 + 列表
**Files:** Create: `.../ui/history/HistoryScreen.kt` + ViewModel
**内容:**
- MPAndroidChart 双 Y 轴曲线：左轴体重(kg)、右轴体脂率(%)（量纲不同必须双轴）
- 可切换查看用户（你/她）
- 手动输入记录无体脂 → 体脂曲线断点：**体脂缺失点用 `Entry(y=Float.NaN)` 形成断点**（MPAndroidChart 原生支持），或按有/无体脂拆两个数据集
- 下方记录列表：日期/体重/体脂/来源(BLE/手动)
**验证:** 真机多次称重后曲线正确、双用户数据隔离、重启保留

---

## Phase 9: 集成与打磨

### Task 9.1: 全流程真机测试
**测试场景（全部通过才算 M5）：**
1. 男档案称重（BLE）→ 确认 → 报告 → 历史曲线
2. 女档案称重（BLE）→ 确认 → 报告 → 历史曲线
3. **双人体重相近（66.8 vs 66.5）→ 低置信弹窗 → 手动选对**
4. 手动输入兜底（秤休眠时）
5. 秤休眠时点同步 → 超时提示 → 引导手动输入
6. 重启 App 数据保留
7. AI 对话上下文连续 3 轮
8. **AI 失败 → 重试生成成功**
9. **首启引导：全新安装→权限→建档→称重**（确认权限不烧窗口）
**验证:** 9 项全过

### Task 9.2: 打包 APK
**Files:** `./gradlew assembleRelease`（或 debug 直装）
**验证:** APK 安装红米，全流程可用

---

## 里程碑与验收

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M1 | 环境就绪 | 真机跑起 Hello World |
| M2 | 数据层+BLE+计算 | 真机踩秤拿到正确体成分（公式单测锁定） |
| M3 | 称重全流程 | 同步→确认→计算→保存→报告 |
| M4 | AI 对话+历史 | 曲线数值与 DB 一致、双用户隔离、重启保留、对话连续 |
| M5 | 打包交付 | APK 可用，9 项测试全过 |

## 风险与对策

| 风险 | 对策 |
|---|---|
| 秤广播窗口短（60s） | 首启引导把权限/建档剥离出称重场景；称完马上打开 App；超时引导手动输入 |
| 双人自动匹配误判 | 弹确认 + 可改选；基准体重字段兜底；双人相近场景专门测试 |
| 体脂公式与 App 显示偏差 | 与蚂蚁阿福 App 同次称重对比校准；单测锁定已验证基准值 |
| Android 16 / HyperOS BLE 兼容 | 主动型前台流程不依赖后台；真机实测（S2 已含 HyperOS 开启步骤） |
| DeepSeek 不可用 | 报告失败状态持久化 + 重试按钮；错误映射提示 |
| 健康数据隐私 | App 内提示数据将发送至 DeepSeek API；Key 存本地 DataStore |
| Room 升级 | v1 不加迁移逻辑，后续版本用 Migration 链 |
| 0x00 vs 0x80 语义不确定 | 会话完成只认 0x02 阻抗包（自带最终体重），重量包仅 UI 显示，天然规避 |

## 依赖的已验证事实（勿改动）

- 握手命令 `FD37000000000000` 必须写，否则收不到数据
- 重量包偏移 6815744；阻抗包 data[6:8] 直接是 Ω（不再除系数）
- 体重范围过滤 2.0~200.0kg
- 公式基准：(67.00, 559, 176, 27, male) → 体脂 14.4% / BMI 21.6 / BMR 1608（已验证）

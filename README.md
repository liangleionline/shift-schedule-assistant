# 班组排班助手（Android）

<p align="center">
  <a href="https://github.com/liangleionline/shift-schedule-assistant/releases/latest"><img src="https://img.shields.io/github/v/release/liangleionline/shift-schedule-assistant?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=4F7DF3&logo=github" alt="最新版本"></a>
  <a href="https://github.com/liangleionline/shift-schedule-assistant/releases"><img src="https://img.shields.io/github/downloads/liangleionline/shift-schedule-assistant/total?label=%E4%B8%8B%E8%BD%BD%E6%80%BB%E6%95%B0&color=2EA043&logo=github" alt="下载总数"></a>
  <a href="https://github.com/liangleionline/shift-schedule-assistant"><img src="https://img.shields.io/github/stars/liangleionline/shift-schedule-assistant?label=Stars&color=DB61A2&logo=github" alt="Stars"></a>
  <a href="https://github.com/liangleionline/shift-schedule-assistant/blob/main/LICENSE"><img src="https://img.shields.io/github/license/liangleionline/shift-schedule-assistant?label=%E8%AE%B8%E5%8F%AF%E8%AF%81&color=blue" alt="License"></a>
  <a href="https://github.com/liangleionline/shift-schedule-assistant"><img src="https://img.shields.io/github/last-commit/liangleionline/shift-schedule-assistant?label=%E6%9C%80%E8%BF%91%E6%8F%90%E4%BA%A4&color=orange" alt="Last commit"></a>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 6.0+">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-blueviolet" alt="arm64-v8a">
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Room-2.6.1-6DB33F" alt="Room">
  <img src="https://img.shields.io/badge/SDK-36-informational" alt="SDK 36">
</p>
<p align="center">
  <img src="https://komarev.com/ghpvc/?username=liangleionline&repo=shift-schedule-assistant&label=%E8%AE%BF%E9%97%AE%E9%87%8F&color=0e75b6&style=flat" alt="访问量">
  <a href="https://github.com/liangleionline/shift-schedule-assistant/graphs/traffic"><img src="https://img.shields.io/badge/%E8%AE%BF%E9%97%AE%E4%B8%8E%E4%B8%8B%E8%BD%BD%E8%AF%A6%E6%83%85-Insights%20Traffic-8250df" alt="访问与下载详情"></a>
</p>

面向**作息不固定、休息日不规律**班组（如轮班制一线班组）的安卓排班查看与管理工具。以「班 → 小组」为组织层级，支持把聊天群里复制来的排班文本一键导入、按小组循环排班、每日手动微调，并在首页以「上班 / 休息」双栏查看当天与次日安排。

全部数据保存在手机本地，**离线可用、无需登录、不联网上传**。

> 「访问量」徽章按 README 被浏览的次数累加；「下载总数」统计所有 Release 附件的累计下载次数，均由对应服务实时生成。精确的 14 天访问/克隆曲线与来源分布见 [Insights → Traffic](https://github.com/liangleionline/shift-schedule-assistant/graphs/traffic)。

## 当前版本

| 项目 | 值 |
| --- | --- |
| 最新版本 | **v1.6.7**（versionCode 28） |
| 最低系统 | Android 6.0（minSdk 23） |
| 目标 / 编译 SDK | 36 |
| ABI | arm64-v8a（64 位 ARM，不含 32 位） |
| 技术栈 | Kotlin 1.9.24 · AndroidX · Room 2.6.1 · AGP 8.5.2 · Gradle 8.7 |
| 开源协议 | GPL-3.0 |
| 数据存储 | 本地 Room 数据库 `shift-schedule.db`，从不上传 |

## 功能总览

| 模块 | 能力 |
| --- | --- |
| 首页排班总览 | 周历切换、今日 / 明日双区块、上下班双栏、班长置顶、切回前台自动回到今天 |
| 粘贴导入 | 多格式日期识别、多分隔符切分、上班 / 休息双向推导、姓名模糊匹配、别名记忆 |
| 循环排班 | 按小组轮换，支持任意循环天数、任意日期区间、逐日预览与手动增删 |
| 每日微调 | 点击「当天上班 / 当天休息」即可勾选调整，两栏自动联动 |
| 组织架构 | 多班、多小组的新增 / 改名 / 删除，删除联动清理下属数据 |
| 人员管理 | 新增、改名、调整班长 / 组员、删除（每个小组至少保留一名班长） |
| 排班设置 | 我的姓名高亮、撤销导入（最多 3 次）、按日期区间清空、清空全部 |
| 视觉 | 渐变头部、圆角卡片、柔和投影、胶囊按钮、纯黑状态栏（应用自绘，兼容 Android 15+ / 三星） |

## 使用说明

### 1. 首页排班总览

- 顶部周历显示一周 7 天，只显示日期数字、不显示星期；`上一周 / 本周 / 下一周` 三个按钮切换周范围。
- 点「本周」或任意日期数字即可跳转；打开 App、切回前台都默认停在**今天**并回到本周。
- 中部可切换当前班组。
- 页面主体分「今日」「明日」两个区块，各自左右两栏：当天上班 / 当天休息。
- 人员按小组分组展示，班长排在本组最前并标注「（班长）」；无数据时显示「暂无排班数据」。
- 左右两栏的上班、休息色块与下方姓名卡等宽对齐、中间留白统一。

### 2. 粘贴导入排班

从首页「📋 粘贴导入」进入，粘贴原始文本后点「解析并导入」：

**日期格式**

- `9月25日（周五）`、`10月1日`
- `10.04`、`9.25`

**名单分隔符**：空格、半角逗号、全角逗号、顿号、句号、分号均可。

**上班 / 休息判定**：只认「**上班**」与「**休息**」两个关键词（不再要求带「人员」二字）。

| 识别情况 | 处理 |
| --- | --- |
| 只出现「上班」 | 按上班表导入 |
| 只出现「休息」 | 按休息表导入，上班人员自动取「全员 − 休息人员」 |
| 两个都出现 / 两个都没有 | 弹窗询问用户按哪张表导入 |

**姓名匹配优先级**：已保存别名 → 精确匹配 → 缺字匹配（`小雨`→`张小雨`）→ 同音近音匹配（`小雨`→`张晓雨`）→ 缺字 + 同音组合。

- 唯一候选：静默匹配并记忆别名。
- 多候选：弹窗选择后记忆。
- 完全匹配不到：弹窗可选「忽略」或「新增人员」；**同一次导入里同一个未匹配姓名只询问一次**。

### 3. 循环排班

适用于「A 组上、B 组休，隔天轮换」这类固定节律：

1. 选择导入日期区间；
2. 设置几天一循环（例如 2 天）；
3. 为循环内每一天指定当天上班的小组；
4. 生成逐日预览，预览里可增减当天上班 / 休息人员；
5. 确认后批量写入整个区间。

### 4. 每日排班调整

首页「当天上班」和「当天休息」标题卡都可点击，打开当天全部人员清单（按小组分组）。勾选＝上班、取消＝休息，保存后两栏自动同步：新勾选的人从休息移入上班，取消勾选的人从上班移回休息。循环排班预览里同样是这套交互。

### 5. 组织架构

- 支持多个班、每个班下多个小组的增删改；顶部班名下拉框加宽显示，不再挤成竖排。
- 「编辑班信息」一个入口弹出「改名 / 新增班 / 删除班」三项操作（删除班有二次确认，并联动删除其小组、人员与排班记录）。
- 数据层对班名、`(班, 组名)` 建唯一索引，插入走唯一方法，从源头阻止重复小组；页面渲染采用单一数据快照，避免同一小组被重复渲染成多张卡片。

### 6. 人员管理

- 人员字段：姓名、职位（班长 / 组员，默认组员）。
- 支持按小组查看、新增、改名、改职位、删除（二次确认）。
- 每个小组至少保留一名班长，班长在列表与首页均置顶。
- 从首页进入默认第一个组；在人员管理页内新增人员会保持并切换到实际选择的班 / 组。

### 7. 排班设置

- **我的姓名**：输入自己的姓名后，首页今日 / 明日名单中同名人员加粗并以主题色高亮；可随时修改或清空。
- **撤销上一次导入**：最多连续撤销 3 次，无内容时按钮置灰并显示「暂无可撤销内容」。
- **按时间段清空**：日期选择器指定起止日期，只清除该区间排班。
- **清空全部排班**：二次确认后清空所有排班记录（不影响组织架构与人员）。

## 数据存储

全部数据保存在手机本地 Room 数据库 `shift-schedule.db`，不需要联网，也不会外传。

| 表 | 用途 |
| --- | --- |
| `Team` | 班 |
| `Group` | 小组（`teamId + name` 唯一） |
| `Staff` | 人员（姓名、职位） |
| `NameAlias` | 排班文本姓名 → 人员的别名规则 |
| `ScheduleRecord` | 每日排班（`teamId + dateKey` 唯一，人员 id 列表以逗号串存储） |

> 数据库升级使用破坏性迁移策略：跨大版本升级时可能清空本地数据，升级前请留意 Release 说明。

## 安装

1. 打开 [Releases](https://github.com/liangleionline/shift-schedule-assistant/releases)，下载最新版的 `shift-schedule-assistant-vX.Y.Z-arm64-v8a-debug.apk`。
2. 手机允许「安装未知来源应用」后安装。
3. 仅支持 64 位 ARM 设备（arm64-v8a），32 位机型无法安装。

## 构建

### 环境要求

- JDK 17
- Android SDK Platform 36（compileSdk / targetSdk 36）
- Gradle 8.7（仓库自带 wrapper 配置）+ AGP 8.5.2
- Kotlin 1.9.24、KSP 1.9.24-1.0.20、Room 2.6.1

### 命令行构建

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 仓库包含 `gradle/wrapper/gradle-wrapper.properties`；若本地缺 wrapper jar，用 Android Studio 打开自动补齐，或用本机 Gradle 执行 `gradle wrapper`。

## 目录结构

```text
shift-schedule-assistant/
├── README.md
├── CHANGELOG.md
├── LICENSE
├── build.gradle
├── settings.gradle
├── docs/releases/                     # 各版本说明（RELEASE_vX.Y.Z.md）
├── gradle/wrapper/gradle-wrapper.properties
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/liangleionline/shiftschedule/
    │   ├── ScheduleParser.kt          # 排班文本解析、日期识别、姓名模糊匹配
    │   ├── data/
    │   │   ├── Entities.kt            # Team / Group / Staff / NameAlias / ScheduleRecord
    │   │   └── AppDatabase.kt         # Room 数据库与 DAO
    │   └── ui/
    │       ├── MainActivity.kt        # 首页、周历、粘贴导入、循环排班、每日调整
    │       ├── OrganizationActivity.kt# 组织架构（班 / 小组）
    │       ├── StaffManageActivity.kt # 人员管理
    │       ├── ScheduleSettingsActivity.kt # 排班设置（我的姓名、撤销、清空）
    │       └── Theme.kt               # 配色、控件样式、状态栏处理
    └── res/                           # 图标、主题、布局资源
```

## 版本历史

完整版本演进（含已归档的修补版本）见 [CHANGELOG.md](CHANGELOG.md)。当前仓库保留的功能节点 Release：

| 版本 | 主要内容 |
| --- | --- |
| [v1.6.7](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.6.7) | 开源协议切换为 GPL-3.0（最新） |
| [v1.6.1](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.6.1) | 设置「我的姓名」并在排班中加粗高亮 |
| [v1.6.0](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.6.0) | 全新视觉改版，首页新增「明日」排班 |
| [v1.5.2](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.5.2) | 首页当天排班可直接编辑 |
| [v1.5.0](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.5.0) | 循环排班 |
| [v1.4.0](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.4.0) | 排班设置、撤销导入、模式询问 |
| [v1.2.0](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.2.0) | 多班多组与人员归属 |
| [v1.1.0](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.1.0) | 独立人员管理页 |
| [v1.0.1](https://github.com/liangleionline/shift-schedule-assistant/releases/tag/v1.0.1) | 首个可运行版本 |

## 发布流程

每次发版按以下步骤执行，并**同步更新本 README 与 CHANGELOG**：

1. 递增 `app/build.gradle` 中的 `versionCode` 与 `versionName`；
2. `./gradlew assembleDebug` 构建，用 `aapt dump badging` 校验包名、版本号、启动 Activity；
3. 复制 APK 为 `shift-schedule-assistant-vX.Y.Z-arm64-v8a-debug.apk`；
4. 更新 `README.md`（当前版本、功能说明）与 `CHANGELOG.md`，新增 `docs/releases/RELEASE_vX.Y.Z.md`；
5. 提交、打 tag、推送，创建 GitHub Release 并上传 APK；
6. Release 列表保留**最新 3 个版本**，更早的删除。

## 开源协议

本项目基于 **GNU General Public License v3.0（GPL-3.0）** 开源，完整协议文本见根目录 [LICENSE](LICENSE)。

- 你可以自由使用、复制、修改、再分发本软件及其源代码。
- 任何基于本软件的修改与衍生作品再分发时，必须同样以 GPL-3.0 开源并提供完整源代码，不得转为更严格或专有许可。
- 本软件按“现状”提供，不提供任何明示或默示担保。详见 GPL-3.0 条款：<https://www.gnu.org/licenses/gpl-3.0.html>。

版权所有 © 2026 liangleionline。

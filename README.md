# 班组排班助手（Android）

面向作息不规律班组的本地排班查看与文本导入工具。应用以「班 → 小组」为组织层级，支持人员管理、批量粘贴排班文本、姓名模糊匹配、本周日历切换，以及上班/休息双栏查看。

## 当前版本

- v1.0.0
- minSdk 23（Android 6.0+）
- targetSdk / compileSdk 35
- Kotlin + AndroidX + Material Components + Room

## 功能清单

### 1. 首页排班总览

- 顶部仅显示一周 7 天日期数字，不显示星期。
- 「上一周 / 下一周」按钮切换周范围。
- 打开 App 默认选中今天。
- 点击周内任意日期，下方数据即时刷新。
- 中部可切换班组。
- 下方固定左右两栏：
  - 当天上班
  - 当天休息
- 人员按小组分组展示，班长自动排在本组最前面，并标注「（班长）」。
- 当天无排班数据时显示「暂无排班数据」。

### 2. 首次使用引导

首次启动无组织架构时，自动引导新建班组，并创建默认小组和一名班长，避免空组织无法使用。

### 3. 人员管理

- 人员字段：姓名、职位。
- 职位支持：班长 / 组员，默认组员。
- 每个小组至少保留一个班长。
- 小组列表中班长自动置顶。

> 当前代码提供基础添加人员演示入口；完整多组管理 UI 已预留 Room 数据结构，可在下一版本扩展。

### 4. 排班文本粘贴导入

支持粘贴多行文本并自动解析：

- `上班人员` / 包含「上班」：名单按上班人员处理。
- `人员休息` / 包含「休息」：名单按休息人员处理，系统自动以「全员 - 休息人员」计算上班人员。
- 支持日期：
  - `9月25日（周五）`
  - `10月1日`
  - `10.04`
  - `9.25`
- 支持姓名分隔：空格、半角逗号、全角逗号、顿号、句号、分号。

示例：

```text
上班人员
9月25日（周五）：王旭 李炎 许海 王忠奇 时维桐 王琳 石曼 王晓西 孔飞
10.04：侯晓雨，杜菲，高晗，孔飞
```

### 5. 智能姓名匹配与规则记忆

匹配优先级：

1. 已保存的别名规则。
2. 精确匹配。
3. 缺字匹配，如「小雨」→「侯小雨」。
4. 同音/近音近似匹配，如「小雨」→「侯晓雨」。
5. 缺字 + 同音组合匹配。

处理策略：

- 仅匹配到一个候选：自动匹配，无弹窗，并自动保存别名规则。
- 匹配到多个候选：弹窗由用户选择，选择后保存别名规则。
- 完全无法匹配：弹窗选择已有人员或忽略。
- 后续再遇到同一文本姓名，直接读取别名规则，不再重复询问。

> 当前版本内置常见姓名近似字符集合；生产增强版建议接入 pinyin4j/TinyPinyin 做全量拼音匹配。

## 数据存储

全部数据保存在手机本地 Room 数据库 `shift-schedule.db`，不需要联网。

数据表：

- `Team`：班组
- `Group`：小组
- `Staff`：人员
- `NameAlias`：姓名别名匹配规则
- `ScheduleRecord`：每日排班记录

## 构建方式

### 环境要求

- Android Studio Iguana / Jellfish 或更高版本
- JDK 17（Android Gradle Plugin 8.5 推荐）
- Android SDK Platform 35
- Gradle 8.7

### Android Studio 构建

1. Android Studio 选择 **Open**。
2. 打开本项目根目录 `shift-schedule-assistant`。
3. 等待 Gradle Sync。
4. 连接安卓手机或启动模拟器。
5. 点击 Run 安装运行。

### 命令行构建

如本机已安装 Android SDK 且配置 `ANDROID_HOME`：

```bash
./gradlew assembleDebug
```

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

> 当前交付工程包含 `gradle/wrapper/gradle-wrapper.properties`，但未把二进制 wrapper jar 放入仓库；在 Android Studio 中打开会自动补齐，或用本机 Gradle 执行 `gradle wrapper` 生成完整 wrapper。

## 目录结构

```text
shift-schedule-assistant/
├── README.md
├── build.gradle
├── settings.gradle
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/liangleionline/shiftschedule/
    │   ├── MainActivity 所在 ui 包
    │   ├── ScheduleParser.kt
    │   └── data/
    │       ├── Entities.kt
    │       └── AppDatabase.kt
    └── res/
```

## v1.0.0 已实现范围

- Room 本地数据库结构
- 首次使用默认组织初始化
- 周日历基础切换
- 班组下拉切换
- 上班/休息左右两栏
- 按小组分组、班长置顶
- 多格式日期解析
- 多分隔符姓名切分
- 上班/休息表头识别
- 休息名单自动取反
- 缺字/近音模糊匹配
- 唯一候选自动匹配并记忆
- 多候选/未知人员手动选择或忽略
- 排班记录按「班组 + 日期」覆盖保存

## 后续建议版本规划

### v1.1.0

- 完整组织架构管理 UI：多个班、多个小组的新增/编辑/删除。
- 完整人员管理页：职位修改、人员移动、删除人员。
- 别名规则管理页：查看、编辑、删除映射。
- 新增未知人员后直接加入指定小组。

### v1.2.0

- 接入 TinyPinyin，实现全量拼音/多音字匹配。
- 解析结果预览页，导入前可逐行确认。
- 排班记录管理与清空。

### v1.3.0

- 导出文本、分享当日排班。
- 数据备份/恢复。
- 桌面小组件与日历提醒。

## GitHub 发布建议

建议仓库名：

```text
shift-schedule-assistant
```

初始化命令：

```bash
git init
git add .
git commit -m "feat: initial shift schedule assistant v1.0.0"
git branch -M main
git remote add origin https://github.com/liangleionline/shift-schedule-assistant.git
git push -u origin main
```

创建 Release：

- Tag：`v1.0.0`
- Title：`v1.0.0 初始版本`
- 附件：正式签名后的 `app-release.apk`
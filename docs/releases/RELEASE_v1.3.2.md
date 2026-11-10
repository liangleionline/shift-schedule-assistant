# v1.3.2 强制状态栏图标浅色显示

## 修复

- 废弃旧的 `systemUiVisibility` 状态栏图标控制方式。
- 改用 AndroidX `WindowInsetsControllerCompat` 强制设置状态栏图标为浅色。
- 首页、组织架构页、人员管理页统一设置深蓝黑状态栏背景。
- 修复部分 Android 机型上时间、电量、信号等系统图标仍显示为深色、在深色状态栏上看不清的问题。

## 构建

- versionCode：16
- versionName：1.3.2
- ABI：仅包含 `arm64-v8a`（64 位 ARM）。

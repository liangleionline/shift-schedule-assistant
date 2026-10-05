# v1.6.3 状态栏黑条改为应用自绘（兼容三星 / Android 15+）

## 修复

- 上一版用 `setDecorFitsSystemWindows(true)` 让系统填黑，但在 Android 15/16（targetSdk 36）强制 edge-to-edge、以及三星 One UI 上该开关被忽略，渐变头栏仍顶到最顶部，黑底不出现。
- 本版改为**由应用自己绘制状态栏黑条**：
  - 页面最外层容器底色纯黑，顶部预留出状态栏高度，状态栏区域必然显示一条纯黑；
  - 系统图标/文字通过 `WindowInsetsControllerCompat` 并兼容旧 `systemUiVisibility` 标志，强制为白色；
  - 不依赖系统是否退出 edge-to-edge，因此在三星及各品牌 Android 15/16 上都生效。
- 首页、组织架构、人员管理、排班设置四个页面统一处理。

## 构建

- versionCode：24
- versionName：1.6.3
- 纯 Kotlin/AndroidX 应用，无原生 so 库，不含 32 位原生二进制；按 arm64-v8a（64 位 ARM）配置。
- SHA-256：f6d7b412aba8564bb4cc51b9be534233840af7be9f798b3149b51adc21be0ee9

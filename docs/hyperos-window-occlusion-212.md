# HyperOS 主屏坐标遮挡修复（#212）

`MainScreenWindowPolicy.region` 原先把任何相交但无法按完整边缘裁剪的高层窗口标记为整图遮挡。HyperOS 报告的 1×1 系统占位窗和只覆盖部分屏幕高度的边缘手势条会触发这条路径，截图因此无法提供坐标观察票据。

本次仅调整物理主屏窗口几何策略：

- 有效面积不超过 4 平方像素、非聚焦且非活动的 `SYSTEM` 窗口作为占位窗忽略。应用、无障碍覆盖及其他未知类型没有这项豁免；几何缺失或非法继续拒绝。
- 非交互系统窗和 IME 若贴着当前区域边缘，保守裁掉对应整条边带；角落覆盖选择保留面积最大的有效矩形。手势仍必须完全落在裁剪后的区域，不能穿过被裁掉的部分。
- 完成所有裁剪后，再按最终区域检查其余覆盖。内部真实遮挡、全屏覆盖、交互系统窗不被一概忽略。

无障碍窗口类型来自 `AccessibilityWindowInfo`，并非 `dumpsys window` 的 LayoutParams 类型。不会依据包名、system UID 或截图报告中的 WMS 类型做全局放行。没有修改虚拟屏、观察票据的有效期/单次消费、像素指纹或动作重放规则，也没有混入 `desc`/`nodeId`/`resource-id` 扩展。

验证：新增五项 JUnit 测试在未修复代码上出现两项预期失败（微小系统窗、部分边缘条）；修复后通过。包含原有截图观察、票据、手势、无障碍回归共 13 类、106 项 JVM 测试通过。初次使用 Android Studio JBR25，最终另用 Temurin JDK17.0.20.1 编译并重跑 106 项全部通过。固定 google-java-format 1.22.0 在 JDK17 上完成格式化并核验 Java token 不变。独立只读代码审查未发现需修正问题。

两版完整 Gradle 单测已尝试：离线首次缺少 AGP9.1.1，联网补齐后停在 `prepareBackupAssets`，因为源码检出没有未入库的 `app/src/main/assets/dsh-runtime.bin`；未进入 Android 编译及测试。没有伪造离线资产或跳过这些门禁。Lint、APK 和 HyperOS 真机验收未执行，不以这些 JVM 测试代替。尤其仍需在报告设备核对 AccessibilityWindowInfo 的实际类型/几何，确认 screenshot 签发 observationId、区域内 tap/swipe 可用，真实弹窗、键盘与边缘禁区仍受保护。

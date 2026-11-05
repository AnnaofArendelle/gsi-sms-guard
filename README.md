# GSI 短信修复框架 · GSI SMS Guard

[![CI](https://github.com/AnnaofArendelle/gsi-sms-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/AnnaofArendelle/gsi-sms-guard/actions/workflows/ci.yml)
[![Release](https://github.com/AnnaofArendelle/gsi-sms-guard/actions/workflows/release.yml/badge.svg)](https://github.com/AnnaofArendelle/gsi-sms-guard/actions/workflows/release.yml)
[![GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

一个可以打开管理界面的 Android root 应用，补发高通 **ATEL UI ready** 通知，修复已确认由开机 Power Opt 短信缓冲引起的 GSI 接收问题。

**已验证设备：小米 12 `cupid`，Android 13 DSU/GSI，联通 SIM。** 只上报 ATEL 就绪、保留 Power Opt 默认值 `1`、不挂载配置时，已实际收到验证码。从 0.4.0 起不按机型或 ROM 名称限制修复；检测条件匹配时允许执行，条件不符或尚未检测时可确认警告后选择“仍然尝试修复”。其他设备尚未完成实际收信验证。

[下载正式 Release](https://github.com/AnnaofArendelle/gsi-sms-guard/releases/latest) · [设备验证记录](docs/DEVICE_VALIDATION.md) · [发布维护说明](docs/RELEASING.md)

## 为什么会收不到短信？

这台设备的 GSI 中，IMS 已注册、通话正常、默认短信应用正确，但高通 WMS 收到短信后出现 `MT SMS ACK NEEDED Power Opt buffer length 1`，随后超时清理，短信没有交到 IMS/Android。

设备的 vendor 实现会在 Power Opt 开启、ATEL UI 尚未就绪时缓冲短信。补发固定的就绪通知可以恢复接收。本应用使用 `vendor.qti.hardware.radio.qcrilhook@1.0::IQtiOemHook`，发送 `SET_ATEL_UI_STATUS`（`0x8001A`），不复制运营商 MBN 状态、不刷写基带、不修改 NV 或 SMSC。

这是已经有接收日志和收信对照支持的特定故障。它不能处理所有 GSI、运营商或 Pixel IMS 问题。IMS 显示 SMS 能力可用，也不能单独证明短信接收链路正常。

## 功能

| 功能 | 行为 |
| --- | --- |
| 设备检测 | 检查 DSU/GSI、机型、OEM hook、数据库、RIL 状态、挂载空间和内置修复 |
| 自动框架 | 前台服务接收开机/应用更新事件，每 30 秒检查 RIL 进程及启动时间，变化时重新上报；未识别进程时仍每 5 分钟补发 |
| 手动上报 | 向两个槽位发送已核对的固定 ATEL 就绪请求，单槽位也可使用 |
| 临时挂载备用方案 | 复制 radio 目录，只改副本的 Power Opt 值；临时 bind mount 并重启 RIL，可单独撤销 |
| 故障证据提示 | 只返回短信缓冲、超时和接收回调是否出现；不显示原始日志或短信内容 |

自动模式只上报就绪，不创建配置挂载、不主动重启 RIL。它是用户应用与短时 root worker 组成的自动管理工具；没有安装为系统 APK，也没有修改 init 或 Android framework。

## 安装与使用

1. 从 [Releases](https://github.com/AnnaofArendelle/gsi-sms-guard/releases) 下载 APK；同页提供 `SHA256SUMS` 与签名证书信息。
2. 在目标 Android 系统安装，打开应用，点击 **检测 / 刷新状态**。LineageOS、其他 GSI、设备专用 ROM 和主系统都可以检测与尝试。
3. 允许 Superuser/root 请求。需要后台与开机自动工作时，为应用设置持久 root 授权。
4. 条件匹配时正常启用 **自动框架**；不匹配时先查看原因，再选择 **仍然启用尝试**。允许通知。通过 **打开后台 / 电池设置** 将应用电池使用设为“不受限制”；系统提供自启动开关时允许它。
5. 等通知显示已上报，使用一次受控短信接收测试确认效果。

设备需要已有可用 root。本 APK 不提供 root、解锁或刷机功能。PHH Superuser 的客户端仍经过已有 daemon 的用户授权流程。

### 关闭、恢复与升级

关闭自动框架会停止后续上报，不发送 UI not-ready；已上报状态随 RIL 或手机重启重置。强行停止应用后，需重新打开并恢复服务。

临时挂载必须通过 **撤销临时挂载** 恢复，也可重启清除；卸载 APK 不会自动撤销挂载。ATEL 已有效时无需开启这个备用方案。

正式 Release 使用同一长期签名，允许后续覆盖升级。CI 的 development APK 仅用于检查，每个新 runner 可能生成不同签名，请不要将其当作正式升级包。

## 支持范围与隐私

- **ATEL 推荐条件**：至少一个 OEM hook 的只读 `IBase.interfaceChain` 探测匹配，Power Opt=1 或观察到非空短信缓冲证据，且没有内置修复/现有 radio 挂载。不依赖机型、DSU 标志、数据库存在或主挂载空间。
- **警告覆盖**：接口探测失败、故障未确认、已有内置修复或挂载时，按钮仍可在确认后发起固定 ATEL 请求。自动尝试模式保存独立偏好；接口暂不可用会显示失败并重试。它不能创造 root 或不存在的接口，也不会绕过 SELinux。
- **临时挂载**：独立检查兼容数据库、主挂载空间和可控制 RIL。条件不符也显示“仍然尝试修复”，但预检失败时返回具体错误，不覆盖其他挂载或盲写原配置。RIL 名称不匹配时仍可用 ATEL。
- 随附未修改的 PHH 客户端是 **ARM32 静态程序**，存放在 ARM64 APK 的 nativeLibraryDir，当前设备已实测执行成功。不支持 ARM32 执行的设备不能据此推断兼容。
- APK 没有 `INTERNET`、`READ_SMS`、`SEND_SMS` 或 `READ_PHONE_STATE` 权限；不读取短信正文、验证码或卡号，不自动发送测试短信。
- root worker 可短暂读取 radio 日志做本地事件判断，不保存或返回原始日志，也不自动开启 vendor 调试。
- 非空缓冲事件才算直接证据；长度 `0` 不算故障。历史日志与接口发送成功不能替代收信实测。
- 不关闭 SELinux。root 环境可能被其他应用检测；没有承诺主系统应用完整性检查全部不受影响。

## 验证结果

0.3.0 已通过 APK 构建/签名、手机内 root 授权、模拟 `BOOT_COMPLETED`、更新后自启动、RIL 重启后的自动补发、两个槽位 UI ready=`1`、纯 ATEL 模式实际收信、临时挂载与撤销。

完整手机重启后的最终版启动、长期锁屏/Doze 尚待验证。前台服务仍可能受系统后台限制。手动 RIL 重启测试曾使 vendor `qtidataservices` 出现异常，随后 LTE 注册恢复；自动 ATEL 模式不会主动重启 RIL。

原数据库的 Power Opt 行仍为 `value=NULL, def_val=1`，配置属性表与早期快照一致。整库可由无线服务正常更新，不能用旧文件哈希保证跨重启完全不变。详细事实与限制见 [设备验证记录](docs/DEVICE_VALIDATION.md)。

0.4.0 的策略测试覆盖条件匹配、单/缺失接口、警告覆盖、内置修复与挂载提示、私有挂载空间下的 ATEL、未知进程回退。该版本只在现有小米 12 上进行接口与运行回归，不宣称其他机型已收信验证。

## 本地构建

依赖 Linux、JDK 17、Python 3、`zip`、Android SDK platform 33 与 build tools 34.0.0，无 Gradle/Maven 依赖。

```bash
sdkmanager 'platforms;android-33' 'build-tools;34.0.0'
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
./build.sh
./scripts/test.sh
```

也可以显式指定工具路径：

```bash
GSI_BUILD_TOOLS=/path/to/build-tools/34.0.0 \
GSI_ANDROID_JAR=/path/to/platforms/android-33/android.jar \
GSI_JAVA_HOME=/path/to/jdk-17 ./build.sh
```

输出 `build/GSI-SMS-Guard.apk`。默认生成本地开发签名，密钥不入库。正式签名设置见 [RELEASING.md](docs/RELEASING.md)。

## CI 与 Release

- `main` push、Pull Request、手动触发：ShellCheck、actionlint、Java 编译、APK 打包与签名、20 个兼容策略/警告覆盖/进程解析检查、权限/原生载荷/第三方源码校验、公开文件凭据检查。
- `v*` 标签：校验标签与 manifest 版本一致，使用 GitHub Secrets 中的长期签名构建；检查通过后发布 APK、完整源码归档、签名证书信息与 SHA-256 校验值。
- 外部 Actions 固定到提交 SHA，Dependabot 每月检查更新。CI 默认只读，Release 仅发布 job 获得 `contents: write`。
- CI 不连接手机，不能验证实际 modem 行为。其他设备的实际效果仍需独立进行接收对照；允许尝试不等于已经验证兼容。

## 开源参考与许可

- [lp3-treble / atel](https://github.com/yarosz/lp3-treble/tree/main/atel)：同类 Power Opt 缓冲问题与就绪上报。
- [TrebleDroid PR #88](https://github.com/TrebleDroid/device_phh_treble/pull/88)：开机与 RIL 重启后的 `qcom-atel-ready`。
- [PHH Superuser](https://github.com/phhusson/Superuser)：随附的未修改 native client 与源码。

本项目 [GPL-3.0](LICENSE)。ATEL 协议依据上述公开实现独立编写；没有打包高通固件或库。第三方来源、完整源码归档及校验值见 [NOTICE](NOTICE) 与 [third_party](third_party/)。原客户端的精确原始构建工具链未核实，不宣称二进制可复现构建。

问题反馈请遵守 [贡献说明](CONTRIBUTING.md)，只提供脱敏的事件摘要。

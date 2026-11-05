# GSI 短信修复框架 0.3.0

Android 13 GSI 上的小米 12 cupid 管理应用：检测高通 Power Opt 短信缓冲条件，上报 ATEL UI 就绪，或临时挂载配置副本。

## 当前功能

- 用户可打开管理界面，检测 DSU/GSI、设备、高通 OEM hook、RIL 进程、数据库配置、挂载和原文件哈希。
- 用户开启自动框架后，前台服务带低优先级通知运行；接收 BOOT_COMPLETED，在无线进程 PID/启动时间变化时重新发送 ATEL UI ready。每 30 秒检查一次，另每 5 分钟补发一次。
- 自动模式只发送就绪请求，不创建配置挂载，不修改系统分区、NV/MBN。
- 仅允许当前 DSU/GSI + cupid 执行修改；主系统不执行无线操作。其他设备可以检测，暂不开启修改。
- root 在 init/RIL 的主挂载空间才允许操作；不静默修改 SELinux。
- 挂载备用方案复制整个 radio 目录，只改副本 SQLite 属性，以目录 bind mount 避免 SQLite 备份/日志写到原文件；可撤销，重启消失。
- 所有 worker 修改由文件锁串行化；应用 UI/外部 intent 不提供任意 shell/OEM 请求入口。
- 无 INTERNET、READ_SMS、SEND_SMS、READ_PHONE_STATE 权限。诊断日志仅在 root worker 内短暂读取，输出事件布尔值，原始日志不保存/展示。

## 使用

打开应用 → 检测 → 授予 root → 启用自动框架 → 允许通知。要让开机自动执行不再次弹授权，须在 Superuser 为本应用设持久授权。本次返回 GSI 后与安装更新后均无需再次授权，但未核对授权有效期限，不代表已配置永久权限。

“上报 ATEL UI 就绪”只改变 RIL 内存状态。关闭自动框架停止后续上报，不发送 UI not-ready，状态随无线服务或手机重启重置。临时挂载需单独撤销，卸载 APK 不会执行撤销；重启也可清除。

## 检测能力边界

设备/API/配置匹配不等于根因确诊。日志出现非空 `MT SMS ACK NEEDED Power Opt buffer length` 才提供直接证据；长度 0 表示空缓冲，不能算故障。日志是历史记录，ring 滚动/调试未开启/权限不足时无证据并不能排除故障。工具不自动开启高通诊断日志。最终需要受控收信对照。

HIDL 接口是 oneway，发送成功不等于 modem 已处理，更不等于短信已收到。当前设备已验证请求被 RIL 接受、UI ready=1；用户已确认纯 ATEL 自动框架模式收到验证码，期间没有临时挂载，Power Opt 保持 1。

## 已验证与待验证

已验证：最终版 0.3.0 的构建与签名、安装启动、手机内 root 授权、状态检测、更新广播与模拟 BOOT_COMPLETED 启动、前台服务、轻量进程检查、RIL 重启后自动重发；两个槽位均确认请求被接受，ATEL UI 状态为 1，Power Opt 保持 1。临时挂载原方案已使短信恢复，挂载操作期间被隐藏的原文件哈希一致；此 APK worker 的启用与撤销也已实测。主系统切换或 RIL 自身运行可正常更新数据库，不能将旧快照哈希作为跨重启永不变化的保证。

待验证：最终版完整手机重启后 BOOT_COMPLETED 自启动、锁屏/Doze 长时间运行。Android/厂商可能限制后台和广播，用户强行停止应用后需重新打开。前台服务不承诺永不被杀，不安装 init 系统服务。

## 验证备注

手动重启 RIL 的测试中，vendor 的 `.qtidataservices` 出现 `NetworkCallback was not registered` 异常，随后语音和数据恢复 LTE 注册。APK 无崩溃，自动 ATEL 方式本身不重启 RIL。模拟开机广播验证接收器链路，不能替代完整手机重启测试。

## 构建

依赖 JDK 17、Android SDK platform 33 和 build tools 34。原生 PHH su 作为未改动 ARM32 静态可执行文件（打包于 ARM64 APK 的 nativeLibraryDir；该设备实测支持执行）位于 `payload/lib/arm64-v8a/libphhsu.so`，用于本 GSI 的 SELinux phhsu_exec map 缺失问题，连接既有 root daemon，daemon 保留真实 UID 与用户审批。

```sh
GSI_BUILD_TOOLS=/path/to/build-tools/34.0.0 GSI_ANDROID_JAR=/path/to/platforms/android-33/android.jar ./build.sh
```

此版本开发签名仅供本地试用，正式分发应自行管理发行密钥。第三方源码与许可证见 LICENSE / NOTICE / third_party。

纯解析器测试：`javac --release 8 -d build/tests src/org/gsisms/guard/PollState.java tests/org/gsisms/guard/PollStateTest.java && java -cp build/tests org.gsisms.guard.PollStateTest`；11 个保护与进程变化检查已通过。

最终只读核对：原数据库的 Power Opt 行仍为 `value=NULL, def_val=1`，`qcril_properties_table` 全部属性行与早期快照一致；整库文件哈希不同不能据此认定本修复改写了配置，也不能据此保证其他表或运行数据完全不变。

# Release 维护流程

## 签名配置

Release 只使用持久发行密钥，不回退到开发签名。仓库的 `release` Environment 配置以下 Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | JKS/PKCS12 签名文件的 base64（单行，无换行） |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |
| `ANDROID_KEY_ALIAS` | alias |

初次发布可以保留此前本地试用 APK 的私有签名密钥，让原安装直接升级。该私钥必须作为正式长期密钥妥善备份；不提交到 Git，不放入 Release。所有 Actions 使用自己的短期 `GITHUB_TOKEN`，不需要个人推送令牌。

如果重新生成不同签名，已有应用无法覆盖升级，需要卸载重装，自动框架偏好也会丢失；有临时挂载时先撤销。

本地发行构建例子（密码在当前 shell 环境设置，不放命令行）：

```bash
export GSI_SIGNING_MODE=release
export GSI_RELEASE_KEYSTORE=/secure/path/release.jks
export GSI_KEY_ALIAS=gsisms
# 提前安全设置 GSI_KEYSTORE_PASSWORD 与 GSI_KEY_PASSWORD
./build.sh
./scripts/test.sh
```

## 发布步骤

1. 修改 `AndroidManifest.xml` 的 `versionName` 和递增的 `versionCode`，同步界面版本说明、CHANGELOG。
2. 在 PR/main 上等待 `Build and checks` 成功，并完成适当的设备回归；CI 不能代替收信验证。
3. 确认 `release` Environment 的四项签名 Secrets 已配置。
4. 推送版本标签。例如 manifest 为 `0.3.0`：

   ```bash
   git tag -a v0.3.0 -m 'Release 0.3.0'
   git push origin v0.3.0
   ```

5. Release workflow 校验版本、构建、检查，再由独立 job 发布 GitHub Release。发布资产包括 APK、源码 ZIP、`signing-certificate.txt` 与 `SHA256SUMS`。

工作流限制并发、不取消进行中的发布；不覆盖已有 Release。失败时检查日志并通过 Actions rerun 重试，已有发布不应移动原标签。

GitHub Secrets 不替代离线密钥备份。Environment 可配置维护者审批，按项目维护需要设置；当前自动流程不要求额外审批。

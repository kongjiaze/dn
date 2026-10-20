# 黑石日常助手

在 Android 模拟器内运行的《龙之谷世界》黑石秘境日常助手，当前版本为 0.3.13。通过无障碍手势和系统截图执行操作，运行时不需要电脑 ADB、root 或网络权限。

## 功能

- 六个角色槽独立选择，可配置领取体力、合成纹章和存仓。
- 支持贤者、元素师、魔导师、药剂师和祭祀，配置页提供技能位置示意图及前后站位微调。
- 自动处理登录、选角、领取体力、修理、黑石战斗、开箱和再次挑战。
- 体力用完后返回城镇，按配置传送至魔法山脊、合成非绑定 B 级纹章、存放 A 级纹章，再切换角色。
- 可拖动、收起的悬浮窗；异常自动重试，超过次数后暂停，点击继续可重试当前步骤。

## 使用

1. 安装后按提示开启无障碍、悬浮窗及截图权限。
2. 勾选角色并选择职业，按示意图摆放技能；角色需已解锁相应副本。
3. 可选填写手机号和密码；游戏已登录时可以留空。遇到需要重新登录而未配置账号时，流程会暂停等待处理。
4. 点击“开始自动运行”。需要调整配置时从设置返回角色配置页。

当前画面适配横屏 16:9，主要在雷电模拟器 9、2560×1440 下验证。不同职业、设备速度和游戏界面变化可能需要调整站位或更新识别素材。祭祀只使用天堂审判；该职业完整的自动开箱流程仍待实际运行验证。

## 源码与构建

`android-app/src` 为 Java 源码，`res` 为 Android 资源，`assets` 为已裁剪的识别素材及技能图标。源码不包含账号配置或签名私钥。

构建使用 Windows PowerShell、JDK 17 和 Android SDK Build Tools（包含 `aapt2`、`d8`、`zipalign`、`apksigner`）。当前构建脚本沿用本地 SDK 目录布局：

```text
android-tools/sdk/android-15/           # Build Tools 可执行文件和 lib
android-tools/sdk/android-35-ext15/     # android.jar
```

在 `android-app/build.ps1` 中将 `$javaBin`、`$sdkRoot`、`$buildTools` 和 `android.jar` 路径改为本机实际位置，然后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\android-app\build.ps1
```

输出位于 `android-app/dist/`。首次构建会在本机生成调试签名文件，使用 Android 通用调试口令。该文件不上传 GitHub；请保留自己的签名文件，以便后续覆盖安装。新生成的签名不能覆盖使用其他私钥签名的旧版本。

## 流程检查

仓库包含五项不依赖私人截图的宿主流程检查。需要 Python 3 和 JDK 17；先将各检查脚本的 `JAVA` 变量改为本机 JDK 的 `bin` 路径：

```powershell
python .\android-app\check-combat-policy.py
python .\android-app\check-profile-flow.py
python .\android-app\check-entrance-movement.py
python .\android-app\check-login-flow.py
python .\android-app\check-ultimate-wait.py
```

这些检查使用实际 Java 流程和模拟 Android 环境，不会操作游戏。依赖原始游戏截图的回放实验留在本地，没有随仓库上传。

## 上传前检查

新增或修改文件后，先暂存，再检查 Git 暂存区的实际内容：

```powershell
git add .
python .\tools\check-upload.py
git diff --cached --stat
```

`.gitignore` 使用上传白名单，排除原始截图、录像、运行日志、设备配置、SDK、历史 APK 和私钥。新增文件若被忽略，先检查内容并更新白名单，再暂存。敏感数据处理和本次检查范围见 [SECURITY.md](SECURITY.md)。

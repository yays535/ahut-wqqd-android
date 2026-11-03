# AHUT晚寝签到（Android）

安徽工业大学晚寝签到的安卓客户端。签到逻辑移植自 [Spance/ahut_wqqd](https://github.com/UnthinkingBrain/-_ahut_wqqd) 的 Python 脚本，
所有流程都在本机完成，不经过任何自建服务器。

## 功能

- **多学生**：一个界面管理多人，可分别启用/停用
- **定时随机签到**：每天在设定窗口内随机取一个时刻执行，默认 21:30–22:00
- **失败通知**：签到结果推送到通知栏，失败带原因；窗口内自动重试，最多 3 次
- **位置**：默认用学校登记的宿舍坐标，每次在指定半径内随机偏移（默认 100 米）；也可为每个学生单独指定坐标，随时取消
- **密码**：默认使用学校初始密码，可逐个修改，AES-GCM 加密后存入本机 Keystore
- **签到记录**：按日期分组的本地历史

## 安装

从 [Releases](../../releases) 下载最新 APK 侧载安装（需允许「未知来源」）。

自己构建的话产物在 `app/build/outputs/apk/release/`：
`./gradlew :sign-core:test :app:assembleRelease`（未签名，需自行用 apksigner 签）。

首次启动需要给三项授权，缺一项自动签到就可能不执行：

| 权限 | 用途 | 位置 |
|---|---|---|
| 通知 | 接收签到结果 | 设置 → 系统权限 |
| 精确闹钟 | 准点触发 | 设置 → 系统权限 |
| 电池优化白名单 | 防止后台被清理 | 设置 → 系统权限 |

小米 / 华为 / OPPO / vivo 还需在「手机管家」里允许自启动和后台活动。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :sign-core:test :app:assembleDebug
```

`sign-core` 是纯 Kotlin 模块，负责三个签名算法与坐标偏移，附带 8 个单元测试锁死与 Python 实现的一致性。

## 代码结构

```
sign-core/            纯 Kotlin：签名算法、Python 浮点格式化、圆内随机偏移
  src/test/resources/golden.json  由 tools/gen_golden.py 从原 Python 脚本生成
app/
  data/               Student / AppSettings / RunLog / SignRepository（进程内单例）
  engine/             AhutEngine：六步签到状态机
  work/               Scheduler（随机闹钟）、SignService（前台服务）、Notifier、BootReceiver
  ui/                 Compose 界面：主页、记录、设置
tools/
  gen_golden.py       从 Python 源码生成黄金测试向量
  gen_icons.py        从原图生成各密度图标
```

## 说明

- 非官方工具，与学校无关。学校接口变更时可能失效（历史上 2026-03-24 变更过一次）。
- 签到时间窗由服务端决定，本地只能尽量贴近。
- 只用于**本人**账号的自助签到。不要拿去替别人签到，那属于伪造考勤，出了事没人替你兜。
- 本项目不含任何账号、密码、学号。仓库里的 `users.json`、签名证书、联调日志均已被 `.gitignore` 排除。

## 致谢

签到协议（六步状态机、MD5 签名、`stuTaskId` 紧凑 JSON、坐标偏移策略）来自 **杨国龙** 的 Python 实现
[`UnthinkingBrain/-_ahut_wqqd`](https://github.com/UnthinkingBrain/-_ahut_wqqd)，MIT 许可。

本仓库做的是把它搬到 Android 上：包一层前台服务和精确闹钟做到「熄屏也能按时签到」，
算法在 `sign-core` 里用 Kotlin 重写，并用 `tools/gen_golden.py` 从原 Python 脚本直接生成黄金测试向量，
逐字节对齐才有现在这份可用的 App。协议本身不是我的功劳。

## 许可证

MIT，见 [LICENSE](LICENSE)。上游 Python 实现的版权归原作者，本仓库的 Android 客户端与 Kotlin 移植部分
在原许可下同样以 MIT 发布。

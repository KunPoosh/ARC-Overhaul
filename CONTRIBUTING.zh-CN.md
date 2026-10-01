# 参与 ARC Overhaul（ARC 大修）开发

[English](CONTRIBUTING.md)

这是基于 Acbric 的独立 Airships: Conquer the Skies 玩法 MOD。先阅读 [项目介绍](README.zh-CN.md)、[实现研究](RESEARCH.zh-CN.md)、[测试手册](TESTING.zh-CN.md)。项目自有源码与文档采用 [MIT 许可证](LICENSE)，第三方文件保留原声明。

## 准备开发环境

1. 将仓库克隆到任意目录；本地文件夹名不决定 MOD ID。
2. 安装 JDK 21，把 `JAVA_HOME` 设为其根目录，不能只使用 JRE。目前验证的开发环境为 Windows；集成检查还需要 Python 3.10 或更新版本。
3. 准备提供 API **0.3.3-dev.21 或更新兼容版本**的 Acbric 开发副本/构建。本项目基线为 Acbric 提交 `1be89b2`，不能默认上游主分支已经包含它；如未公开，请向维护者索取匹配修订。按照框架文档构建，包含生成 `loader-libs` 的 `distDir` 任务。
4. 在仓库外准备自己拥有的游戏依赖：`asplit-A.zip`、`asplit-B.zip` 和配套库 JAR。本仓库不提供游戏字节码、资源或反编译文件。
5. 按实际路径构建：

```powershell
.\gradlew.bat build -PacbricDir="D:/Development/Acbric" -PgameLibDir="D:/Games/Airships/libs"
```

默认使用相邻 `../Acbric`，包括其中 `libs` 的游戏库。首次可能下载 Gradle 8.13，缓存准备好后才可加 `--offline`。机器路径可以写在已忽略的项目 `gradle.properties`（`acbricDir=...`、`gameLibDir=...`），建议使用正斜杠。构建不会读取 `local.properties`。

输出仅为 MOD：`build/libs/ARC-Overhaul-0.1.0-dev.9.jar`，不组装完整游戏，也不自动安装。安装时替换旧 ARC JAR；新旧名称的 ID 都是 `arc_overhaul`，不可同时保留启用。

## 开发与评审

- 从 `main` 创建短期功能分支，例如 `feature/conquest-ui`，本地验证后提交 PR。不同玩法尽量分开提交。
- 玩法放在 `conquest/` 等功能包，游戏适配放在 `mixin/`。优先使用现有 Acbric API；涉及通用框架的改动在框架项目讨论。
- 用户界面与文档同时维护英文、中文，界面跟随游戏当前语言。源码文件头和必要注释使用简明中文；第三方文件保留上游注释。
- MOD ID `arc_overhaul`、包名 `net.poosh.arc`、配置键、共享规则版本及存档字段 `arcStartingLayout` 属于兼容契约，除非有经过评审的迁移方案，否则保持不变。产品改名不改变这些标识。
- 修改 Mixin 前核对各目标游戏版本的方法描述符。反编译文本用于研究，不作为编译依赖，也不能代替字节码核对。
- 开局参数不能在读档时重复应用；本地配置与自定义存档字段不等于联机自动同步。
- PR 写清行为变化、测试输入和结果、尚未覆盖的范围。`build` 负责编译/打包；`test NO-SOURCE` 不是测试通过。按改动执行相关[隔离检查及实机用例](TESTING.zh-CN.md)。

## 分享源码与反馈问题

提交自有源码、文档、测试源码和 Gradle wrapper。依赖、游戏文件、反编译材料、运行夹具、日志、存档、凭据及 `build/` 不进入 Git。忽略规则覆盖常见路径/二进制格式，但提交前仍需检查 `git diff --cached` 与 `git status`。源码检出不依赖维护者私有研究目录。

反馈问题时提供版本、启用 MOD、设置、复现步骤和相关日志片段，去掉个人路径和无关存档内容。仓库没有自动发布或凭据配置。创建远端时建议仓库名为 **ARC-Overhaul**；此处不硬编码远端地址。

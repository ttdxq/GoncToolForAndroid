# Gonc Tool For Android

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Go Version](https://img.shields.io/badge/Go-1.20%2B-blue)](https://golang.org)
[![Android API](https://img.shields.io/badge/API-30%2B-green)](https://developer.android.com)

Gonc Tool 是一款基于 Go 语言核心开发的 Android P2P 客户端。它通过集成 `gonc` 和 `tun2socks` 核心，利用 Android `VpnService` 实现了高性能、低延迟的私有网络加密访问。


---

## ✨ 核心特性

*   **高性能 P2P 通讯**：基于 `gonc` (v2) 协议，实现端对端的加密隧道。
*   **Android 11+ 支持**：集成特殊的 `anet` 补丁库，通过 Netlink `RTM_GETADDR` 绕过系统对网卡信息的封锁，保证在 Android 11 及以上系统上稳定发现网络接口。
*   **全协议支持**：完美支持 **IPv4 & IPv6** 全局代理。
*   **JNI 桥接架构**：使用 `gomobile` 将 Go 核心封装为 AAR 库，通过原生内存访问传递文件描述符（FD），比运行二进制文件的方案更安全、更高效。

---

## 🛠️ 技术架构

项目采用了清晰的分层设计：

```mermaid
graph TD
    A[Android UI / Kotlin] --> B[GoncVpnService]
    B -->|JNI| C[Gobridge / Go AAR]
    C --> D[tun2socks / Core]
    C --> E[gonc / P2P Core]
    D -->|FD Passing| F[Android VpnInterface]
    G[anet Library] -.->|Patch| D
```

---

## 🚀 快速开始

### 编译流程

由于本项目包含复杂的 Git 子模块依赖，请务必使用递归克隆：

1.  **拉取源码**:
    ```bash
    git clone --recursive https://github.com/ttdxq/GoncToolForAndroid.git
    ```

2.  **编译 Go AAR 库**:
    确保已安装 `gomobile` 并配置了 `ANDROID_HOME`。
    ```powershell
    # 运行编译脚本
    ./build_binaries.ps1
    ```

3.  **编译 Android APK**:
    在 Android Studio 中打开项目，或者直接运行：
    ```powershell
    ./gradlew assembleDebug
    ```

### 使用指南

> [!TIP]
> **重要提示**：本应用仅支持作为 P2P 客户端使用。在使用前，请确保您已在服务端启动了 `gonc` 隧道：
> ```bash
> gonc -p2p <您的口令> -linkagent
> ```

1.  启动 App。
2.  输入您的 **P2P Secret Key**。
3.  配置需要代理的 **CIDR 路由**（例如 `0.0.0.0/0` 为全局代理，`::/0` 为全局 IPv6）。
4.  点击 **启动 VPN** 按钮，授予系统 VPN 权限。

---

## 📜 开源协议与声明

*   **主项目**: [MIT License](LICENSE)
*   **核心依赖**:
    *   [gonc](https://github.com/threatexpert/gonc) (MIT) - 稳定性增强版
    *   [tun2socks](https://github.com/xjasonlyu/tun2socks) (MIT)
    *   [anet](https://github.com/wlynxg/anet) (BSD 3-Clause) - Android 11+ 兼容补丁版

详细声明请参见 [NOTICE](NOTICE) 文件。

---

## 🤝 贡献与反馈

欢迎提交 Issue 和 Pull Request！我们特别欢迎针对UI/UX 改进以及多平台兼容性的建议。

---
*Created by [ttdxq](https://github.com/ttdxq)*

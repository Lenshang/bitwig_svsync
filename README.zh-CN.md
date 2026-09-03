# SV2 → Bitwig 游标同步（空格触发）

[English](README.md) | 中文

在 Synthesizer V Studio 2 PRO（作为 Bitwig 插件运行）中按 **空格**：
脚本读取 SV 播放头位置，Bitwig 自动跳转到对应位置并开始播放。

不需要 AutoHotkey、loopMIDI、Python、npm——所有桥接逻辑都在一个
Bitwig 扩展内完成。使用体验类似 ARA 插件：SV 编辑器窗口就像 DAW 的
一部分，而不是一个割裂的外部窗口。

同时支持 Dreamtonics 旗下的另一款产品 **Instrument X**：把
`sv2_cursor_sync.js` 放入
`C:\Users\<你>\AppData\Roaming\Dreamtonics\Instrument X\scripts\`，
同样在 Settings → Shortcuts 里绑定空格，即可在其插件窗口中获得相同的
同步体验。

## 工作原理

在 SV2 内按空格（快捷键绑定到 JS 脚本），脚本自动判断运行模式：

- **插件模式**（hostName 含 "Plugin"）：读取播放头秒数 → 向剪贴板写入
  `SVSYNC:P:<秒>` → `SVCursorSync.bwextension` 扩展轮询剪贴板发现标记、
  恢复原剪贴板内容 → 换算成拍（按播放头拍/秒实测比值；原点附近用
  tempo 归一化值在 20~666 BPM 范围内反算）→ `setPosition` + `play`
  （主线程调度）。
- **独立模式**：直接调用 `SV.getPlayback().play()` 本地播放。

## 安装（只需两步）

1. **Bitwig 扩展**：把 `svsync-ext/SVCursorSync.bwextension` 复制到你的
   Bitwig 扩展目录，例如
   `C:\Users\<你>\Documents\Bitwig Studio\Extensions\`。
   然后在 Bitwig 中 Settings → Controllers → Add Controller，按 Vendor
   **bitwig_svsync** / Product **SV Cursor Sync** 添加并启用（不占用任何
   MIDI 端口）。扩展基于 API 23（Bitwig 6.x）。
2. **Synthesizer V Studio 2**：把 `sv2_cursor_sync.js` 复制到 SV2 的
   脚本目录：
   `C:\Users\<你>\AppData\Roaming\Dreamtonics\Synthesizer V Studio 2\scripts\`。
   然后在 SV2 中打开 **Settings → Shortcuts**：先移除空格默认的 "Play"
   绑定，再把空格绑到脚本的 "sv2 cursor sync" 条目（在 Tools > Scripts
   命令列表中）。SV2 中无论脚本快捷键还是常规快捷键都在
   Settings → Shortcuts 里修改。

## 使用方法

两端装好之后：

1. 在 Bitwig 轨道上加载 Synthesizer V Studio 2 PRO 作为乐器插件。
2. 在 SV2 时间轴上点击定位播放头到你希望开始播放的位置。
3. 在 SV2 插件窗口内按 **空格**，Bitwig 即跳转到对应位置并开始播放。

独立模式下，同一快捷键就是普通的本地播放开关。

## 调试通道（不走剪贴板，直接测试 Bitwig 侧）

向 `127.0.0.1:8890` 发送文本（带换行）：

    GO 32.5     -> 跳到 32.5 秒并播放
    STOP        -> 停止

## 已知限制

- 位置换算 `拍 = 秒 × BPM / 60` 使用工程当前 BPM；速度渐变区间内会有偏差。
- 剪贴板备份只覆盖纯文本；触发同步瞬间若剪贴板里是图片/文件，会被清为空文本。
- SV2 停止状态下 `getPlayhead()` 返回播放头位置；若你的定位方式不移动播放头，
  需先在时间轴上点击定位。
- 两端剪贴板操作存在理论上的竞争窗口，偶发失败时再按一次空格即可。

## 开发排障记录

- **必须用 JavaScript，不能用 Lua**：SV2 2.2.1 的 Lua 绑定调用
  `setHostClipboard` 会抛内部错误（中文错误信息在弹窗中显示为乱码）；
  JavaScript 版正常。
- SV2 错误弹窗显示中文会乱码，英文正常。调试时可用
  `throw new Error("ASCII 诊断信息")` 把信息"打印"到弹窗。
- Bitwig 扩展 jar 必须包含
  `META-INF/services/com.bitwig.extension.ExtensionDefinition`
  （内容为定义类全名），否则日志报 "No extensions found"；且类必须在包内
  （不能用默认包）。
- Bitwig 的 JVM 是 headless 模式，AWT 剪贴板不可用；扩展内用 JNA 调
  Win32 API 读写剪贴板（JNA 已打进 fat jar）。

## 文件

- `sv2_cursor_sync.js` — SV2 侧一次性脚本（绑空格）
- `svsync-ext/` — 全合一 Bitwig 扩展（剪贴板轮询 + TCP 调试通道）源码与
  已编译的 `SVCursorSync.bwextension`

### 重新编译扩展

    javac -nowarn -cp "lib/extension-api-23.jar;jna.jar" -d build src/svsync/*.java

然后把 `jna.jar` 解压进 `build`（排除 `META-INF/MANIFEST.MF`），保留
`build/META-INF/services/com.bitwig.extension.ExtensionDefinition`，再执行：

    jar --create --file SVCursorSync.bwextension -C build .

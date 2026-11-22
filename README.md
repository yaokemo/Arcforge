# ArcForge（Beta）

Android 版 Arcaea 的本地改动工具：立绘/图标替换、搭档改名、录制与回放操作。

> **本工具不包含任何联网功能，不收集、不上传任何数据。**
> 所有改动都发生在本地；游戏本体的网络行为不受本工具影响。

## 功能

- **立绘替换**：从相册选图，拖动/双指缩放裁剪，写入 `char/1080/<id>.png`
- **图标替换**：菱形裁剪，输出 170×170，四角透明
- **搭档改名**：改写 `assets/tl/*.mo` 翻译表（部分搭档无条目，无法改名）
- **立绘预设 A/B**：两套立绘快速切换
- **录制 / 回放**：记录真实触摸，回放时按时间轴重新注入
- **备份**：录制可导出/导入 zip
- **界面**：半透明深色 UI、配色可调、悬浮按钮可隐藏（音量键唤出）

## 原理

### 1. 资源重定向（native）

`libarcshim.so` 在构造函数里给已加载模块打 GOT 补丁，接管：

```
AAssetManager_open / AAsset_read / AAsset_getLength / AAsset_close
```

读取时先查镜像目录：

```
/sdcard/Android/data/moe.low.arc/files/arcmod/assets/<name>
```

存在就用镜像文件，否则回退到 APK 内原始资源。

### 2. UI 层（Java）

`arcmod.Overlay` 打进 `classes4.dex`，在 `MainActivity.onCreate` 中：

```java
System.loadLibrary("arcshim");
arcmod.Overlay.attach(this);
```

悬浮按钮、对话框、触摸注入都在这里。

### 3. 录制

给游戏的 `SurfaceView` 设置 `OnTouchListener`（返回 `false`，不影响游戏），
记录每个事件：`时间;action;指针id,x,y;...`，保存为文本。

### 4. 回放

后台线程按录制时间轴到点投递，主线程构造 `MotionEvent` 派发给 `decorView`。
多指用 `PointerProperties` / `PointerCoords`，注意 `ACTION_POINTER_DOWN/UP` 的索引位。

### 5. 触发时机

shim 记录游戏加载战斗场景资源（`layouts/ingame/`、`img/gamescene/`）的时刻，
Java 侧轮询状态文件，检测到变化即自动开始录制/回放。

## 构建

See [BUILD.md](BUILD.md).

## 已知问题

- 回放不是逐帧复刻：录制时若游戏卡顿会留下空档，回放会如实重放（表现为延迟/漏键）
- 建议先重启游戏，再录制 / 回放；录制结束会提示是否有 >500ms 中断
- 与游戏版本强相关：每次大版本更新后需要重新注入

## 免责声明

1. **版权归属**：本程序仅供逆向工程学习与技术探讨，所涉及的原游戏版权均归属原游戏制作与发行公司所有。
2. **免责条款**：本程序完全免费共享，严禁用于任何商业倒卖、代练牟利或非法用途。因传播、违法使用等一切行为所引发的一切后果均由使用者全部承担，开发者概不负责。
3. **清理义务**：本程序不包含游戏本体；请支持正版并遵守官方协议。
4. **维权处理**：若本程序相关内容侵犯了您的合法权益，请权利人携带相关证明与开发者联系，核实后我们将立刻停止维护。

联系邮箱：ykm956km@gmail.com

## License

MIT

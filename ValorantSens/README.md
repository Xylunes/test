# 🎯 无畏契约灵敏度调节器

无畏契约手游专用灵敏度调节 App，支持腰射、开镜、陀螺仪三项独立调节。

---

## 📁 项目文件结构

```
ValorantSens/
├── app/src/main/
│   ├── AndroidManifest.xml                  ← 权限声明
│   ├── java/com/valorantsens/
│   │   ├── MainActivity.kt                  ← 主界面（权限引导 + 灵敏度设置）
│   │   ├── SensitivityConfig.kt             ← 配置数据结构 + 本地存储
│   │   ├── SensitivityEngine.kt             ← 核心算法（线性/分段映射）
│   │   ├── SensitivityAccessibilityService.kt ← 无障碍服务（触控拦截 + 陀螺仪）
│   │   ├── OverlayService.kt                ← 悬浮窗服务（悬浮球显示）
│   │   └── ControlPanelController.kt        ← 悬浮面板的滑块逻辑
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml            ← 主界面布局
│       │   ├── view_float_ball.xml          ← 悬浮球样式
│       │   └── view_control_panel.xml       ← 游戏内调节面板
│       ├── drawable/                        ← 深色主题圆形背景
│       ├── values/
│       │   ├── strings.xml
│       │   └── themes.xml                   ← 无畏契约红色主题
│       └── xml/
│           └── accessibility_service_config.xml
├── app/build.gradle                         ← App 依赖配置
├── build.gradle                             ← 项目配置
└── settings.gradle
```

---

## 🚀 导入步骤（新手友好）

### 第一步：安装 Android Studio
1. 访问 https://developer.android.com/studio
2. 下载并安装（Windows/Mac 均可）
3. 首次启动时选择 Standard 安装，等待 SDK 下载完成

### 第二步：导入项目
1. 打开 Android Studio
2. 点击 **File → Open**
3. 选择 `ValorantSens` 文件夹
4. 等待 Gradle Sync 完成（底部进度条）

### 第三步：连接手机
1. 手机开启「开发者选项」（设置 → 关于手机 → 连击版本号7次）
2. 开启「USB调试」
3. 用数据线连接电脑
4. 手机弹出授权提示 → 点击「允许」

### 第四步：运行
1. 点击 Android Studio 顶部绿色 ▶ 按钮
2. 选择你的手机
3. 等待安装完成，App 自动打开

---

## 📱 使用方法

### 首次使用
1. 打开 App，点击「**授权悬浮窗权限**」→ 在系统设置里开启
2. 点击「**开启无障碍服务**」→ 找到「无畏契约灵敏度」→ 开启
3. 调节滑块到你想要的灵敏度
4. 点击「**▶ 启动悬浮球**」
5. 切换到无畏契约游戏，悬浮球会浮在游戏上方

### 游戏内调节
- **点击悬浮球** → 展开调节面板，可实时修改
- **拖动悬浮球** → 移动到不挡视线的位置
- **面板右上角 ✕** → 收起面板

### 推荐初始值
| 参数 | 推荐值 | 说明 |
|---|---|---|
| 腰射视角 | 1.0 | 先从默认开始 |
| 开镜/瞄准镜 | 0.6 | 比腰射慢，更精准 |
| 陀螺仪强度 | 0.8 | 开启后倾斜手机辅助瞄准 |

---

## ⚠️ 注意事项

1. **无障碍服务必须开启**，否则陀螺仪手势注入不生效
2. **游戏包名确认**：打开 `accessibility_service_config.xml`，
   确认 `packageNames` 是无畏契约手游的实际包名
   - 查询命令：`adb shell pm list packages | grep riot`
3. 首次运行建议先用**中预设**，打几局再微调
4. 陀螺仪建议在熟悉基础操作后再开启

---

## 🔧 后续扩展（你熟悉后可以加）

- [ ] 贝塞尔曲线编辑器（更精细的曲线调节）
- [ ] 多配置方案管理（比赛配置/练习配置）
- [ ] 开镜状态自动识别（截图像素比对）
- [ ] 支持其他游戏（COD手游、和平精英等）

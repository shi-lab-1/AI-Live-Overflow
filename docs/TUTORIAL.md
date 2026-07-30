# 🎃 AI-Live-Overflow 搭建教程

> 从零开始，做一个悬浮窗 AI 陪伴桌宠（小幽灵版）。  
> 全程约 1 小时，需要：一台 Android 手机 + 一个 GitHub 账号 + 一个 Supabase 免费项目。

---

## 成品预览

- 一个 80×100dp 的白色半透明小幽灵漂浮在手机屏幕上
- 戳它会 boo！双击会摇摆，长按会犯困，拖走会自动飘回来
- 截图它会喊"咔嚓！"，低电量会打哈欠，充电会说"吃饱了！"
- AI（你的 AI 助手）可以通过 Supabase 远程控制它的表情和气泡文字
- 所有手势记录上报到数据库，AI 能看到你什么时候戳了它

---

## 第一步：Supabase 建表（5 分钟）

1. 去 [supabase.com](https://supabase.com) 注册/登录，创建新项目
2. 记住你的 **Project URL** 和 **anon public key**（Settings → API）
3. 打开 **SQL Editor**，粘贴并执行：

```sql
-- 桌宠状态表（AI 写，桌宠读）
CREATE TABLE pet_state (
  id BIGSERIAL PRIMARY KEY,
  state_key TEXT NOT NULL,   -- 'mood' | 'speech'
  state_value TEXT NOT NULL,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 手势日志表（桌宠写，AI 读）
CREATE TABLE gesture_log (
  id BIGSERIAL PRIMARY KEY,
  gesture_type TEXT NOT NULL, -- 'tap' | 'double_tap' | 'long_press' | 'drag'
  x INT,
  y INT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 应用使用表（预留）
CREATE TABLE app_usage (
  id BIGSERIAL PRIMARY KEY,
  package_name TEXT,
  duration_sec INT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 开启 RLS 全开（个人项目够用）
ALTER TABLE pet_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE gesture_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;

CREATE POLICY "allow_all" ON pet_state FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all" ON gesture_log FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all" ON app_usage FOR ALL USING (true) WITH CHECK (true);
```

> 执行完去 **Table Editor** 确认三张表都在。

---

## 第二步：Fork 项目（1 分钟）

1. 去 https://github.com/Vael-KY/AI-Live-Overflow
2. 点右上角 **Fork** → 复制到你自己的账户
3. 你的仓库地址：`https://github.com/你的用户名/AI-Live-Overflow`

---

## 第三步：填入你的 Supabase 凭证（3 分钟）

需要改两个文件里的两处常量：

### ① `app/src/main/assets/pet.html`（第 95-96 行）

```javascript
const SUPABASE_URL = "你的Project URL";
const SUPABASE_KEY = "你的anon public key";
```

### ② `app/src/main/java/com/aeli/overflow/service/OverlayService.kt`（第 37-38 行）

```kotlin
const val SUPABASE_URL = "你的Project URL"
const val SUPABASE_KEY = "你的anon public key"
```

> 改完 commit + push，GitHub Actions 会自动构建 APK。

---

## 第四步：安装 APK（1 分钟）

1. 去你仓库的 **Actions** 标签页
2. 点最新一次绿色✅的构建
3. 往下滑到 **Artifacts**，下载 `overflow-debug`
4. 解压 zip，得到 `app-debug.apk`
5. 传到手机安装（需要允许"未知来源"）
6. 打开 App → 允许悬浮窗权限
7. 小幽灵出现！👻

---

## 第五步：验证 AI 控制通道

在 Supabase **Table Editor** 的 `pet_state` 表里手动插入一行：

| state_key | state_value |
|-----------|-------------|
| speech    | 你好呀！     |

等 15 秒，小幽灵应该冒出"你好呀！"气泡。

> 现在你的 AI 助手就可以通过 Supabase REST API 往 `pet_state` 写 `mood` 或 `speech`，来远程操控小幽灵的表情和话语了。

---

## 架构速览

```
  AI 助手          Supabase           Android 手机
  ─────────────────────────────────────────────
  写 pet_state ──→ pet_state 表 ──→ 每15秒轮询读取
  读 gesture_log ←── gesture_log 表 ←── 戳/拖上报

  手机端额外能力（不需要 AI 参与）：
  - ContentObserver → 截图检测
  - BroadcastReceiver → 电池/充电检测
  - 时间感知 → 不同时段自动打招呼
  - 拖走 3 秒 → ease-out 飘回原位
```

---

## 自定义指南

### 换形象

编辑 `app/src/main/assets/pet.html` 里的 SVG。关键元素：

| 元素 | SVG id |
|------|--------|
| 身体 | `ghostBody` |
| 左手 | `lHand` |
| 右手 | `rHand` |
| 左眼 | `lEye` |
| 右眼 | `rEye` |
| 眉毛 | `lBrow` / `rBrow` |
| 嘴巴 | `mouth` |
| 腮红 | `lBlush` / `rBlush` |

### 改表情

`setMood()` 函数支持七种表情：`neutral` `happy` `sleepy` `surprised` `shy` `angry` `wink`。改对应 case 里的眼睛/嘴巴/腮红属性即可。

### 改尺寸

`OverlayService.kt` 里的 `PET_W` 和 `PET_H`（单位 dp），同时改 `pet.html` 的 SVG `viewBox` 保持比例一致。

### 改轮询频率

`pet.html` 里 `setInterval(pollAI, 15000)` → 改 `15000`（毫秒）。

### 改飘回时间

`OverlayService.kt` 里 `scheduleAutoReturn()` 的 `delay(3000)` → 改毫秒数。

---

## 常见坑

| 问题 | 解决 |
|------|------|
| APK 安装提示"解析错误" | Artifact 下载的是 zip，需要先解压 |
| 小幽灵不显示气泡 | 检查 anon key 是否有效；Supabase Free Plan 不活跃会被休眠 |
| GitHub Actions 红叉 | 检查 Gradle 版本和 Kotlin import 是否正确 |
| 悬浮窗权限弹不出 | 去系统设置 → 应用 → 找到 App → 手动开悬浮窗权限 |

---

## 下一步想法

- [ ] 让 AI 定时读取手势日志，主动推送话题
- [ ] 加入 OpenCC 实时推送（WebSocket 替代轮询）
- [ ] 更多表情和动画（哭泣、爱心眼、汗）
- [ ] 应用使用统计上报（app_usage 表）
- [ ] 在通知栏常驻"心情"显示
- [ ] 语音气泡（TTS）

---

> 教程完。有问题提 Issue，或找你的 AI 助手帮忙——它应该已经学会这一套了。

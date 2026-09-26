# 智学网手表版 · 前端 UI 需求与交接文档

> 目的：你要重新设计这个手表 App 的 UI，这份文档给你**做界面所需的全部依据**：
> 有哪些页面、每页要展示什么数据、数据从哪来、有哪些必须保留的交互约束，
> 以及**哪些可以随便改、哪些改了就会把功能改坏**。
>
> 对应版本：zhixue-wear v1.0（4 项功能修复已合入）
> 最后更新：2026-09-23

---

## 目录

- [1. 项目与目标设备](#1-项目与目标设备)
- [2. 技术栈与工程约束](#2-技术栈与工程约束)
- [3. 信息架构与导航图](#3-信息架构与导航图)
- [4. 全局交互规范](#4-全局交互规范)
- [5. 逐页 UI 需求](#5-逐页-ui-需求)
- [6. 设计令牌（当前值）](#6-设计令牌当前值)
- [7. ★ UI ↔ 逻辑契约（必读）](#7--ui--逻辑契约必读)
- [8. 数据字典](#8-数据字典)
- [9. 状态与异常](#9-状态与异常)
- [10. 验收清单](#10-验收清单)

---

## 1. 项目与目标设备

### 1.1 这是什么

非官方智学网手表客户端。核心链路：**WebView 承载官方登录页完成极验人机验证 → 回收 Cookie → 原生界面展示成绩/考试/作业/私信**。

### 1.2 目标设备（真机实测）

| 项 | 值 |
| :--- | :--- |
| 型号 | `OWW211`（QUALCOMM，OPPO/HeyTap 系） |
| Android | 11（API 30） |
| CPU | armeabi-v7a |
| **物理分辨率** | **378 × 496 px**，density 320 |
| **换算后设计画布** | **≈ 189dp × 248dp（矩形屏）** |
| WebView | `com.android.webview` 83.0.4103.120 |

同时兼容圆屏（Wear OS 454×454 px / 227dp 圆形，已在模拟器验证）。

### 1.3 ★ 画布极小 —— 这是本次设计的核心约束

```
矩形屏（真机）                圆屏（Wear OS）
┌───────────────┐ 248dp      ╭───────────────╮ 227dp
│               │            │   ╭───────╮   │
│   189dp 宽    │            │   │ 内容区 │   │
│               │            │   │≈160dp │   │
│               │            │   ╰───────╯   │
└───────────────┘            ╰───────────────╯
```

- **宽只有 189dp**：约等于 3 个 56dp 按钮横排就到头了。当前主菜单按钮高 56dp、间距 7dp，一屏只能放 4 个多一点（需要滚动）。
- **圆屏更惨**：`BoxInsetLayout` + `app:boxedEdges="all"` 会把内容收进内接正方形，**可用区只剩约 160×160dp**。当前所有页面都套了这一层。
- 因此：**优先纵向列表 + 大点击区（≥48dp）**，不要做横向密集布局；字号建议正文 12–15sp，标题 13–17sp。

> 圆屏适配策略可选：保持 `boxedEdges="all"`（安全、不裁切）或改为贴边 + 手动 padding（空间大但四角可能被圆角吃掉）。选后者请务必在圆屏模拟器上回归。

### 1.4 设备怪癖（会影响体验，设计时要考虑）

| 怪癖 | 说明 |
| :--- | :--- |
| 充电时被系统 UI 全屏接管 | 该表充电时会弹 `com.heytap.wearable.systemui` 充电动画，**完全盖住 App**，`adb screencap` 也只能拍到充电画面。拔掉充电线才正常 |
| 息屏会挂起 adbd | 调试时需保持亮屏 |
| 登录页会弹软键盘 | 输入框被 focus 时输入法会弹出（已在代码里规避，见 §4.4） |

---

## 2. 技术栈与工程约束

| 项 | 值 | 说明 |
| :--- | :--- | :--- |
| 语言 | **Java**（无 Kotlin） | 换 Kotlin/Compose 属于重写，不在本次范围 |
| 构建 | AGP 8.5.2 / Gradle 8.7 | `./build.sh :app:assembleDebug` |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 | minSdk 26 = Android 8.0 |
| 依赖 | `androidx.wear:wear`、`androidx.recyclerview`、`okhttp` | 无 ConstraintLayout、无 Material Components |
| 布局 | XML（`app/src/main/res/layout/`） | |
| 主题 | `WearTheme`（`values/styles.xml`），父主题 `Theme.DeviceDefault` | 深色 |

**可用的控件能力**：
- `androidx.wear.widget.BoxInsetLayout`（圆屏安全区）
- `androidx.recyclerview.widget.RecyclerView`（所有列表页）
- 原生 `Button` / `TextView` / `EditText` / `FrameLayout` / `LinearLayout` / `ScrollView` / `ProgressBar` / `AlertDialog`
- 想用 ConstraintLayout 或 Material 组件需要先加依赖（可加，但要重新构建验证）

> ⚠️ **没有** `setInitialScale()`（该 SDK stub 里不存在，WebView 相关代码用 `setTextZoom()` 代替）。

---

## 3. 信息架构与导航图

```
                    ┌──────────────────┐
                    │   MainActivity   │  ← 启动页 / 主菜单
                    │   [退出] 账号     │
                    └────────┬─────────┘
                             │
      ┌──────────┬───────────┼───────────┬──────────┐
      ▼          ▼           ▼           ▼          ▼
  成绩        考试        作业        私信        关于
  Marks    Exams     Homework     Messages     About
             │                                    │
             ▼                                    ▼
      ExamDetail                          CookieLogin
      (科目+错题本)                        (手动登录/账号设置)

  未登录 / 登录态失效 ──→ LoginWebActivity（WebView 登录 + 极验验证码）
                              │ 登录成功（服务端双重校验通过）
                              └──→ 保存 Cookie，回到调用方
```

**导航规则**

| 场景 | 行为 |
| :--- | :--- |
| 未登录时点任意功能菜单 | 不进功能页，直接跳 `LoginWebActivity` |
| 登录态失效（接口返回 3002 等） | 清本地会话 → 跳 `LoginWebActivity` → `finish()` 当前页 |
| 二级页左上角「返回」 | `finish()` 回上级 |
| 主界面左上角「退出」 | `finishAndRemoveTask()` + `moveTaskToBack()`，回到表盘 |
| 主界面按系统返回键 | 等同「退出」 |
| 登录页按返回键 | 先回退 WebView 历史，无历史则 `finish()` |

---

## 4. 全局交互规范

### 4.1 顶部条（当前所有页面统一）

主界面与列表页顶部都是 **「左按钮 + 居中标题」** 结构，用 `FrameLayout`：

```
┌──────────────────────────────────────┐
│ [退出]          20230101              │  ← 按钮 46×34dp，标题左右各留 50dp 防遮挡
├──────────────────────────────────────┤
```

- 主界面左按钮文案 **退出**
- 二级页左按钮文案 **返回**
- 标题 `maxLines=1` + `ellipsize="end"`

> 50dp 的左右 padding 是为了不让长标题压到按钮上，改设计时保留这个避让。

### 4.2 点击区尺寸

所有可点元素 **≥ 48×48dp**（小屏手指更粗）。当前菜单按钮 56dp 高、左上角小按钮 34dp 高（略小，可考虑加大）。

### 4.3 会话失效必须自动跳登录（不要改成弹错误）

接口返回「未登录 / 登录失效 / `errorCode 3002`」时，**必须**：
清本地会话 → 跳登录页 → 结束当前页。

判断逻辑已封装在 `ListActivityBase.isSessionExpired(String)`，改 UI 时**不要绕过它**。

### 4.4 ★ 登录页（LoginWebActivity）的特殊约束

这是全 App 最容易做坏的一页，请重点看：

| 约束 | 原因 |
| :--- | :--- |
| **WebView 必须占据主体且可完整点击** | 极验验证码（Geetest）由官方页面在 WebView 内渲染，用户要**用手指点选/拖拽**。任何覆盖在它上面的遮罩、浮层、手势拦截都会导致验证做不了 |
| 顶部提示条只占一条细窄区域 | 它同时承担「状态显示」和「点按重新填充账号」两个功能，不接收穿透之外的点击 |
| **不要给登录页加周期性刷新/聚焦** | 对输入框 `focus()` 会弹软键盘；曾经的每 2 秒重填导致"验证码弹窗时输入法反复弹出" |
| 自动填充只做两件事 | 赋值 + 补发 `input`/`change`/`keyup` 事件；**不 focus、不派发 blur** |
| 登录成功判定不可改 | 必须「`getCurrentUser` 返回 role」**且**「`getToken` 返回 token」双通过。只看其一会出现"没验完就报成功" |
| 不要用 URL 变化当成功条件 | 页面可能用 JS 跳转不触发 `onPageFinished`，会漏判 |

登录页当前结构：

```
┌──────────────────────────────┐
│ 提示条（状态 / 点按重填）      │  tv_hint   ← 半透明黑底、10sp、顶部居中
├──────────────────────────────┤
│                              │
│      WebView（官方登录页）      │  web       ← 撑满剩余全部空间
│      含极验验证码弹窗           │
│                              │
│         [加载指示]            │  progress
└──────────────────────────────┘
```

---

## 5. 逐页 UI 需求

> 每页给出：**布局文件 / Activity / 必需的 view id / 要展示的数据 / 交互 / 状态**。
> "必需的 view id"指 Java 用 `findViewById` 引用的，**删了或改名会直接崩**。见 §7。

### 5.1 主菜单 MainActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | `res/layout/activity_main.xml` |
| 类 | `MainActivity.java` |

**必需 view id**

| id | 控件 | 用途 |
| :--- | :--- | :--- |
| `btn_exit` | Button | 左上角「退出」，关任务回表盘 |
| `tv_user` | TextView | 居中显示当前账号 |
| `btn_login` | Button | 「登录」，**未登录才显示（GONE）** |
| `btn_marks` | Button | 进成绩 |
| `btn_exams` | Button | 进考试 |
| `btn_homework` | Button | 进作业 |
| `btn_messages` | Button | 进私信 |
| `btn_about` | Button | 进关于 |

**展示逻辑**

| 状态 | `tv_user` 文案 | `btn_login` |
| :--- | :--- | :--- |
| 未登录 | `未登录，请先登录`（灰色 12sp） | VISIBLE |
| 已登录 | Cookie `loginUserName` 的值，如 `20230101` | GONE |

**当前视觉**：整屏可滚动 `ScrollView` > `LinearLayout`；菜单按钮用 `WatchMenuButton` 样式（56dp 高、圆角 28dp 卡片、1dp 白边 20% 透明）。

**可自由发挥**：菜单项排布（列表/宫格/环形）、图标、颜色、是否需要滚动、账号区样式。

---

### 5.2 列表页基类 ListActivityBase

「成绩 / 考试 / 考试详情 / 作业 / 私信」**全部复用**这一套骨架。

| 项 | 值 |
| :--- | :--- |
| 布局 | `res/layout/activity_list.xml` |
| 行布局 | `res/layout/item_row.xml` |
| 类 | `ui/ListActivityBase.java`（抽象基类） |

**必需的 view id**

| id | 控件 | 用途 |
| :--- | :--- | :--- |
| `btn_back` | Button | 左上角「返回」→ `finish()` |
| `tv_title` | TextView | 居中标题 |
| `list` | RecyclerView | 数据列表 |
| `tv_empty` | TextView | 空态 / 错误态文案（居中） |
| `progress` | ProgressBar | 加载指示（居中） |
| `tv_row_title` | TextView | **行布局里**：主标题 |
| `tv_row_sub` | TextView | **行布局里**：副标题（可为空，空则隐藏） |

**三态**

| 状态 | 表现 |
| :--- | :--- |
| 加载中 | `progress` VISIBLE |
| 有数据 | `list` 显示，`tv_empty` GONE |
| 空 / 出错 | `tv_empty` 显示对应文案，列表清空 |

**行数据模型**：每行 = `(title, sub, tag)`，`title` 走 `tv_row_title`，`sub` 走 `tv_row_sub`，`tag` 是点击时带出的原始对象（不展示）。**行内如有自定义控件，只要保证这两个 id 存在即可**；你完全可以把行改成卡片、加图标、改高度。

**当前视觉**：行 = 圆角 14dp 卡片（`card_bg.xml`），上下 4dp 外边距、内边距 10/8dp；标题 15sp 加粗白、副标题 12sp 灰。

> ⚠️ 列表用 `RecyclerView` + `LinearLayoutManager`。若要换 `WearableRecyclerView`（弯折/旋转表冠支持）需自行加依赖并回归。

---

### 5.3 成绩 MarksActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | 复用 `activity_list.xml` |
| 类 | `MarksActivity.java` |
| 标题 | `最近成绩` |

**数据来源**（3 次串行请求）

```
1) GET /zhixuebao/base/common/academicYear          → 找最近有数据的学年
2) GET /zhixuebao/report/exam/getRecentExam         → examInfo.examName / examId
3) GET /zhixuebao/report/exam/getReportMain?examId= → 各科成绩
```

**行展示**

| 第几行 | `tv_row_title` | `tv_row_sub` |
| :--- | :--- | :--- |
| 第 1 行 | 考试名称 | 固定文案 `考试名称` |
| 各科 | `{subjectName}  {userScore}` | `满分 {standardScore}   {subjectCode}` |
| 末行（有总分时） | `{总分subjectName}  {userScore}` | `满分 {standardScore}` |

**可自由发挥**：分数用大号数字、颜色区分高低、加环形/条形进度（`userScore / standardScore`）、按学科排序、卡片分组。

**空态**：`暂无考试数据` / `成绩接口无数据` / `未获取到考试 ID`

---

### 5.4 考试列表 ExamsActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | 复用 `activity_list.xml` |
| 类 | `ExamsActivity.java` |
| 标题 | `考试列表` |

**数据来源**：`GET /zhixuebao/report/exam/getUserExamList`，按学年分页（`pageSize=10`，**最多拉 6 页**，`hasNextPage` 为 false 时停）。

**行展示**

| `tv_row_title` | `tv_row_sub` |
| :--- | :--- |
| `examName` | `examId`（UUID） |

**交互**：点整行 → 进 `ExamDetailActivity`，带 `examId` + `examName` 两个 extra。

**可自由发挥**：副标题现在露的是 UUID，很难看 —— 建议换成年月/科目数等更有意义的信息，或直接隐藏。

**空态**：`暂无考试数据`

---

### 5.5 考试详情 + 错题本 ExamDetailActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | 复用 `activity_list.xml` |
| 类 | `ExamDetailActivity.java` |
| 标题 | 传入的考试名（不可用时 `考试详情`） |

**行展示**

| `tv_row_title` | `tv_row_sub` |
| :--- | :--- |
| `{subjectName}  {userScore}` | `满分 {standardScore} · 点按看错题` |
| 总分行 | 同上，但无副标题 |

**交互**

- 点某科 → 请求错题本 `GET /zhixuebao/report/paper/getLostTopicAndAnalysis?examId=&paperId=`
- 有错题 → 弹 `AlertDialog` 列表：`第 {disTitleNumber} 题` / `得分 {score} / {standardScore}`
- 无错题 → 弹 `AlertDialog`：`该学科暂无错题数据`
- 该科无数据 → 走错误态

**可自由发挥**：错题详情现在是 `AlertDialog`，**建议改成独立页面**（题号、得分、难度、班级得分率、题干 HTML/图片、解析图片都有数据可用，见 §8.5）。这是最值得重做的一块。

---

### 5.6 作业 HomeworkActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | 复用 `activity_list.xml` |
| 类 | `HomeworkActivity.java` |
| 标题 | `作业（未完成）` / `作业（已完成）` |

**数据来源**：`GET https://mhw.zhixue.com/homework_middle_service/stuapp/getStudentHomeWorkList`

**行展示**

| `tv_row_title` | `tv_row_sub` |
| :--- | :--- |
| `hwTitle` | `{typeName} · {MM-dd HH:mm}`（截止时间） |

**交互**：**点任意一行切换「未完成 / 已完成」**（当前用点击整行做切换，比较反直觉）。

> ⚠️ 这是一个 UX 缺陷：行点击既是"看详情"又没做成详情，而是切换列表。**重做 UI 时建议改成顶部 Tab / 左右滑动切换**，行列点击留给作业详情。

**空态**：`暂无未完成作业` / `暂无已完成作业`

---

### 5.7 私信 MessagesActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | 复用 `activity_list.xml` |
| 类 | `MessagesActivity.java` |
| 标题 | `私信` |

**数据来源**：`GET /container/personal/getPersonalMessage/`（`pageSize=50`）

**行展示**

| `tv_row_title` | `tv_row_sub` |
| :--- | :--- |
| `notify.content`（消息正文） | `{发送者姓名} · {MM-dd HH:mm}` |

> `senderDetail` 是**嵌套 JSON 字符串**，需二次解析取 `userName`。

**可自由发挥**：未读数角标、置顶标记、按发送者分组、消息详情页。

**空态**：`暂无数据`

---

### 5.8 关于 / 退出 AboutActivity

| 项 | 值 |
| :--- | :--- |
| 布局 | `res/layout/activity_about.xml` |
| 类 | `AboutActivity.java` |

**必需 view id**

| id | 用途 |
| :--- | :--- |
| `btn_back` | 返回 |
| `tv_about` | 版本、免责声明、当前登录态 |
| `btn_manual` | 进「手动登录 / Cookie」页 |
| `btn_logout` | 清会话 + 清账号 → 回主界面 |

`tv_about` 当前文案：`智学网手表版 v1.0` + 免责声明 + `登录态：已登录/未登录`，居中 11sp。

---

### 5.9 手动登录 / 账号设置 CookieLoginActivity

无 WebView 设备的降级方案，同时也承担「保存账号密码供自动填充」的职责。

| 项 | 值 |
| :--- | :--- |
| 布局 | `res/layout/activity_cookie_login.xml` |
| 类 | `CookieLoginActivity.java` |

**必需 view id**

| id | 控件 | 用途 |
| :--- | :--- | :--- |
| `btn_back` | Button | 返回 |
| `et_user` | EditText | 账号（会读进 `zhixue_creds`） |
| `et_pwd` | EditText | 密码（同上） |
| `et_cookie` | EditText | 粘贴 Cookie |
| `btn_save_creds` | Button | 存账号 → 打开登录页 |
| `btn_save_cookie` | Button | 用 Cookie 登录 |
| `btn_clear` | Button | 清除登录信息 |

**校验提示**：Cookie 为空 → Toast `请先粘贴 Cookie`；缺 `loginUserName` → Toast `Cookie 缺少 loginUserName，可能不完整`。

**可自由发挥**：表单样式、键盘类型、是否折叠、是否加"显示密码"。

---

### 5.10 登录页 LoginWebActivity

见 [§4.4](#44--登录页loginwebactivity的特殊约束) 的硬约束。这里给结构契约。

| 项 | 值 |
| :--- | :--- |
| 布局 | `res/layout/activity_login_web.xml` |
| 类 | `LoginWebActivity.java` |

**必需 view id**

| id | 控件 | 用途 |
| :--- | :--- | :--- |
| `web` | WebView | **承载官方登录页 + 极验验证码，必须可完整点击** |
| `tv_hint` | TextView | 状态提示 + 点按重新填充账号 |
| `progress` | ProgressBar | 页面加载指示 |

**`tv_hint` 文案状态机**

| 时机 | 文案 |
| :--- | :--- |
| 初始 | `点此重新填充账号 / 密码` |
| 页面离开登录页后 | `已提交，正在校验登录状态…` |
| 校验未通过（已离开登录页） | `尚未通过校验，请完成滑块验证` |
| 校验未通过（仍在登录页） | `请完成滑块验证后点登录` |

**可自由发挥**：提示条样式、加载动画、位置。**但不能遮挡 WebView 的可交互区域**。
---

## 6. 设计令牌（当前值）

全部集中在这几个文件，改这里就能全局换肤：

| 文件 | 内容 |
| :--- | :--- |
| `res/values/colors.xml` | 颜色 |
| `res/values/styles.xml` | 主题 + 按钮/文本样式 |
| `res/values/strings.xml` | 文案 |
| `res/drawable/menu_item_bg.xml` | 菜单按钮圆角卡片背景 |
| `res/drawable/card_bg.xml` | 列表行卡片背景 |
| `res/drawable/ic_launcher.xml` | 应用图标（矢量） |

### 6.1 颜色

| 名称 | 色值 | 当前用途 |
| :--- | :--- | :--- |
| `zhixue_dark` | `#111418` | 全局背景（窗口背景也是它） |
| `zhixue_card` | `#1C2126` | 卡片/按钮底色 |
| `zhixue_blue` | `#1E88E5` | 标题强调色 |
| `zhixue_green` | `#43A047` | 备用（当前未使用） |
| `zhixue_red` | `#E53935` | 备用（当前未使用） |
| `zhixue_grey` | `#9AA0A6` | 副标题、提示文本 |
| `white` / `black` | `#FFFFFF` / `#000000` | 正文 / 备用 |

> 目前是**深色单主题**。若要做浅色，`WearTheme` 的 `android:windowBackground` / `textColorPrimary` 要一起改，且注意系统 `Theme.DeviceDefault` 的默认控件配色。

### 6.2 文本样式

| 样式名 | 字号 | 颜色 | 用途 |
| :--- | :--- | :--- | :--- |
| `RowTitle` | 15sp | 白、加粗 | 列表主标题 |
| `RowSub` | 12sp | `zhixue_grey` | 列表副标题 |
| `WatchMenuButton` | 17sp | 白 | 主菜单按钮 |
| `TopIconButton` | 13sp | 白 | 左上角退出/返回 |

### 6.3 尺寸令牌（写在布局里，未抽成 dimens）

| 元素 | 当前值 |
| :--- | :--- |
| 主菜单按钮 | 高 56dp、上边距 7dp、圆角 28dp、边框 1dp `#33FFFFFF` |
| 左上角小按钮 | 46×34dp，`minWidth/minHeight=0dp` |
| 列表行卡片 | 圆角 14dp、上下外边距 4dp、内边距 10dp(左右) / 8dp(上下) |
| 标题避让 padding | 左右各 50dp |
| 列表加载指示 | 32×32dp |

> 建议你把尺寸抽到 `res/values/dimens.xml`，方便统一调。

---

## 7. ★ UI ↔ 逻辑契约（必读）

**你可以随意改**：布局结构、控件类型、颜色、字号、图标、间距、圆角、动画、行的排布、页面内信息层级、新增页面。
**你必须保证**：下面这些 id 存在且类型兼容 —— 否则 Java 侧 `findViewById` 返回 null，直接 `NullPointerException` 崩溃。

### 7.1 映射总表

| 布局文件 | 对应 Activity / 基类 | 必须存在的 view id |
| :--- | :--- | :--- |
| `activity_main.xml` | `MainActivity` | `btn_exit` `tv_user` `btn_login` `btn_marks` `btn_exams` `btn_homework` `btn_messages` `btn_about` |
| `activity_list.xml` | `ui/ListActivityBase`（及 4 个子类） | `btn_back` `tv_title` `list` `tv_empty` `progress` |
| `item_row.xml` | `ListActivityBase.Adapter` | `tv_row_title` `tv_row_sub` |
| `activity_login_web.xml` | `LoginWebActivity` | `web` `tv_hint` `progress` |
| `activity_cookie_login.xml` | `CookieLoginActivity` | `btn_back` `et_user` `et_pwd` `et_cookie` `btn_save_creds` `btn_save_cookie` `btn_clear` |
| `activity_about.xml` | `AboutActivity` | `btn_back` `tv_about` `btn_logout` |

**使用 `activity_list.xml` 的 Activity**：`MarksActivity`、`ExamsActivity`、`ExamDetailActivity`、`HomeworkActivity`、`MessagesActivity`。
→ 改这一个布局，**5 个页面同时生效**。

### 7.2 如果你想改 id 名（可以，但要同步改 Java）

| 布局文件 | 需要同步修改的 Java 文件 |
| :--- | :--- |
| `activity_main.xml` | `MainActivity.java` |
| `activity_list.xml` | `ui/ListActivityBase.java` |
| `item_row.xml` | `ui/ListActivityBase.java`（内部 `Adapter.VH`） |
| `activity_login_web.xml` | `LoginWebActivity.java` |
| `activity_cookie_login.xml` | `CookieLoginActivity.java` |
| `activity_about.xml` | `AboutActivity.java` |

> 更省事的做法：**只改视觉，不动 id**。把现有控件包一层新的容器、换背景、换字体都可以不碰 Java。

### 7.3 控件类型契约（重要）

| id | Java 侧把它当作 | 换成什么会安全 | 换成什么会崩 |
| :--- | :--- | :--- | :--- |
| `list` | `RecyclerView`（`setLayoutManager` / `setAdapter`） | 任意 `RecyclerView` 子类 | `ListView`、`ScrollView`（强转失败） |
| `progress` | `View`（`setVisibility`） | 任意 View 子类（`ProgressBar`/自定义） | — |
| `tv_empty` `tv_user` `tv_title` `tv_hint` `tv_about` `tv_row_title` `tv_row_sub` | `TextView`（`setText` / `setVisibility`） | 任意 `TextView` 子类 | 非 TextView |
| `web` | `WebView`（大量方法调用） | 只能 `WebView` | 非 WebView |
| `et_*` | `EditText`（`getText` / `setText`） | 任意 `EditText` 子类 | 非 EditText |
| `btn_*` | `View`（只 `setOnClickListener`，除 `btn_login` 用 `setVisibility`） | **任意 View 子类**（TextView/ImageView/自定义卡片都行） | — |

> 好消息：**所有按钮只被当作 `View` 用**，所以你可以把任何 `Button` 换成 `TextView`/`ImageView`/自定义 View 做视觉，不会崩。

### 7.4 绝对不能碰的逻辑（改 UI 时请保持）

| 位置 | 约束 |
| :--- | :--- |
| `LoginWebActivity.verifySession()` | 登录成功判定：`getCurrentUser` 有 role **且** `getToken` 有 token。**不要**为了"看起来更快"而放宽 |
| `LoginWebActivity.autofill()` | **不要**加回 `el.focus()` 或周期性重填（会弹软键盘） |
| `ListActivityBase.isSessionExpired()` + `showError()` | 失效必须跳登录页，不要改成只弹错误文案 |
| `SessionStore` | Cookie 持久化 + `uname = base64(loginUserName)` 补齐，不要绕过 |
| Activity 类名 | Manifest 已注册，改名要同步改 Manifest 和所有跳转 |
| `AndroidManifest.xml` 的 `INTERNET` 权限 | 删了所有网络功能失效 |

### 7.5 新增页面怎么做

1. 新建 `XxxActivity.java`，列表类页面**继承 `ListActivityBase`**（自动获得标题/返回/三态/列表逻辑）；
2. 在 `AndroidManifest.xml` 注册 `<activity android:name=".XxxActivity" android:exported="false" />`；
3. 复用 `activity_list.xml`，或自建布局但保留上表要求的 id；
4. 在 `MainActivity` 的菜单里加入口。

---

## 8. 数据字典

### 8.1 页面用到的字段速查

| 页面 | 字段 | 含义 |
| :--- | :--- | :--- |
| 主菜单 | Cookie `loginUserName` | 当前登录账号（如 `20230101`） |
| 成绩 | `examInfo.examName` | 考试名称 |
| 成绩 | `paperList[].subjectName` | 学科名（语文/数学…） |
| 成绩 | `paperList[].userScore` | 得分 |
| 成绩 | `paperList[].standardScore` | 满分 |
| 成绩 | `paperList[].subjectCode` | 学科代码（`01` 语文 / `02` 数学…） |
| 成绩 | `totalScore.{subjectName,userScore,standardScore}` | 总分行 |
| 考试 | `examList[].examName` / `examId` | 考试名 / ID |
| 作业 | `list[].hwTitle` | 作业标题 |
| 作业 | `list[].endTime` | 截止时间（**毫秒**，除以 1000 后格式化） |
| 作业 | `list[].homeWorkTypeDTO.typeName` | 作业类型名 |
| 私信 | `notify.content` | 消息正文 |
| 私信 | `notify.createTime` | 创建时间（毫秒） |
| 私信 | `notify.senderDetail` | **JSON 字符串**，解析后取 `userName` |

### 8.2 学科代码对照（可用作分组/配色）

| code | 学科 | code | 学科 |
| :--- | :--- | :--- | :--- |
| `01` | 语文 | `05` | 化学 |
| `02` | 数学 | `06` | 生物 |
| `03` | 英语 | `07` | 政治 |
| `04` | 物理 | `08` | 历史 |
| — | — | `99` | **总分**（`subject.id` 为空） |

### 8.3 作业类型

| code | 含义 |
| :--- | :--- |
| `105` | 自由出题 |
| `102` | 题库练习 |

### 8.4 考试相关 ID 的层级（做导航时用到）

```
Exam(examId)
 └── Subject(paperId / topicSetId)     ← getReportMain 的 paperList[].paperId
      └── 错题本(getLostTopicAndAnalysis 用 examId + paperId)
```

### 8.5 错题本可用字段（做详情页时很丰富）

`ErrorBookTopic`：`analysis_html`（解析）、`answer_html`（答案）、`content_html`（题干）、
`topic_img_url` / `topic_analysis_img_url`（题目/解析图）、`image_answer`（学生作答图，List）、
`answer_type`、`is_correct`、`difficulty`（难度值）、`class_score_rate`（班级得分率）、
`dis_title_number`（显示题号）、`score` / `standard_score`、`subject_name`、`topic_source_paper_name`（来源）。

---

## 9. 状态与异常

### 9.1 统一三态（所有数据页）

| 状态 | 当前表现 | 建议 |
| :--- | :--- | :--- |
| 加载 | 居中 `ProgressBar` | 可改骨架屏/进度动画 |
| 空 | `tv_empty` 居中灰字 | 可加插图 |
| 错误 | `tv_empty` 显示 `加载失败` + 异常信息 | 建议加「重试」按钮 |
| **会话失效** | **不显示错误，直接跳登录页** | 保持 |

### 9.2 各页空态文案

| 页面 | 文案 |
| :--- | :--- |
| 成绩 | `暂无考试数据` / `成绩接口无数据` / `未获取到考试 ID` |
| 考试 | `暂无考试数据` |
| 作业 | `暂无未完成作业` / `暂无已完成作业` |
| 私信 | `暂无数据` |
| 列表默认 | `暂无数据` |

### 9.3 失败时后端会返回的东西（设计错误文案时参考）

| 场景 | 返回 |
| :--- | :--- |
| 会话失效 | `{"errorCode":3002,"errorInfo":"未登录或登录失效"}` |
| 错题本未收集 | `{"errorCode":40217,"errorInfo":"暂时未收集到试题信息,无法查看"}` |
| 登录时缺验证码 | `账号或密码错误，请点击登录遇到问题解决`（**注意：这个文案是通用的，不代表密码真的错**） |
| 验证码没通过 | `验证码错误` |
| 尝试过多 | `尝试次数已超过限制，请15分钟后重试` |

---

## 10. 验收清单

改完 UI 后，请至少跑通以下项（真机 + 圆屏模拟器各一遍）：

- [ ] 主菜单：未登录时显示「登录」，已登录时隐藏并显示账号
- [ ] 主菜单：左上角「退出」能回到表盘（任务栈清空）
- [ ] 主菜单：系统返回键等同退出
- [ ] 所有二级页左上角「返回」能回到上级
- [ ] 列表页：加载态 / 空态 / 错误态都能正常显示
- [ ] 列表行点击能进对应详情（考试 → 考试详情 → 错题本）
- [ ] 圆形屏上四角内容未被裁切（若去掉 `boxedEdges="all"` 要重点回归）
- [ ] 登录页：WebView 能完整点击，**极验验证码可以正常点选/拖拽**
- [ ] 登录页：**输入法不会反复弹出**
- [ ] 登录页：账号密码自动填充后能正常提交（不报「密码不能为空」）
- [ ] 登录成功后能正常进入功能页
- [ ] 登录态失效时点功能页 → **自动跳登录页**（不是弹错误）
- [ ] 所有可点元素 ≥48×48dp，手指点得中

---

## 附：文件位置速查

```
zhixue-wear/app/src/main/
├── AndroidManifest.xml                      页面注册 / 权限
├── java/com/chaoli/zhixuewear/
│   ├── MainActivity.java                    主菜单
│   ├── LoginWebActivity.java                登录（WebView + 极验）★逻辑敏感
│   ├── CookieLoginActivity.java             手动登录 / Cookie
│   ├── MarksActivity.java                   成绩
│   ├── ExamsActivity.java                   考试列表
│   ├── ExamDetailActivity.java              考试详情 + 错题本
│   ├── HomeworkActivity.java                作业
│   ├── MessagesActivity.java                私信
│   ├── AboutActivity.java                   关于 / 退出
│   ├── ui/ListActivityBase.java             列表页基类 ★逻辑敏感
│   ├── net/ZhixueApi.java                   接口地址
│   ├── net/ZhixueClient.java                HTTP + Cookie + XToken
│   ├── net/ZhixueService.java               高层业务组合
│   └── store/SessionStore.java              登录态持久化 ★逻辑敏感
└── res/
    ├── layout/                              6 个布局文件（见 §7.1）
    ├── values/                              colors / styles / strings
    └── drawable/                            背景与图标
```

构建：项目根目录 `./build.sh :app:assembleDebug`  
产物：`zhixue-wear/app/build/outputs/apk/debug/app-debug.apk`

---

<sub>本文档由源码逐项核对生成（view id 与 Java 绑定关系经脚本提取验证），对应 zhixue-wear v1.0。</sub>

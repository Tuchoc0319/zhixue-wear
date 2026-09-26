# zhixuewang 开发文档（API 参考）

> 本文档基于 **zhixuewang v1.5.1** 源码逐行整理，覆盖公开 API 签名、返回值、异常、数据模型、底层 HTTP 接口与扩展开发指南。
>
> - 上游仓库：<https://github.com/anwenhu/zhixuewang-python>
> - PyPI 包名：`zhixuewang`
> - 官方文档站：<https://anwenhu.github.io/zhixuewang-docs/>（部分内容与 1.5.1 源码不一致，见文末[附录 C](#附录-c与官方文档站的差异勘误)）
> - 许可证：MIT

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 安装与依赖](#2-安装与依赖)
- [3. 包结构与模块地图](#3-包结构与模块地图)
- [4. 快速开始](#4-快速开始)
- [5. 登录模块 zhixuewang.account](#5-登录模块-zhixuewangaccount)
- [6. 通用账号基类 Account](#6-通用账号基类-account)
- [7. 学生账号 StudentAccount](#7-学生账号-studentaccount)
- [8. 教师账号 TeacherAccount](#8-教师账号-teacheraccount)
- [9. 家长账号 ParentAccount](#9-家长账号-parentaccount)
- [10. 数据模型 zhixuewang.models](#10-数据模型-zhixuewangmodels)
- [11. 教师数据模型](#11-教师数据模型-zhixuewangteachermodels)
- [12. 工具与辅助 API](#12-工具与辅助-api)
- [13. 异常体系](#13-异常体系)
- [14. 底层 HTTP 接口清单](#14-底层-http-接口清单)
- [15. 扩展开发指南](#15-扩展开发指南)
- [16. 已知问题与注意事项](#16-已知问题与注意事项)
- [附录 A：API 速查表](#附录-aapi-速查表)
- [附录 B：版本与贡献](#附录-b版本与贡献)
- [附录 C：与官方文档站的差异勘误](#附录-c与官方文档站的差异勘误)

---

## 1. 项目概述

`zhixuewang` 是一个非官方的 Python 版智学网（zhixue.com）API 封装库。它通过复用浏览器的 Cookie / Session，模拟网页端与 App 端请求，对外提供面向对象的接口。

支持三种账号类型：

| 账号类型 | 对应类 | 能力概述 |
| :--- | :--- | :--- |
| 学生账号 | `StudentAccount` | 考试成绩、考试列表、学科、原卷、答题详情、错题本、班级、同学、作业与答案 |
| 教师账号 | `TeacherAccount` | 考试列表、考试详情、考试科目、阅卷进度、学生原卷、参考班级 |
| 家长账号 | `ParentAccount` | 继承学生账号能力（查看孩子成绩/作业），但禁用涉及班级与同学隐私的接口 |

> 维护说明（摘自 README）：由于智学网登录及部分接口发生变化，学生网页端的部分功能可能不再继续适配；项目仍会维护现有可用功能、兼容性问题、Bug 修复及社区贡献。

### 1.1 设计要点

1. **Session 复用**：所有请求都通过 `requests.Session` 发起，登录成功后 Cookie 保存在 Session 中，后续接口自动携带。
2. **延迟鉴权**：学生端接口使用 `XToken` 机制（见 14.2），由 `get_auth_header()` 按需签发并缓存 600 秒。
3. **纯数据类模型**：接口返回统一解析为 `dataclass` 模型（`Exam`、`Subject`、`Mark` 等），便于 IDE 提示与类型检查。
4. **扩展列表**：所有集合类型均为 `ExtendedList`，内置 `find` / `find_by_id` / `find_by_name` 等检索方法。

---

## 2. 安装与依赖

### 2.1 环境要求

- Python **3.7+**

### 2.2 安装

```bash
pip install zhixuewang
```

从源码安装：

```bash
git clone https://github.com/anwenhu/zhixuewang-python
cd zhixuewang-python
pip install .
```

### 2.3 运行时依赖

| 依赖 | 版本约束 | 用途 |
| :--- | :--- | :--- |
| `requests` | `>=2.31.0` | 所有 HTTP 请求 |
| `playwright` | `>=1.35.0` | `login_playwright()` 打开浏览器完成人机验证 |
| `setuptools` | `>=68.0.0` | 打包 |
| `typing-extensions` | `>=4.7.1` | `@deprecated` 装饰器等 |

使用 Playwright 登录前还需安装浏览器内核：

```bash
playwright install chromium
```

---

## 3. 包结构与模块地图

```text
zhixuewang/
├── __init__.py            # 导出 login_cookie / login_playwright / rewrite_str
├── account.py             # 登录入口：cookie / playwright / session → Account
├── session.py             # get_basic_session()：构造带 UA 的 requests.Session
├── urls.py                # 公共 Url：SSO、私信等
├── models.py              # 核心数据模型 + Account 基类 + ExtendedList
├── exceptions.py          # 异常体系
├── tools/
│   ├── __init__.py        # 导出 get_property
│   └── datetime_tool.py   # timestamp2datetime / get_property
├── student/
│   ├── __init__.py        # 导出 StudentAccount
│   ├── student.py         # 学生全部接口实现
│   └── urls.py            # 学生端接口地址
├── teacher/
│   ├── __init__.py        # 导出 TeacherAccount
│   ├── teacher.py         # 教师全部接口实现
│   ├── models.py          # 教师相关数据模型
│   └── urls.py            # 教师端接口地址
└── parent/
    ├── __init__.py        # 导出 ParentAccount
    └── parent.py          # 家长账号（继承学生账号 + 禁用部分接口）
```

`example/` 目录提供开箱即用示例：

| 示例 | 说明 |
| :--- | :--- |
| `example/student/get_mark.py` | 交互式查询考试成绩 |
| `example/student/get_weight_mark.py` | 用 `rewrite_str` 自定义成绩输出 |
| `example/student/get_errorbooks.py` | 生成错题本 PDF（需 `wkhtmltopdf` + `jinja2`） |
| `example/student/download_hw_resources.py` | 轮询下载作业资源 |
| `example/teacher/get_original_paper.py` | 获取并解析学生原卷 |
| `example/teacher/get_paper.py` | 教师下载学生原卷 HTML（使用了已不存在的 `login_student` / `login_teacher`，见附录 C） |

---

## 4. 快速开始

```python
from zhixuewang import login_playwright

# 会弹出 Chromium 浏览器，请手动完成滑块/人机验证
account = login_playwright("你的智学网账号", "你的智学网密码")

# 配合类型提示转换为学生账号
student = account.to_student()
print(student.get_self_mark())
```

输出（`Mark.__str__` 格式）：

```text
张三-2026学年第一学期期末考试
语文: 121.0
数学: 121.0
英语: 137.5
总分: 379.5
```

教师账号示例：

```python
from zhixuewang import login_playwright

teacher = login_playwright("教师账号", "密码").to_teacher()
page = teacher.get_exams(page_index=1, page_size=15)
print(f"第 {page.page_index} 页 / 共 {page.all_pages} 页")
for exam in page.exams:
    print(exam.id, exam.name, exam.grade_code)
```

---

## 5. 登录模块 `zhixuewang.account`

模块路径：`zhixuewang/account.py`。顶层 `zhixuewang` 包已导出 `login_cookie`、`login_playwright`、`rewrite_str`。

### 5.1 `login_cookie`

```python
def login_cookie(cookies: Union[dict, str]) -> Account
```

通过浏览器 Cookie 登录，自动识别账号类型。

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `cookies` | `dict` 或 `str` | — | Cookie 字典，或形如 `k1=v1; k2=v2` 的字符串 |

**返回值**：`Account`（实际为 `StudentAccount` / `TeacherAccount` / `ParentAccount`）。

**行为细节**：

1. 使用 `get_basic_session()` 创建新 Session，并设置 `trust_env = False`。
2. 若为字符串，按分隔符 **`"; "`（分号+空格）** 切分为字典；因此字符串形式的 Cookie 必须使用 `; ` 分隔。
3. **必须包含 `loginUserName` 键**，否则抛 `KeyError`；库会据此设置：
   `session.cookies["uname"] = base64(cookies["loginUserName"])`。
4. 调用 `session_to_account(session)` 自动判断角色并完成初始化（学生会调用 `set_base_info()`，教师会额外调用 `set_advanced_info()`）。

**异常**：`KeyError`（缺少 `loginUserName`）、`UserDefunctError`（学生账号无班级）、`PageConnectionError`（接口异常）。

```python
from zhixuewang import login_cookie

zxw = login_cookie("loginUserName=xxx; other=yyy").to_student()
print(zxw.get_self_mark())
```

> 网页端可用如下 JS 书签一键复制 Cookie：
>
> ```javascript
> javascript:(function(){function getCookies(){return document.cookie;}function copyToClipboard(text){const textarea=document.createElement('textarea');textarea.value=text;document.body.appendChild(textarea);textarea.select();document.execCommand('copy');document.body.removeChild(textarea);}const cookies=getCookies();copyToClipboard(cookies);alert('Cookies 已复制到剪切板！');})();
> ```

### 5.2 `login_playwright`

```python
def login_playwright(username: str, password: str) -> Account
```

通过 Playwright 打开真实浏览器登录，允许人工完成人机验证。

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `username` | `str` | — | 智学网账号（准考证号 / 手机号等） |
| `password` | `str` | — | 密码 |

**返回值**：`Account`。

**行为细节**：

1. 以 `headless=False` 启动 Chromium（**会弹出浏览器窗口**）。
2. 打开 `https://www.zhixue.com/wap_login.html`，等待 `networkidle`。
3. 自动填写 `#txtUserName` 与 `#txtPassword`，点击 `#signup_button`。
4. 等待 URL 跳转到 `https://www.zhixue.com/htm-vessel/**`（**超时时间为无限**，需要用户手动完成验证）。
5. 读取浏览器 Cookie 转为字典，关闭浏览器，再走与 `login_cookie` 相同的流程。

**异常**：浏览器/网络异常；`KeyError`（如 Cookie 中无 `loginUserName`）。

> ⚠️ **重要限制**：内部使用 `asyncio.run()`，**不能在已有事件循环中调用**（例如 Jupyter Notebook、异步框架内），否则抛 `RuntimeError: asyncio.run() cannot be called from a running event loop`。

### 5.3 `session_to_account`

```python
def session_to_account(session: requests.Session) -> Account
```

由已登录的 Session 构造账号对象。先调用 `get_account_role()` 判断角色，再分别构造：

| role 值 | 返回对象 | 额外初始化 |
| :--- | :--- | :--- |
| `"student"` | `StudentAccount` | `set_base_info()` |
| `"teacher"` | `TeacherAccount` | `set_base_info().set_advanced_info()` |
| `"parent"` | `ParentAccount` | 无（家长无法获取基本信息） |
| 其他 | `TeacherAccount` | 同上（兜底逻辑） |

### 5.4 `get_account_role`

```python
def get_account_role(s: requests.Session) -> str
```

请求 `GET https://www.zhixue.com/container/getCurrentUser`，读取 `result.role` 字段。

### 5.5 `rewrite_str`

```python
def rewrite_str(model)
```

一个类装饰器工厂，用于重写任意模型的 `__str__` 方法，常用于自定义输出格式。

```python
from zhixuewang import login_playwright, rewrite_str
from zhixuewang.models import Mark

@rewrite_str(Mark)
def _(self: Mark):
    total = sum(s.score for s in self if s.subject.name != "总分")
    return f"自定义输出，总分 {total}"

print(login_playwright("账号", "密码").to_student().get_self_mark())
```

---

## 6. 通用账号基类 `Account`

模块路径：`zhixuewang/models.py`，类 `Account`。

```python
class Account:
    role: Role                    # 账号角色枚举
    username: str                 # 由 session.cookies["uname"] base64 解码得到
    _session: requests.Session    # 内部会话
    def __init__(self, session: requests.Session, role: Role) -> None
```

### 6.1 通用方法

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `get_session` | `get_session() -> requests.Session` | 返回内部 Session，可用于扩展自定义请求 |
| `to_student` | `to_student() -> StudentAccount` | 类型转换（仅类型提示），不匹配时抛 `NotImplementedError` |
| `to_teacher` | `to_teacher() -> TeacherAccount` | 同上 |
| `to_parent` | `to_parent() -> ParentAccount` | 同上 |

### 6.2 私信：`get_personal_messages`

```python
def get_personal_messages(self, page_index: int = 1, page_size: int = 10000) -> PersonalMessageList
```

获取当前账号的私信列表，**三种账号类型均可使用**。

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `page_index` | `int` | `1` | 页码，从 1 开始 |
| `page_size` | `int` | `10000` | 每页条数 |

**返回值**：`PersonalMessageList`，包含 `messages`、`page_info`、`total_count`，并提供 `get_unread_messages()`、`get_messages_by_sender(sender_id)`。

**请求细节**：`GET {BASE_URL}/container/personal/getPersonalMessage/`，参数 `_t`、`pageIndex`、`pageSize`、`type=personalMsg`、`_`（时间戳）。

### 6.3 私信：`send_personal_message`

```python
def send_personal_message(self, receiver_id: str, content: str) -> bool
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `receiver_id` | `str` | — | 接收者用户 ID |
| `content` | `str` | — | 消息内容 |

**返回值**：`bool`，当响应 `result == "success"` 时为 `True`。

**请求细节**：`POST {BASE_URL}/container/personal/sendPersonalMsg/?_t=<ms>`，表单 `receiverId` / `content` / `type=personalMsg`。

```python
account = login_cookie(cookies)
msgs = account.get_personal_messages(page_index=1, page_size=20)
for m in msgs.messages:
    print(m.sender.user_name, m.content, m.get_create_datetime())
account.send_personal_message("目标用户ID", "你好")
```

---

## 7. 学生账号 `StudentAccount`

模块路径：`zhixuewang/student/student.py`。

```python
class StudentAccount(Account, StuPerson):
    ...
```

继承 `Account` 与 `StuPerson`，因此同时拥有 `id`、`name`、`gender`、`mobile`、`avatar`、`code`、`clazz` 等属性。

> **`clazz` 属性的双重含义**：`StudentAccount` 继承自 `StuPerson`，`StuPerson.clazz` 是**当前学生所在班级**；而 `get_clazz(clazz_data)` 是**按 ID / 名称查询班级**的方法，两者不要混淆。

### 7.1 `get_auth_header`

```python
def get_auth_header(self) -> dict
```

学生端鉴权核心。生成并返回如下请求头：

| Header | 说明 |
| :--- | :--- |
| `authbizcode` | 固定 `"0001"` |
| `authguid` | 随机 UUID4 字符串 |
| `authtimestamp` | 当前毫秒时间戳 |
| `authtoken` | `md5(authguid + authtimestamp + "iflytek!@#123student")` |
| `XToken` | 通过 `GET /container/app/token/getToken` 换取 |

**缓存策略**：`XToken` 与其签发时间保存在 `self._auth`，**600 秒内复用**，过期后重新签发。

**异常**：`PageConnectionError`（签发失败）。

> 所有学生端查询接口（除 `get_clazzs` / `get_classmates` / 私信外）基本都会调用本方法，开发者无需手动调用。

### 7.2 `set_base_info`

```python
def set_base_info(self) -> "StudentAccount"
```

请求 `GET {BASE_URL}/container/container/student/account/`，解析 `student` 节点，填充 `code`、`name`、`avatar`、`gender`、`username`、`id`、`mobile` 与 `clazz`。

**异常**：

- `PageConnectionError`：HTTP 非 2xx；
- `UserDefunctError`：返回数据中 `clazz` 为空（账号已失效/无班级）。

### 7.3 考试相关

#### `get_academic_year`

```python
def get_academic_year(self) -> ExtendedList[AcademicYear]
```

获取学年列表（用于按学年筛选考试）。

#### `get_exam`

```python
def get_exam(self, exam_data: Union[Exam, str] = "") -> Optional[Exam]
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `exam_data` | `Exam` / `str` | `""` | 考试 ID、考试名称或 `Exam` 实例；留空返回最新考试 |

返回 `Exam` 或 `None`。传入 `Exam` 实例时：若已有班级/年级排名则直接返回，否则按 `id` 回查。

> 传入的字符串通过 `_check_is_uuid()` 判断是 ID 还是名称：长度为 36、`s[14] == "4"`、且 `-` 分别位于下标 8/13/18/23 时视为 UUID。

#### `get_page_exam`

```python
def get_page_exam(self, page_index: int, acamemic_year: Optional[AcademicYear] = None) -> Tuple[ExtendedList[Exam], bool]
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `page_index` | `int` | — | 页码 |
| `acamemic_year` | `AcademicYear` | `None` | 学年；为空时自动选取“最近一个含有效考试的学年” |

**返回值**：`Tuple[ExtendedList[Exam], bool]`，第二个元素表示是否还有下一页。每页固定 10 条。

> ⚠️ 参数名拼写错误 `acamemic_year`（正确应为 `academic_year`），调用时请使用关键字 `acamemic_year=`。
> ⚠️ 若某学年没有任何考试，会自动向前查找更早的学年（`_get_latest_valid_academic_year`）。

#### `get_latest_exam`

```python
def get_latest_exam(self) -> Exam
```

获取最近一次考试，返回的 `Exam` 会带上 `subjects`、`grade_code`、`is_final`、`create_time` 等字段。

#### `get_exams`

```python
def get_exams(self) -> ExtendedList[Exam]
```

获取**全部**历史考试。

> ⚠️ 该函数会遍历所有学年逐页请求，**耗时较长**。结果缓存在 `self.exams`；下次调用时若发现最新考试未变化则直接返回缓存。

### 7.4 成绩与学科

#### `get_self_mark`

```python
def get_self_mark(self, exam_data: Union[Exam, str] = "", has_total_score: bool = True) -> Mark
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `exam_data` | `Exam` / `str` | `""` | 考试 ID / 名称 / 实例；留空为最新考试 |
| `has_total_score` | `bool` | `True` | 是否附带“总分”科目 |

**返回值**：`Mark`（`ExtendedList[SubjectScore]` 的子类），同时持有 `mark.exam` 与 `mark.person`。

**细节**：

- 普通科目的 `SubjectScore.subject.code` 为学科代码（`01` 语文、`02` 数学…）。
- 总分科目的 `Subject.code == "99"`、`Subject.id == ""`，并携带 `class_rank` / `grade_rank`。
- 内部会调用 `_set_exam_rank()` 通过排名接口补算各科 `class_rank`；接口不可用时静默跳过。

```python
mark = student.get_self_mark()          # 最新考试，含总分
for s in mark:
    print(s.subject.name, s.score, s.class_rank, s.grade_rank)
```

#### `get_subjects`

```python
def get_subjects(self, exam_data: Union[Exam, str] = "") -> ExtendedList[Subject]
```

获取指定考试的全部学科（**不含总分**）。返回的 `Subject` 含 `id`（即 `paperId` / `topicSetId`）、`name`、`code`、`standard_score`、`exam_id`。

#### `get_subject`

```python
def get_subject(self, subject_data: Union[Subject, str], exam_data: Union[Exam, str] = "") -> Subject
```

按 ID / 名称获取某场考试下的单个学科；传入 `Subject` 实例时原样返回。查不到时返回**空 `Subject()`**（不抛异常）。

### 7.5 原卷与答题详情

#### `get_original`

```python
def get_original(self, subject_data: Union[Subject, str], exam_data: Union[Exam, str] = "") -> List[str]
```

获取指定考试、指定学科的**原卷图片 URL 列表**。返回空列表表示无数据或无权限。

#### `get_answer_records`

```python
def get_answer_records(self, subject_data: Union[Subject, str], exam_data: Union[Exam, str] = "") -> AnswerRecord
```

获取逐题得分详情，返回 `AnswerRecord`（`ExtendedList[TopicRecord]`）。

结构层次：

```text
AnswerRecord
└── TopicRecord(title, score, standard_score, subtopic_records)
    └── SubTopicRecord(score, marking_records)
        └── MarkingRecord(time: datetime, score: float)
```

> 源码注释标注 `# TODO: 需要测试`；解析依赖 `sheetDatas` JSON。

### 7.6 班级与同学

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `get_clazzs` | `get_clazzs() -> ExtendedList[StuClass]` | 获取当前年级全部班级（班级的 `grade`/`school` 取自本人） |
| `get_clazz` | `get_clazz(clazz_data: Union[StuClass, str] = "") -> Optional[StuClass]` | 按 ID/名称查班级；留空返回本人班级 `self.clazz`；传入实例直接返回 |
| `get_classmates` | `get_classmates(clazz_data: Union[StuClass, str] = "") -> ExtendedList[StuPerson]` | 获取指定班级学生列表；留空为本班 |

> `get_clazz` 内部用 `str.isdigit()` 区分 ID 与名称，因此**纯数字的班级名称会被误判为 ID**。
> 同学列表字段：`name`、`id`、`clazz`、`code`、`gender`、`mobile`。
> ⚠️ 涉及其他学生隐私，请谨慎使用。

### 7.7 作业

#### `get_homeworks`

```python
def get_homeworks(self, size: int = 20, is_complete: bool = False, subject_code: str = "-1", create_time: int = 0) -> ExtendedList[StuHomework]
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `size` | `int` | `20` | 返回条数（`pageSize`） |
| `is_complete` | `bool` | `False` | `False` 取未完成，`True` 取已完成 |
| `subject_code` | `str` | `"-1"` | 学科代码，`"01"` 语文、`"02"` 数学…；`"-1"` 表示全部 |
| `create_time` | `int` | `0` | 只取创建时间早于某时刻的作业，`0` 表示从最新开始 |

**返回值**：`ExtendedList[StuHomework]`，时间字段已换算为**秒级**（`begin_time` / `end_time` / `create_time`）。

> ⚠️ 源码中 `pageIndex` 被**硬编码为 `2`**，因此严格来说这并非“第一页”语义，使用时请注意。

#### `get_homework_resources`

```python
def get_homework_resources(self, homework: StuHomework) -> List[HwResource]
```

获取自由出题作业的附件资源（题目文档等）。若 `homework.type.code == 102` 直接返回 `[]`。

`HwResource` 提供 `download(path)` 方法，会把文件保存到 `path/name`：

```python
for hw in student.get_homeworks():
    for res in student.get_homework_resources(hw):
        res.download("./downloads")
```

#### 答案获取三件套

| 方法 | 签名 | 适用作业类型 |
| :--- | :--- | :--- |
| `get_exercise_answer` | `(homework) -> List[HwAnswer]` | 自由出题（code 105） |
| `get_bank_answer` | `(homework) -> List[HwAnswer]` | 题库练习（code 102） |
| `get_homework_answer` | `(homework) -> List[HwAnswer]` | 分发器，其它类型返回 `[]` |

`HwAnswer` 含 `title`（题号/标题）与 `content`（答案文本）。

> 作业类型代码（`HwType.code`）参考：`105` 自由出题、`102` 题库练习。

### 7.8 错题本

```python
def get_errorbook(self, exam_id, topic_set_id: str) -> List[ErrorBookTopic]
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `exam_id` | `str` | — | 考试 ID |
| `topic_set_id` | `str` | — | 学科 ID（即 `Subject.id`） |

**返回值**：`List[ErrorBookTopic]`，每个元素包含题目 HTML、答案 HTML、难度、班级得分率、标准答案等（见 10.15）。

**异常**：当响应 `errorCode != 0` 时抛 `Exception(data)`。典型场景：尚未收集到试题信息（`errorCode = 40217`，`errorInfo = 暂时未收集到试题信息,无法查看`），**调用方应 try/except 捕获**。

```python
try:
    topics = student.get_errorbook(exam.id, subject.id)
except Exception as e:
    print("该学科暂无错题数据:", e)
```

### 7.9 内部/私有方法（不建议直接调用）

| 方法 | 说明 |
| :--- | :--- |
| `_get_latest_valid_academic_year()` | 从最近学年向前查找第一个含考试的学年 |
| `__get_self_mark(exam, has_total_score)` | `get_self_mark` 的实际实现 |
| `__get_subjects(exam)` | 拉取考试学科（未做名称/ID 匹配） |
| `__get_subject(exam, subject_data)` | 学科匹配 |
| `__get_original(topic_set_id, exam_id)` | 原卷图片抓取 |
| `__get_answer_records(topic_set_id, exam_id)` | 答题详情抓取 |
| `__get_classmates(clazz_id)` | 同学列表抓取 |
| `_set_exam_rank(mark)` | 通过 `getLevelTrend` / `getSubjectDiagnosis` 反算班级排名 |

---

## 8. 教师账号 `TeacherAccount`

模块路径：`zhixuewang/teacher/teacher.py`。

```python
class TeacherAccount(Account, TeaPerson):
    teaching_classes: List[StuClass]        # 教学班级列表
    school: Optional[School]                # 所在学校
    cur_phase: Optional[Phase]              # 当前学段
    cur_subject: Optional[BasicSubject]     # 当前学科
    book_version: Optional[str]             # 书籍版本名
    textbook_version: Optional[TextBook]    # 教科书版本
    phase_subjects_grades: List[PhaseSubjectGrade]  # 学段-学科-年级
    cur_teaching_grades: List[Grade]        # 当前教学年级
```

继承 `Account` 与 `TeaPerson`，因此拥有 `id`、`name`、`login_name`、`roles`、`province`、`city`、`district`、`mobile` 等属性。

### 8.1 初始化信息

#### `set_base_info`

```python
def set_base_info(self) -> "TeacherAccount"
```

请求 `{BASE_URL}/container/container/teacher/teacherAccountNew`，解析 `teacher` 节点，设置 `id`、`mobile`、`name`、`roles`。

> 未知角色会被**静默跳过**（例如家长角色，避免 `TeacherRole.from_zxw` 抛错）。

#### `set_advanced_info`

```python
def set_advanced_info(self) -> "TeacherAccount"
```

请求 `{BASE_URL}/paperfresh/api/common/getCurrentUser`，解析 `result` 并填充：

- 基本信息：`id`、`login_name`、`name`、`mobile`
- `roles`：由 `TeacherRole.from_zxw(eName)` 转换
- 地区：`province` / `city` / `district`（注意响应字段名为 `distinct`，源码已做映射）
- `school`、`cur_phase`、`cur_subject`、`book_version`、`textbook_version`
- `cur_teaching_grades` 与 `teaching_classes`（从 `curTeachingGrades[].clazzs` 提取）
- `phase_subjects_grades`（从 `phaseAndSubjects` 提取）

> HTTP 状态码非 200 时**直接返回 `self`**，不做任何赋值。

### 8.2 `get_exams`

```python
def get_exams(
    self,
    year: int = 0,
    index: int = 1,
    class_id: str = "all",
    exam_name: str = "",
    grade_code: str = "all",
    subject_code: str = "all",
    exam_type_code: str = "all",
    page_size: int = 15,
    page_index: int = 1,
) -> PageExam
```

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `year` | `int` | `0` | 按**入学年级**查询，如 `2022`；为 `0` 时改按学期查询 |
| `index` | `int` | `1` | 按学期查询时，距当前第几个学期（`1` 为最新学期） |
| `class_id` | `str` | `"all"` | 班级筛选 |
| `exam_name` | `str` | `""` | 考试名称筛选 |
| `grade_code` | `str` | `"all"` | 年级筛选 |
| `subject_code` | `str` | `"all"` | 学科筛选 |
| `exam_type_code` | `str` | `"all"` | 考试类型筛选 |
| `page_size` | `int` | `15` | 每页条数 |
| `page_index` | `int` | `1` | 页码 |

**返回值**：`PageExam(exams, page_index, page_size, all_pages, has_next_page)`。

**行为**：

- `year == 0`：调用 `_get_academic_info()` 取学期列表，用 `index - 1` 定位学期，`searchType = "schoolYearType"`。
- `year != 0`：`searchType = "circlesType"`，直接按年级查询。
- 结果中的 `Exam.subjects` 来自 `zxSubjects`，会**排除复合学科**（如理综，`isMultiSubject == True`）。

### 8.3 `get_exam_detail`

```python
def get_exam_detail(self, exam_id: str) -> Optional[Exam]
```

请求 `POST {BASE_URL}/api-classreport/class/examInfo/`（表单 `examId`），返回包含考试科目、参考班级、参考学校的 `Exam`；考试不存在时返回 `None`。

> 注意：源码注释指出该接口**无法完整获取各科满分**。当不同班级的同名学科 `topicSetId` 冲突时会抛 `ValueError`（提示到 issue 反馈）。

### 8.4 `get_exam_subjects`

```python
def get_exam_subjects(self, exam_id: str) -> ExtendedList[Subject]
```

获取某场考试的全部考试科目，**排除“总分”与学科组**（`isSubjectGroup`）。结果按 `code` 升序排序。

### 8.5 `get_school_exam_classes`

```python
def get_school_exam_classes(
    self,
    school_id: Optional[str] = None,
    topic_set_id: Optional[str] = None,
    exam_id: Optional[str] = None,
) -> ExtendedList[StuClass]
```

获取某学校参加某考试的班级列表。

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `school_id` | `str` | `None` | 学校 ID；为空时使用 `self.school.id` |
| `topic_set_id` | `str` | `None` | 科目 ID（与 `exam_id` 至少传一个，优先使用） |
| `exam_id` | `str` | `None` | 考试 ID；仅传该参数时会取其第一门科目 |

**异常**：`ValueError`——教师未关联学校、或 `topic_set_id` 与 `exam_id` 都未传。

> 返回的班级**只有 `id` / `name` / `grade` / `school.id`**，其余信息为空（接口限制）。

### 8.6 `get_original_paper`

```python
def get_original_paper(self, user_id: str, topic_set_id: str, save_to_path: Optional[str] = None) -> OriginalPaper
```

获取指定学生的原卷信息，并解析为 `OriginalPaper`。

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `user_id` | `str` | — | 学生用户 ID |
| `topic_set_id` | `str` | — | 学科 ID（`paperId`） |
| `save_to_path` | `str` | `None` | 保存渲染后 HTML 的文件名；相对地址会被补全为绝对地址 |

**返回值**：`OriginalPaper`，含 `total_score`、`answer_details`、`answer_sheet_images`，并提供：

- `objective_questions`：`answer_type == "s01Text"` 的题目
- `subjective_questions`：`answer_type == "s02Image"` 的题目
- `total_objective_score` / `total_subjective_score`

解析方式：正则从 HTML 中提取 `var totalScore`、`var sheetImages`、`var sheetDatas`，再解析 `userAnswerRecordDTO.answerRecordDetails`；解析失败时静默返回空数据。

```python
paper = teacher.get_original_paper(student_id, subject_id, save_to_path="paper.html")
print(paper.total_score, len(paper.answer_details))
for q in paper.objective_questions:
    print(q.topic_number, q.is_correct, q.score, q.answer)
```

### 8.7 `get_marking_progress`

```python
def get_marking_progress(self, topic_set_id: str) -> ExtendedList[MarkingProgress]
```

获取某场考试指定科目的阅卷进度。

**请求**：`POST https://pt-ali-bj-re.zhixue.com/marking/marking/markingTopicProgress/`，表单 `markingPaperId`，头部 `token = self.get_token()`。

**返回值**：`ExtendedList[MarkingProgress(topic_number, complete_rate, complete_count, all_count)]`（源码注释：`comleteRate` 是接口本来的错误拼写）。

### 8.8 `get_token` / `to_teacher`

```python
def get_token(self) -> str
```

教师端简单 token：`GET {BASE_URL}/container/app/token/getToken`，取 `result` 字段，并在实例内缓存（`self._token`）。

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `to_teacher` | `to_teacher() -> "TeacherAccount"` | 返回 `self` |

### 8.9 私有方法

| 方法 | 说明 |
| :--- | :--- |
| `_get_academic_info()` | 获取学年/学期教学周期列表，按 `begin_time` 倒序 |
| `_parse_original_paper_html(html, user_id, topic_set_id)` | 原卷 HTML 解析实现 |

---

## 9. 家长账号 `ParentAccount`

模块路径：`zhixuewang/parent/parent.py`。

```python
class ParentAccount(StudentAccount):
    def __init__(self, session):
        super().__init__(session)
        self.role = Role.parent
```

家长账号**继承 `StudentAccount` 的全部能力**（因此 `get_self_mark()`、`get_exams()`、`get_homeworks()`、`get_errorbook()` 等均可用），但以下接口被显式禁用，调用时抛 `NotImplementedError`（源码用 `typing_extensions.deprecated` 装饰，IDE 会给出弃用告警）：

| 方法 | 签名 | 抛出的异常信息 |
| :--- | :--- | :--- |
| `set_base_info` | `set_base_info()` | `家长账户无法获取基本信息` |
| `get_classmates` | `get_classmates(clazz_data="")` | `家长账户无法获取同班同学列表` |
| `get_clazzs` | `get_clazzs()` | `家长账户无法获取班级列表` |
| `get_clazz` | `get_clazz(clazz_data="")` | `家长账户无法获取班级信息` |
| `to_student` | `to_student()` | `ParentAccount无法转换为StudentAccount` |

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `to_parent` | `to_parent() -> "ParentAccount"` | 返回 `self` |

```python
parent = login_cookie(cookies).to_parent()
for s in parent.get_self_mark():
    print(s.subject.name, s.score, s.class_rank)
```

---

## 10. 数据模型 `zhixuewang.models`

所有模型均为 `dataclass`，字段带默认值的可用关键字构造。以下字段顺序与源码一致。

### 10.1 枚举

```python
class Role(Enum):        # 账号角色
    student = 0
    teacher = 1
    parent  = 2

class Sex(Enum):         # 性别，__str__ 返回中文
    GIRL = "女"
    BOY  = "男"
```

### 10.2 `ExtendedList[T]`

继承自 `list`，为所有集合类型的基类。

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `foreach` | `foreach(f: Callable[[T], None])` | 遍历并执行副作用 |
| `find` | `find(f: Callable[[T], bool]) -> Optional[T]` | 返回首个满足条件的元素，否则 `None` |
| `find_all` | `find_all(f) -> ExtendedList[T]` | 返回所有满足条件的元素 |
| `find_by_name` | `find_by_name(name: str) -> Optional[T]` | 按 `.name` 精确匹配首个 |
| `find_all_by_name` | `find_all_by_name(name: str) -> ExtendedList[T]` | 按 `.name` 匹配全部 |
| `find_by_id` | `find_by_id(spec_id: str) -> Optional[T]` | 按 `.id` 精确匹配首个 |
| `find_all_by_id` | `find_all_by_id(spec_id: str) -> ExtendedList[T]` | 按 `.id` 匹配全部 |

```python
exam = student.get_exams().find_by_name("期中考试")
subjects = student.get_subjects().find_all(lambda s: s.code in ("01", "02"))
```

### 10.3 `AcademicYear` 学年

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `name` | `str` | `""` |
| `code` | `str` | `""` |
| `begin_time` | `str` | `""` |
| `end_time` | `str` | `""` |

### 10.4 `Grade` 年级

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `name` | `str` | `""` |
| `code` | `str` | `""` |
| `phase_name` | `str` | `""` |
| `phase_code` | `str` | `""` |

### 10.5 `School` 学校

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `id` | `str` | `""` |
| `name` | `str` | `""` |

`__str__` 返回学校名。

### 10.6 `StuClass` 班级

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `id` | `str` | `""` |
| `name` | `str` | `""` |
| `grade` | `Grade` | `Grade()` |
| `school` | `School` | `School()` |

- `eq=False` + 自定义 `__eq__`：**仅比较 `id`**。
- `__str__`：`学校: {school} 年级: {grade.name} 班级: {name}`。

### 10.7 `Person` / `StuPerson`

`Person`（基本人员信息）：

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `id` | `str` | `""` |
| `name` | `str` | `""` |
| `gender` | `Sex` | `Sex.GIRL` |
| `mobile` | `str` | `""` |
| `avatar` | `str` | `""` |

`StuPerson`（继承 `Person`，学生信息）：

| 额外字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `code` | `str` | `""` |
| `clazz` | `StuClass` | `StuClass()` |

`__str__` 输出班级、姓名、性别与（可选的）手机号。

### 10.8 `BasicSubject` / `Subject`

`BasicSubject`：

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `name` | `str` | `""` |
| `code` | `str` | `""` |

`Subject`（继承 `BasicSubject`）：

| 额外字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | `str` | `""` | 即 `paperId` / `topicSetId` |
| `standard_score` | `float` | `0` | 满分 |
| `exam_id` | `str` | `""` | 所属考试 ID |
| `create_user` | `Person` | `Person()` | 创建人 |
| `create_time` | `float` | `0` | 创建时间 |

- `eq=False` + 自定义 `__eq__`：**仅比较 `id`**。

### 10.9 `TextBook` 教科书

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `code` | `str` | `""` | 教科书编号 |
| `name` | `str` | `""` | 教科书名称 |
| `version` | `str` | `""` | 版本（北师大 / 人教 / 部编…） |
| `versionCode` | `int` | `0` | 版本编号 |
| `bindSubject` | `BasicSubject` | `BasicSubject()` | 绑定学科 |

`__str__`：`{学科名} {名称} ({版本})`。

### 10.10 `Exam` 考试

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | `str` | `""` | 考试 ID |
| `name` | `str` | `""` | 考试名称 |
| `status` | `str` | `""` | 状态 |
| `grade_code` | `str` | `""` | 年级代码 |
| `subjects` | `ExtendedList[Subject]` | `[]` | 考试科目（各班实际科目可能是子集） |
| `clazzs` | `ExtendedList[StuClass]` | `[]` | 参考班级 |
| `schools` | `ExtendedList[School]` | `[]` | 参考学校 |
| `create_user` | `Person` | `Person()` | 创建人 |
| `create_time` | `float` | `0` | 创建时间 |
| `class_rank` | `int` | `0` | 班级排名 |
| `grade_rank` | `int` | `0` | 年级排名 |
| `academic_year` | `AcademicYear` | `AcademicYear()` | 所属学年 |
| `is_final` | `bool` | `False` | 是否期末考试 |

- `eq=False` + 自定义 `__eq__`：**仅比较 `id`**。
- 自定义 `__bool__`：`bool(exam)` 等价于 `bool(exam.id)`——空考试为 `False`。

### 10.11 `SubjectScore` 单科成绩

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `score` | `float` | `0` | 得分 |
| `subject` | `Subject` | `Subject()` | 学科 |
| `person` | `StuPerson` | `StuPerson()` | 学生 |
| `class_rank` | `int` | `0` | 班级排名 |
| `grade_rank` | `int` | `0` | 年级排名 |
| `exam_rank` | `int` | `0` | 考试排名 |

`__str__`：当 `person.id == ""` 时输出 `学科: 分数 (班级第N名)`，否则回退到 `repr`。

### 10.12 `Mark` 一场考试的成绩

```python
class Mark(ExtendedList[SubjectScore]):
    exam: Exam
    person: StuPerson
    def __init__(self, ls=None, exam=None, person=None)
```

- 可直接迭代得到 `SubjectScore`。
- `__str__` / `__repr__` 输出形如：

```text
张三-2026学年第一学期期末考试
语文: 121.0
数学: 121.0
总分: 379.5
```

### 10.13 答题详情相关

`MarkingRecord` 批改记录：

| 字段 | 类型 |
| :--- | :--- |
| `time` | `datetime` |
| `score` | `float` |

`SubTopicRecord` 小题得分：

| 字段 | 类型 |
| :--- | :--- |
| `score` | `float` |
| `marking_records` | `ExtendedList[MarkingRecord]` 或 `None` |

`TopicRecord` 题目得分：

| 字段 | 类型 |
| :--- | :--- |
| `title` | `str` |
| `score` | `float` |
| `standard_score` | `float` |
| `subtopic_records` | `ExtendedList[SubTopicRecord]` 或 `None` |

`AnswerRecord`：`ExtendedList[TopicRecord]` 的子类，无新增字段。

### 10.14 作业相关

`HwType` 作业类型：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `name` | `str` | `""` | 类型名 |
| `code` | `int` | `0` | 类型码（105 自由出题、102 题库练习） |

`Homework` / `StuHomework`：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | `str` | **必填** | 作业 ID |
| `title` | `str` | `""` | 标题 |
| `type` | `HwType` | `HwType()` | 作业类型 |
| `begin_time` | `int` | `0` | 开始时间（秒） |
| `end_time` | `int` | `0` | 截止时间（秒） |
| `create_time` | `int` | `0` | 创建时间（秒） |
| `subject_name` | `str` | `""` | 学科名 |
| `is_allow_makeup` | `bool` | `False` | 是否允许重做 |
| `class_id` | `str` | `""` | 班级 ID |
| `stu_hwid` | `str` | `""` | **仅 `StuHomework`**，学生作业 ID |

`HwResource` 作业资源：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `path` | `str` | 资源下载地址 |
| `name` | `str` | 文件名 |

方法：`download(path)` —— 使用**全新 Session**（不带登录态）下载并写入 `path/name`。

`HwAnswer` 作业答案：

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `title` | `str` | `""` |
| `content` | `str` | `""` |

### 10.15 `ErrorBookTopic` 错题

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `analysis_html` | `str` | 解析 HTML |
| `answer_html` | `str` | 答案 HTML |
| `answer_type` | `str` | 作答类型 |
| `is_correct` | `bool` | 是否答对 |
| `class_score_rate` | `float` | 班级得分率 |
| `content_html` | `str` | 题干 HTML |
| `difficulty` | `int` | 难度值 |
| `dis_title_number` | `str` | 显示题号 |
| `paper_id` | `str` | 试卷 ID |
| `subject_name` | `str` | 学科名 |
| `score` | `float` | 得分 |
| `standard_answer` | `str` | 标准答案（网址） |
| `standard_score` | `float` | 满分 |
| `topic_set_id` | `str` | 题目 ID |
| `topic_img_url` | `str` | 题目图片 |
| `topic_source_paper_name` | `str` | 题目来源试卷名 |
| `image_answer` | `List[str]` | 学生作答图片 |
| `topic_analysis_img_url` | `str` | 解析图片 |

### 10.16 私信相关

`MessageUser` 消息用户：

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `user_id` | `str` | `""` |
| `user_name` | `str` | `""` |
| `role` | `str` | `""` |
| `school_id` | `str` | `""` |
| `area_id` | `str` | `""` |
| `city_id` | `str` | `""` |
| `country_id` | `str` | `""` |
| `province_id` | `str` | `""` |

`PersonalMessage` 私信：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | `int` | **必填** | 消息 ID |
| `content` | `str` | **必填** | 内容 |
| `create_time` | `int` | **必填** | 创建时间（毫秒） |
| `update_time` | `int` | **必填** | 更新时间（毫秒） |
| `send_user_id` | `str` | **必填** | 发送者 ID |
| `sender` | `MessageUser` | **必填** | 发送者信息 |
| `notify_id` | `int` | **必填** | 通知 ID |
| `subscriber` | `str` | `""` | 订阅者 ID |
| `subscriber_name` | `str` | `""` | 订阅者名称 |
| `subscriber_role` | `str` | `""` | 订阅者角色 |
| `is_delete` | `bool` | `False` | 是否删除 |
| `is_top` | `bool` | `False` | 是否置顶 |
| `view_count` | `int` | `0` | 查看次数 |
| `like_count` | `int` | `0` | 点赞数 |
| `comment_count` | `int` | `0` | 评论数 |

方法：

| 方法 | 说明 |
| :--- | :--- |
| `get_create_datetime() -> datetime` | 创建时间 `datetime` |
| `get_update_datetime() -> datetime` | 更新时间 `datetime` |

`MessagePageInfo` 分页信息：`current_page`、`page_size`、`total_count`、`total_page`、`first_page`、`last_page`、`next_page`、`prev_page`（均 `int`，必填），`all_pages: List[int] = []`。

`PersonalMessageList`：

| 字段 | 类型 |
| :--- | :--- |
| `messages` | `ExtendedList[PersonalMessage]` |
| `page_info` | `MessagePageInfo` |
| `total_count` | `int` |

方法：

| 方法 | 说明 |
| :--- | :--- |
| `get_unread_messages() -> ExtendedList[PersonalMessage]` | `view_count == 0` 的消息 |
| `get_messages_by_sender(sender_id: str) -> ExtendedList[PersonalMessage]` | 按 `send_user_id` 过滤 |

---

## 11. 教师数据模型 `zhixuewang.teacher.models`

### 11.1 `TeacherRole` 教师角色

```python
class TeacherRole(Enum):
    TEACHER              = "老师"
    HEADMASTER           = "校长"
    VICE_HEADMASTER      = "副校长"
    VICE_HEADTEACHER     = "副班主任"
    HEADTEACHER          = "班主任"
    SCHOOL_ADMINISTRATOR = "校管理员"
    GRADE_DIRECTER       = "年级组长"
    SUBJECT_LEADER       = "备课组长"
```

| 方法 | 签名 | 说明 |
| :--- | :--- | :--- |
| `from_zxw` | `from_zxw(label: str) -> "TeacherRole"` | 由英文标签（如 `"teacher"`、`"headmaster"`）转换；未知标签抛 `ValueError` |
| `to_zxw` | `to_zxw() -> str` | 反向转换；无法匹配时抛 `ValueError` |
| `__str__` | — | 返回中文名 |

英文标签映射表（`ROLE_TABLE`）：

| 英文标签 | 中文角色 |
| :--- | :--- |
| `teacher` | 老师 |
| `subjectLeader` | 备课组长 |
| `gradeDirecter` | 年级组长 |
| `headteacher` | 班主任 |
| `headmaster` | 校长 |
| `viceHeadteacher` | 副班主任 |
| `viceHeadmaster` | 副校长 |
| `schoolAdministrator` | 校管理员 |

### 11.2 地理与学段

`Phase` 学段：`code: str = ""`、`name: str = ""`。

`Region` 地区：`code: str = ""`、`name: str = ""`、`level: Optional[str] = None`、`id: str = ""`。

`PhaseSubjectGrade` 学段-学科-年级：`phase: Phase`、`subjects: List[BasicSubject]`、`grades: List[Grade]`。

### 11.3 `TeaPerson` 教师信息

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `id` | `str` | `""` |
| `name` | `str` | `""` |
| `gender` | `Sex` | `Sex.GIRL` |
| `mobile` | `str` | `""` |
| `avatar` | `str` | `""` |
| `login_name` | `str` | `""` |
| `roles` | `List[TeacherRole]` | `[]` |
| `province` | `Optional[Region]` | `None` |
| `city` | `Optional[Region]` | `None` |
| `district` | `Optional[Region]` | `None` |

### 11.4 `PageExam` 分页考试

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `exams` | `List[Exam]` | 本页考试 |
| `page_index` | `int` | 当前页 |
| `page_size` | `int` | 每页条数 |
| `all_pages` | `int` | 总页数 |
| `has_next_page` | `bool` | 是否有下一页 |

### 11.5 `MarkingProgress` 阅卷进度

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `topic_number` | `str` | 题号 |
| `complete_rate` | `float` | 完成率（源字段 `comleteRate`） |
| `complete_count` | `int` | 已阅数量 |
| `all_count` | `int` | 总数量 |

### 11.6 `AcademicInfo` 教学周期

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `term_id` | `str` | 学期 ID |
| `circles_year` | `str` | 学年 |
| `teaching_cycle_id` | `str` | 教学周期 ID |
| `begin_time` | `int` | 开始时间（毫秒时间戳） |
| `end_time` | `int` | 结束时间（毫秒时间戳） |
| `school_id` | `str` | 学校 ID |

### 11.7 `OriginalPaper` 原卷

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `user_id` | `str` | **必填** | 学生 ID |
| `topic_set_id` | `str` | **必填** | 科目 ID |
| `total_score` | `float` | **必填** | 总分 |
| `answer_details` | `List[AnswerRecordDetail]` | `[]` | 答题详情 |
| `answer_sheet_images` | `List[str]` | `[]` | 答题卡图片 |

属性：

| 属性 | 类型 | 说明 |
| :--- | :--- | :--- |
| `objective_questions` | `List[AnswerRecordDetail]` | `answer_type == "s01Text"` |
| `subjective_questions` | `List[AnswerRecordDetail]` | `answer_type == "s02Image"` |
| `total_objective_score` | `float` | 客观题得分合计 |
| `total_subjective_score` | `float` | 主观题得分合计 |

### 11.8 `AnswerRecordDetail` / `SubTopicDetail` / `TeacherMarkingRecord`

`AnswerRecordDetail` 答题详情：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `topic_number` | `int` | **必填** | 题号 |
| `disp_title` | `str` | **必填** | 显示题号 |
| `answer` | `str` | **必填** | 学生答案（文本） |
| `score` | `float` | **必填** | 得分 |
| `standard_score` | `float` | **必填** | 满分 |
| `is_correct` | `bool` | **必填** | 是否正确 |
| `answer_type` | `str` | **必填** | 作答类型（`s01Text` / `s02Image`） |
| `source_category_name` | `str` | `""` | 题型（单选、主观题…） |
| `topic_type_id` | `str` | `""` | 题型 ID |
| `sub_topics` | `List[SubTopicDetail]` | `[]` | 小题列表 |
| `is_excellent` | `bool` | `False` | 是否优秀 |
| `is_typical_error` | `bool` | `False` | 是否典型错误 |
| `marking_paper_topic_id` | `str` | `""` | 批改题目 ID |

`SubTopicDetail` 小题详情：

| 字段 | 类型 | 默认值 |
| :--- | :--- | :--- |
| `score` | `float` | **必填** |
| `sub_topic_index` | `int` | `-1` |
| `score_source` | `str` | `""` |
| `teacher_marking_records` | `List[TeacherMarkingRecord]` | `[]` |

`TeacherMarkingRecord` 教师批改记录：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `score` | `float` | **必填** | 给分 |
| `marking_time` | `int` | **必填** | 批改时间（毫秒时间戳） |
| `teacher_name` | `str` | `""` | 教师姓名 |
| `teacher_id` | `str` | `""` | 教师 ID |
| `role` | `str` | `""` | 批改角色（`marking1` 等） |
| `is_excellent` | `bool` | `False` | 是否优秀 |
| `is_typical_error` | `bool` | `False` | 是否典型错误 |
| `marking_content` | `str` | `""` | 批改内容（JSON 字符串） |

---

## 12. 工具与辅助 API

### 12.1 `zhixuewang.session`

```python
def get_basic_session() -> requests.Session
```

创建一个全新的 Session，设置 `trust_env = False`，并将 `User-Agent` 设为：

```text
Mozilla/5.0 (Windows NT 6.1; rv:2.0.1) Gecko/20100101 Firefox/4.0.1
```

> 该 UA 是火狐 4.0.1 的旧 UA，库内固定使用。`trust_env = False` 会忽略系统代理与 `HTTP_PROXY` 等环境变量。

### 12.2 `zhixuewang.tools.datetime_tool`

```python
def timestamp2datetime(timestamp: float) -> datetime.datetime
def get_property(arg_name: str) -> property
```

- `timestamp2datetime`：以 1970-01-01 为基准加上秒数。
- `get_property(arg_name)`：返回一个 property，其 setter 会把**毫秒时间戳**转换为 `datetime` 存入对象 `__dict__[arg_name]`。

```python
from zhixuewang.tools import get_property

class Foo:
    created_at = get_property("created_at")
```

### 12.3 `zhixuewang.tools` 导出

```python
from zhixuewang.tools import get_property   # __all__ = ["get_property"]
```

---

## 13. 异常体系

模块：`zhixuewang/exceptions.py`。所有异常均继承自 `Error`，其 `__str__` 返回 `self.value`。

```text
Exception
└── Error
    ├── LoginError
    │   ├── UserOrPassError      # 默认 "用户名或密码错误!"
    │   ├── UserNotFoundError    # 默认 "用户不存在!"
    │   └── UserDefunctError     # 默认 "用户已失效!"（学生无班级时抛出）
    ├── RoleError                # 默认 "账号是未知用户"
    ├── ArgError                 # 默认 "请输入正确的参数!"
    └── PageConnectionError      # 请求/解析失败，携带原始响应文本
```

| 异常 | 默认信息 | 常见触发点 |
| :--- | :--- | :--- |
| `UserOrPassError` | 用户名或密码错误! | 登录失败 |
| `UserNotFoundError` | 用户不存在! | 账号不存在 |
| `UserDefunctError` | 用户已失效! | `set_base_info()` 中学生数据无 `clazz` |
| `RoleError` | 账号是未知用户 | 角色判定异常 |
| `ArgError` | 请输入正确的参数! | 参数校验 |
| `PageConnectionError` | — | 任意接口返回非 2xx 或数据异常，消息形如 `get_auth_header出错 \n {响应文本}` |

> ⚠️ 并非所有错误都包装为上述异常：`get_errorbook()` 在 `errorCode != 0` 时抛**裸 `Exception`**；`get_exam_detail()` 冲突时抛 `ValueError`；`get_school_exam_classes()` 参数缺失时抛 `ValueError`。

---

## 14. 底层 HTTP 接口清单

本节面向需要二次开发 / 抓包对照的开发者。所有请求均通过 `Account.get_session()` 返回的 Session 发出。

### 14.1 基础约定

| 常量 | 值 |
| :--- | :--- |
| `BASE_URL` | `https://www.zhixue.com` |
| `APP_BASE_URL` | `https://mhw.zhixue.com` |

### 14.2 学生端 XToken 鉴权

```text
authguid  = uuid4()
authtimestamp = 毫秒时间戳
authtoken = md5(authguid + authtimestamp + "iflytek!@#123student")

GET /container/app/token/getToken
Headers: authbizcode=0001, authguid, authtimestamp, authtoken
→ resp.result 即 XToken（缓存 600s）
```

后续业务请求携带 Header `XToken: <token>`。

### 14.3 公共 / 登录接口

| 功能 | 方法 | 地址 | 对应 API |
| :--- | :--- | :--- | :--- |
| 获取当前用户角色 | GET | `/container/getCurrentUser` | `get_account_role` |
| 教师基本信息 | GET | `/container/container/teacher/teacherAccountNew` | `TeacherAccount.set_base_info` |
| 教师详细信息 | GET | `/paperfresh/api/common/getCurrentUser` | `TeacherAccount.set_advanced_info` |
| 获取 token（教师/学生通用） | GET | `/container/app/token/getToken` | `TeacherAccount.get_token` / `StudentAccount.get_auth_header` |
| 私信列表 | GET | `/container/personal/getPersonalMessage/` | `Account.get_personal_messages` |
| 发送私信 | POST | `/container/personal/sendPersonalMsg/` | `Account.send_personal_message` |

### 14.4 学生端接口

| 功能 | 方法 | 地址 | 鉴权 | 对应 API |
| :--- | :--- | :--- | :--- | :--- |
| 学生基本信息 | GET | `/container/container/student/account/` | Cookie | `set_base_info` |
| 学年列表 | GET | `/zhixuebao/base/common/academicYear` | XToken | `get_academic_year` |
| 考试分页列表 | GET | `/zhixuebao/report/exam/getUserExamList` | XToken | `get_page_exam` |
| 最近考试 | GET | `/zhixuebao/report/exam/getRecentExam` | XToken | `get_latest_exam` / `_get_latest_valid_academic_year` |
| 成绩 + 学科 | GET | `/zhixuebao/report/exam/getReportMain` | XToken | `get_self_mark` / `get_subjects` |
| 原卷图片 / 答题数据 | GET | `/zhixuebao/report/checksheet/` | XToken | `get_original` / `get_answer_records` |
| 年级排名趋势 | GET | `/zhixuebao/report/exam/getLevelTrend` | XToken | `_set_exam_rank` |
| 单科排名趋势 | GET | `/zhixuebao/report/paper/getLevelTrend` | XToken | `_set_exam_rank` |
| 学科诊断（班级排名） | GET | `/zhixuebao/report/exam/getSubjectDiagnosis` | XToken | `_set_exam_rank` |
| 错题本 | GET | `/zhixuebao/report/paper/getLostTopicAndAnalysis` | XToken | `get_errorbook` |
| 班级列表 | GET | `/zhixuebao/zhixuebao/friendmanage/` | Cookie | `get_clazzs` |
| 同班同学 | GET | `/container/contact/student/students` | Cookie | `get_classmates` |
| 作业列表 | GET | `https://mhw.zhixue.com/homework_middle_service/stuapp/getStudentHomeWorkList` | XToken | `get_homeworks` |
| 自由出题作业详情/资源 | POST | `https://mhw.zhixue.com/hw/manage/homework/redeploy` | XToken(Authorization) | `get_homework_resources` / `get_exercise_answer` |
| 题库练习答案 | POST | `https://mhw.zhixue.com/hwreport/question/listView` | XToken(Authorization) | `get_bank_answer` |

> `get_homeworks` 把 token 同时放在 **查询参数 `token`** 中；作业相关 POST 接口则放在 **`Authorization` 头**。

### 14.5 教师端接口

| 功能 | 方法 | 地址 | 鉴权 | 对应 API |
| :--- | :--- | :--- | :--- | :--- |
| 考试分页列表 | GET | `/api-classreport/class/classReportList/` | Cookie | `get_exams` |
| 学期教学周期 | GET | `/api-classreport/class/getAcademicTermTeachingCycle/` | Cookie | `_get_academic_info` |
| 考试详情 | POST | `/api-classreport/class/examInfo/` | Cookie | `get_exam_detail` |
| 考试科目 | GET | `/configure/class/getSubjectsIncludeSubAndGroup` | Cookie | `get_exam_subjects` |
| 参考班级 | GET | `/exam/marking/schoolClass` | Cookie | `get_school_exam_classes` |
| 阅卷进度 | POST | `https://pt-ali-bj-re.zhixue.com/marking/marking/markingTopicProgress/` | `token` 头 | `get_marking_progress` |
| 学生原卷 HTML | GET | `/classreport/class/student/checksheet/` | Cookie | `get_original_paper` |

### 14.6 已声明但未被调用的接口常量

以下 URL 常量在 `urls.py` 中定义，但 1.5.1 源码中**没有任何方法使用**，属于预留或历史遗留（可用于二次开发）：

| 常量 | 地址 |
| :--- | :--- |
| `Url.SERVICE_URL` | `/ssoservice.jsp` |
| `Url.SSO_URL` | `https://sso.zhixue.com/sso_alpha/login?service=...` |
| `Url.TEST_PASSWORD_URL` | `/weakPwdLogin/?from=web_login` |
| `Url.GET_LOGIN_STATE` | `/loginState/` |
| `student.Url.SSO_URL` | `https://open.changyan.com/sso/login?sso_from=zhixuesso&service=...` |
| `student.Url.CHANGE_PASSWORD_URL` | `/portalcenter/home/updatePassword/` |
| `student.Url.TEST_PASSWORD_URL` | `/weakPwdLogin/?from=web_login` |
| `student.Url.GET_TEACHERS_URL` | `/container/contact/student/teachers` |
| `student.Url.GET_LOST_TOPIC_URL` | `/zhixuebao/report/paper/getExamPointsAndScoringAbility` |
| `teacher.Url.INFO_URL` | `/container/container/student/account/` |
| `teacher.Url.CHANGE_PASSWORD_URL` | `/portalcenter/home/updatePassword/` |
| `teacher.Url.GET_REPORT_URL` | `/exportpaper/class/getExportStudentInfo` |
| `teacher.Url.GET_STUDENT_STATUS_URL` | `/api-teacher/home/getStudentStatus` |

---

## 15. 扩展开发指南

### 15.1 复用已有 Session

```python
from zhixuewang import login_playwright

account = login_playwright("账号", "密码")
session = account.get_session()          # 已带登录 Cookie

resp = session.get("https://www.zhixue.com/some/endpoint", params={"k": "v"})
data = resp.json()
```

### 15.2 封装自定义扩展函数

推荐让扩展函数接收 `StudentAccount` / `TeacherAccount` 实例作为参数，而不是继承：

```python
from zhixuewang.models import ExtendedList
from zhixuewang.student.student import StudentAccount

def get_high_scores(account: StudentAccount, threshold: float = 130) -> ExtendedList:
    mark = account.get_self_mark()
    return mark.find_all(lambda s: s.score >= threshold)
```

### 15.3 使用 `rewrite_str` 定制输出

```python
from zhixuewang import rewrite_str
from zhixuewang.models import Exam

@rewrite_str(Exam)
def _(self: Exam):
    return f"[{self.id}] {self.name}"

print(student.get_latest_exam())
```

### 15.4 注意事项

1. **`_session` 是内部属性**，对外建议使用 `get_session()`。
2. **`asyncio.run` 限制**：`login_playwright` 不能在事件循环内调用；如需异步，可参考 `account.playwright_process` 自行封装。
3. **接口易变**：智学网接口无公开契约，字段名可能变化（例如 `distinct`、`comleteRate` 拼写均为服务端原样）。
4. 新增接口时，建议在对应的 `urls.py`（`student` / `teacher`）中登记 URL 常量，保持结构一致。
5. 排序/筛选尽量复用 `ExtendedList` 的检索方法，保持返回类型一致。
6. 时间字段建议统一在解析层换算（作业为秒、私信为毫秒、`MarkingRecord.time` 为 `datetime`，并不统一）。

### 15.5 编码规范（摘自官方文档）

- 遵循 PEP 8，函数与变量使用下划线命名法（`snake_case`）。
- 参数必须有类型注解，优先使用关键字参数。
- 参数命名需有意义，避免 `id`、`user`、`id1` 之类的模糊名称。

```python
# 推荐
def get_sth_by_sth(exam_id: str, someone_user_name: str, someone_id: int): ...

# 不推荐
def get_sth_by_sth(id: str, user: str, id_1: str): ...
```

---

## 16. 已知问题与注意事项

| # | 问题 | 影响 / 规避 |
| :--- | :--- | :--- |
| 1 | `get_page_exam` 参数名拼写为 `acamemic_year` | 只能用关键字 `acamemic_year=` 传参 |
| 2 | `get_homeworks` 的 `pageIndex` 硬编码为 `2` | 返回结果非严格“第一页” |
| 3 | `get_homeworks` 在 1.5.1 中把 token 放在 URL 参数里 | 与作业 POST 接口的 `Authorization` 头不一致 |
| 4 | `get_errorbook` 抛裸 `Exception` | 必须自行 try/except |
| 5 | `get_exam_detail` 学科 ID 冲突时抛 `ValueError` | 提示到 GitHub Issues 反馈 |
| 6 | `set_advanced_info` 遇到非 200 静默返回 | 教师详细信息可能为空，需容错 |
| 7 | `login_cookie` 的字符串 Cookie 仅支持 `"; "` 分隔 | 建议直接传 dict |
| 8 | `login_cookie` 依赖 `loginUserName` 键 | 缺失会 `KeyError` |
| 9 | `login_playwright` 使用 `asyncio.run` | 不可在已有事件循环中调用 |
| 10 | Playwright 等待人机验证为**无限超时** | 需人工完成，脚本可能长时间挂起 |
| 11 | `get_clazz` 用 `isdigit()` 区分 ID/名称 | 纯数字班级名会被误判 |
| 12 | `_check_is_uuid` 为启发式判断 | 不符合 UUID v4 规则的 ID 会被当作名称 |
| 13 | `get_exams`（学生）耗时且全量拉取 | 结果有缓存，存在大账号长耗时风险 |
| 14 | `HwResource.download` 使用无 Cookie 的新 Session | 需要鉴权的资源可能下载失败 |
| 15 | 部分接口 `TODO: 需要测试`（如 `get_answer_records`） | 稳定性未知 |
| 16 | 家长账号 `to_student()` 抛异常 | 家长应使用 `to_parent()` |
| 17 | `get_original` 在无数据时会 `print` 调试信息 | 会污染标准输出 |
| 18 | 非官方库，接口随时可能因智学网更新而失效 | 参考项目维护说明 |

---

## 附录 A：API 速查表

### A.1 顶层导出

```python
from zhixuewang import login_cookie, login_playwright, rewrite_str
```

> 注意：顶层 `__all__` **仅**包含上述三个名字。`StudentAccount` / `TeacherAccount` / `ParentAccount` / 模型类需从子模块导入：
>
> ```python
> from zhixuewang.student import StudentAccount
> from zhixuewang.teacher import TeacherAccount
> from zhixuewang.parent import ParentAccount
> from zhixuewang.models import Exam, Subject, Mark
> ```

### A.2 `Account`（三端通用）

| 方法 |
| :--- |
| `get_session()` |
| `to_student()` / `to_teacher()` / `to_parent()` |
| `get_personal_messages(page_index=1, page_size=10000)` |
| `send_personal_message(receiver_id, content)` |

### A.3 `StudentAccount`

| 分类 | 方法 |
| :--- | :--- |
| 鉴权 | `get_auth_header()` |
| 基础信息 | `set_base_info()` |
| 考试 | `get_academic_year()`、`get_exam(exam_data)`、`get_page_exam(page_index, acamemic_year)`、`get_latest_exam()`、`get_exams()` |
| 成绩 | `get_self_mark(exam_data, has_total_score)`、`get_subjects(exam_data)`、`get_subject(subject_data, exam_data)` |
| 原卷/答题 | `get_original(subject_data, exam_data)`、`get_answer_records(subject_data, exam_data)` |
| 班级/同学 | `get_clazzs()`、`get_clazz(clazz_data)`、`get_classmates(clazz_data)` |
| 作业 | `get_homeworks(size, is_complete, subject_code, create_time)`、`get_homework_resources(hw)`、`get_exercise_answer(hw)`、`get_bank_answer(hw)`、`get_homework_answer(hw)` |
| 错题 | `get_errorbook(exam_id, topic_set_id)` |

### A.4 `TeacherAccount`

| 分类 | 方法 |
| :--- | :--- |
| 基础信息 | `set_base_info()`、`set_advanced_info()` |
| 考试 | `get_exams(year, index, class_id, exam_name, grade_code, subject_code, exam_type_code, page_size, page_index)`、`get_exam_detail(exam_id)`、`get_exam_subjects(exam_id)` |
| 阅卷 | `get_marking_progress(topic_set_id)` |
| 原卷 | `get_original_paper(user_id, topic_set_id, save_to_path)` |
| 班级 | `get_school_exam_classes(school_id, topic_set_id, exam_id)` |
| 鉴权 | `get_token()` |

### A.5 `ParentAccount`

继承 `StudentAccount`，新增/覆盖：`to_parent()`；禁用：`set_base_info()`、`get_clazzs()`、`get_clazz()`、`get_classmates()`、`to_student()`。

---

## 附录 B：版本与贡献

| 项 | 值 |
| :--- | :--- |
| 当前版本 | `1.5.1` |
| `__version__` | `"1.5.1"` |
| `VERSION` | `(1, 5, 1)` |
| 作者 | anwenhu, MasterYuan418, immoses648, krn1pnc, Haorwen, amakerlife |
| 许可证 | MIT |
| 问题反馈 | <https://github.com/anwenhu/zhixuewang/issues> |
| 贡献 | <https://github.com/anwenhu/zhixuewang-python/pulls> |
| 交流群 | QQ 862767072（备注：GitHub智学网库） |

运行期读取版本：

```python
import zhixuewang
print(zhixuewang.__version__)   # 1.5.1
print(zhixuewang.VERSION)       # (1, 5, 1)
```

---

## 附录 C：与官方文档站的差异勘误

官方文档站（<https://anwenhu.github.io/zhixuewang-docs/>）基于 VuePress 构建，部分页面与 **v1.5.1 源码**存在不一致。以源码为准，主要差异如下：

| 官方文档写法 | 实际源码（1.5.1） |
| :--- | :--- |
| `from zhixuewang import login_student, login_teacher`（见 `example/teacher/get_paper.py`） | **不存在**这两个函数，只有 `login_cookie` / `login_playwright` + `to_student()` / `to_teacher()` |
| `student = login_playwright(...).to_student`（漏写括号） | 必须调用 `.to_student()`，否则拿到的是方法对象 |
| `subject_score.rank`（`get_self_mark` 页面） | 不存在 `rank` 字段；应为 `class_rank` / `grade_rank` / `exam_rank` |
| `topic.topic_number`、`topic.topic_type`、`topic.full_score`（`get_errorbook` 页面） | `ErrorBookTopic` 中为 `dis_title_number`、`answer_type`、`standard_score` |
| `parent.md` 中部分示例调用 `get_clazzs()` / `get_classmates()` | 家长账号调用会抛 `NotImplementedError` |
| 文档称 `account.session` | 属性实为私有 `_session`，对外请用 `get_session()` |
| `extend.md` 提到 `from zhixuewang import StudentAccount, TeacherAccount` | 顶层 `__all__` **只导出** `login_cookie`、`login_playwright`、`rewrite_str`；需从子模块导入 |
| `extend.md` 示例 `MyAdvancedStudent(login_playwright(...).get_session())` | 示例缺少导入；且 `StudentAccount.__init__` 仅接收 session，不会自动填充基础信息 |
| `get_exams.md`（学生）描述“获取所有考试” | 方法名正确，但实现会遍历所有学年，耗时长且自动缓存 |

> 建议：以本文档与源码（`zhixuewang/` 目录）为唯一准绳；对文档站的示例代码，先按上表校正后再运行。

---

<sub>本文档由源码静态分析 + 运行时反射（`inspect` / `dataclasses`）生成，对应 zhixuewang v1.5.1。接口可能随上游更新而变化，请以实际安装版本为准。</sub>

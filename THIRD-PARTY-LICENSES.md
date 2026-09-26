# 第三方许可声明

本项目（zhixue-wear）是**基于下列开源项目开发**的衍生作品。
依据 MIT 许可证的要求，其版权声明与许可声明在此**完整保留**。

---

## 1. zhixuewang-python

- **项目地址**：https://github.com/anwenhu/zhixuewang-python
- **作者**：anwenhu（及 MasterYuan418、immoses648、krn1pnc、Haorwen、amakerlife 等贡献者）
- **许可证**：MIT
- **本项目对其的使用方式**：
  - 参考其接口地址定义与请求参数（`zhixuewang/*/urls.py`）
  - 复刻其学生端鉴权算法（XToken = `md5(authguid + authtimestamp + "iflytek!@#123student")`）
  - 参考其数据模型字段命名与业务流程（学年 → 考试 → 成绩 的调用链）
  - 本项目的 Java 实现为独立重写，未直接复制其 Python 源码
  - `docs/api.md` 为该库的接口整理文档

### MIT License 全文（原样保留）

```
MIT License

Copyright (c) 2019 anwenhu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. 其他依赖

本项目在构建时使用的第三方组件，遵循其各自的许可：

| 组件 | 许可证 |
| :--- | :--- |
| AndroidX (wear / recyclerview / annotation) | Apache-2.0 |
| OkHttp (square) | Apache-2.0 |
| Android Gradle Plugin / Gradle | Apache-2.0 |

---

## 商标与免责

- 「智学网」为相关权利人的商标。本项目为**非官方**客户端，与智学网官方**无任何关联**，未获其授权或认可。
- 本项目仅调用智学网**公开网页接口**，用于个人学习、研究与自查。
- 请遵守智学网的用户协议，**不得**用于批量抓取、数据倒卖或骚扰他人。

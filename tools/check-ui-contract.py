#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UI 契约校验器 —— 改完布局后跑一下，确保没有把 Java 逻辑改崩。

用法:
    python tools/check-ui-contract.py [项目根目录]
默认项目根目录 = ./zhixue-wear

校验 4 件事:
  1. 每个布局必须包含它对应 Activity 需要的全部 view id
  2. Java 里引用的每个 R.id.* 必须在布局里有定义（否则 findViewById 返回 null -> NPE）
  3. 关键控件类型必须兼容（list=RecyclerView, web=WebView, et_*=EditText, tv_*=TextView）
  4. 布局里不能有重复 id

退出码: 0=通过, 1=有违规
"""
import io, os, re, sys, glob

# ---- 契约表：布局文件 -> (负责它的 Java 文件, 必须存在的 id) ----
CONTRACT = {
    "activity_main.xml": ("MainActivity.java", [
        "btn_exit", "tv_user", "btn_login", "btn_marks",
        "btn_exams", "btn_homework", "btn_messages", "btn_about"]),
    "activity_list.xml": ("ui/ListActivityBase.java", [
        "btn_back", "tv_title", "list", "tv_empty", "progress"]),
    "item_row.xml": ("ui/ListActivityBase.java", [
        "tv_row_title", "tv_row_sub"]),
    "activity_login_web.xml": ("LoginWebActivity.java", [
        "web", "tv_hint", "progress"]),
    "activity_cookie_login.xml": ("CookieLoginActivity.java", [
        "btn_back", "et_user", "et_pwd", "et_cookie",
        "btn_save_creds", "btn_save_cookie", "btn_clear"]),
    "activity_about.xml": ("AboutActivity.java", [
        "btn_back", "tv_about", "btn_logout"]),
}

# ---- 控件类型契约：id -> 允许的 View 类（子类也算） ----
TYPE_CONTRACT = {
    "list": "RecyclerView",
    "web": "WebView",
    "progress": None,      # 任意 View
    "et_user": "EditText",
    "et_pwd": "EditText",
    "et_cookie": "EditText",
    "tv_user": "TextView",
    "tv_title": "TextView",
    "tv_empty": "TextView",
    "tv_hint": "TextView",
    "tv_about": "TextView",
    "tv_row_title": "TextView",
    "tv_row_sub": "TextView",
}
VIEW_TAGS = ["Button", "TextView", "EditText", "ImageView", "LinearLayout", "FrameLayout",
             "ScrollView", "WebView", "ProgressBar", "RecyclerView", "BoxInsetLayout",
             "WearableRecyclerView", "View", "Space", "Switch", "CheckBox", "RadioButton"]

OK, BAD = 0, 1
issues = []

def err(msg):
    issues.append(msg)

def find_java(root, rel):
    hits = glob.glob(os.path.join(root, "app/src/main/java/**/" + os.path.basename(rel)), recursive=True)
    return hits[0] if hits else None

def declared_ids(xml_text):
    """返回 {id: 控件类名}"""
    out, seen = {}, []
    for m in re.finditer(r'<([A-Za-z0-9_.]+)([^>]*?)(/?)>', xml_text, re.DOTALL):
        tag, attrs = m.group(1), m.group(2)
        idm = re.search(r'android:id\s*=\s*"@\+?id/([A-Za-z_0-9]+)"', attrs)
        if idm:
            out[idm.group(1)] = tag.split(".")[-1]
            seen.append(idm.group(1))
    dup = {i for i in seen if seen.count(i) > 1}
    return out, dup

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "zhixue-wear"
    laydir = os.path.join(root, "app/src/main/res/layout")
    if not os.path.isdir(laydir):
        print(f"[FATAL] 找不到布局目录: {laydir}"); return BAD

    print(f"项目根目录: {os.path.abspath(root)}\n")

    # 1 + 3 + 4：逐布局校验
    all_defined = {}
    for name, (owner, required) in CONTRACT.items():
        path = os.path.join(laydir, name)
        if not os.path.exists(path):
            err(f"[缺失布局] {name}（{owner} 依赖它）"); continue
        text = io.open(path, encoding="utf-8").read()
        ids, dup = declared_ids(text)
        all_defined.update(ids)

        missing = [i for i in required if i not in ids]
        if missing:
            err(f"[缺 id] {name} 缺少 {missing} -> {owner} 的 findViewById 会返回 null，直接崩")
        else:
            print(f"  OK  {name:28s} 必需 {len(required)} 个 id 齐全")

        if dup:
            err(f"[重复 id] {name} 出现重复 id: {sorted(dup)}")

        for vid, want in TYPE_CONTRACT.items():
            if vid in ids and want and want not in ids[vid]:
                err(f"[类型不符] {name} 里 #{vid} 是 {ids[vid]}，Java 按 {want} 用 -> 可能 ClassCastException")

    # 2：Java 引用的 id 必须都存在
    jids = {}
    for f in glob.glob(os.path.join(root, "app/src/main/java/**/*.java"), recursive=True):
        for i in set(re.findall(r'R\.id\.([A-Za-z_0-9]+)', io.open(f, encoding="utf-8").read())):
            jids.setdefault(i, []).append(os.path.basename(f))
    for i, files in sorted(jids.items()):
        if i not in all_defined:
            err(f"[幻影 id] Java 引用了 R.id.{i}（{'/'.join(sorted(set(files)))}），但没有任何布局定义它")

    # 汇总
    print()
    if issues:
        print(f"× 校验未通过，共 {len(issues)} 项：\n")
        for x in issues:
            print("   - " + x)
        print("\n提示：改 UI 时最省事的做法是【只改视觉、不动 id】。")
        print("      确实要改 id 名，就同步改契约表里对应的 Java 文件。")
        return BAD
    print("√ 契约校验全部通过：布局可以被 Java 安全绑定。")
    return OK

if __name__ == "__main__":
    sys.exit(main())

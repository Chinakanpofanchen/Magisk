# Claude Code 命令参考指南

本文档介绍 Claude Code CLI 中可用的所有斜杠命令和功能。

## 📋 目录

- [斜杠命令](#斜杠命令)
- [核心功能](#核心功能)
- [Git 工作流](#git-工作流)
- [规划模式](#规划模式)
- [任务管理](#任务管理)
- [配置选项](#配置选项)

---

## 🎯 斜杠命令

斜杠命令是 Claude Code 中以 `/` 开头的快捷指令。

### `/help`
显示 Claude Code 的帮助信息和使用提示。

```
/help
```

### `/clear`
清除当前对话历史，开始新对话。

```
/clear
```

### `/commit`
创建 Git commit。Claude 会自动：
- 检查 `git status` 和 `git diff`
- 分析改动并生成 commit 消息
- 添加 Co-Authored-By 标签
- 执行 commit

```
/commit
```

**提示：** 也可以直接说 "创建 commit" 或 "commit these changes"。

### `/tasks`
查看所有后台运行的任务（agent、shell 等）及其状态。

```
/tasks
```

### `/remember`
记住项目相关的信息，供后续对话使用。

```
/remember 这个项目使用 Kotlin 编写
/remember 主要分支是 main
```

### `/case-feedback`
提交反馈报告，用于报告问题或建议。

```
/case-feedback
```

---

## 🔧 核心功能

### 文件操作

| 功能 | 说明 |
|------|------|
| 读取文件 | `读取文件路径` 或直接指定文件 |
| 编辑文件 | `修改文件内容` - Claude 会找到并编辑 |
| 创建文件 | `创建新文件...` |
| 搜索文件 | `查找所有 .kt 文件` |

### 代码搜索

| 功能 | 说明 |
|------|------|
| 搜索代码 | `搜索 "SuPolicy"` |
| 查找定义 | `跳转到定义` |
| 查找引用 | `查找所有引用` |

### Bash 命令

直接执行 shell 命令：

```
运行 ./build.sh
执行 git status
```

---

## 🌳 Git 工作流

### 创建 Commit

```
/commit
```

或直接说：
- "commit 这些改动"
- "创建一个 commit"
- "提交代码"

### 创建 Pull Request

```
创建 PR 到 main 分支
```

Claude 会自动：
- 检查分支状态
- 生成 PR 标题和描述
- 推送到远程
- 创建 PR

### 查看 Git 状态

```
git 状态
查看 diff
最近的 commits
```

---

## 📐 规划模式 (Plan Mode)

规划模式用于复杂任务的实现前规划。

### 进入规划模式

```
进入规划模式
/plan
```

### 规划模式的用途

- 新功能实现（需要多个文件修改）
- 重构代码结构
- 性能优化（需要分析多种方案）
- 架构决策

### 规划模式流程

1. Claude 探索代码库
2. 设计实现方案
3. 展示计划给用户审核
4. 用户批准后退出规划模式
5. 开始实际编码

### 不需要规划模式的场景

- 单行或少量修改
- 添加单个函数
- 明确指定细节的任务
- 纯研究/探索任务

---

## 📝 任务管理

Claude Code 可以创建和管理任务列表，用于跟踪复杂工作。

### 创建任务列表

当任务需要 3 步以上时，Claude 会自动创建任务列表。

```
任务示例：
- [ ] 修复类型推断错误
- [ ] 更新测试用例
- [ ] 更新文档
```

### 查看任务

```
查看任务列表
显示所有任务
```

### 更新任务状态

任务状态：`pending` → `in_progress` → `completed`

```
标记任务 1 为完成
更新任务状态
```

---

## ⚙️ 配置选项

### Hooks 配置

在 `~/.claude/hooks.json` 中配置钩子，在特定事件时自动执行命令。

**示例配置：**

```json
{
  "hooks": {
    "user_prompt_submit": {
      "command": "echo 'User submitted a prompt'",
      "background": false
    }
  }
}
```

### 可用钩子事件

| 事件 | 说明 |
|------|------|
| `user_prompt_submit` | 用户提交提示后 |
| `tool_use` | 工具调用前后 |

---

## 🎨 最佳实践

### 1. 清晰的指令

✅ 好的指令：
```
修改 SuperuserViewModel.kt 中的第 114 行，
将 ArrayList() 改为 ArrayList<PolicyRvItem>()
```

❌ 不好的指令：
```
修复那个文件
```

### 2. 利用上下文

Claude 会记住对话历史，可以引用之前的内容：

```
用刚才的方法修复其他文件
像之前那样处理这个错误
```

### 3. 何时使用规划模式

✅ 使用规划模式：
- 添加用户认证功能
- 实现缓存层
- 重构大型模块

❌ 不使用规划模式：
- 修复拼写错误
- 添加单个函数
- 查看某个文件

### 4. Git 工作流

```
1. 完成代码修改
2. 让 Claude 创建 commit: /commit
3. 推送到远程: git push
4. 创建 PR（如需要）: 创建 PR
```

---

## 🔍 高级用法

### LSP 功能

需要配置 LSP 服务器后可用：

| 功能 | 命令 |
|------|------|
| 跳转定义 | 跳转到 `SuPolicy` 的定义 |
| 查找引用 | 查找 `PolicyRvItem` 的所有引用 |
| 悬停信息 | 显示这个变量的类型 |

### Web 搜索

```
搜索 Kotlin 类型推断
查找最新的 Gradle 文档
```

### MCP 服务器

Claude Code 支持 Model Context Protocol (MCP) 服务器，可以扩展功能。

---

## 📞 获取帮助

- 在对话中输入 `/help`
- 访问 https://github.com/anthropics/claude-code/issues 报告问题

---

**最后更新：** 2025-02-03
**Claude Code 版本：** Sonnet 4.5

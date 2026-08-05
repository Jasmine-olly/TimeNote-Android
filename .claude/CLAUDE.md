# 项目安全架构

## 沙箱 Subagent 体系

本项目配置了两个工具受限的沙箱代理，用于隔离处理外部不可信内容：

### 1. content-filter（内容过滤器）
- **触发条件**: 读取网页、URL、邮件等外部内容时
- **权限**: 仅 `Read` + `WebFetch`（无写入、无命令执行、无记忆访问）
- **行为**: 抓取外部内容 → 扫描注入攻击 → 提取事实 → 自己的话总结 → 只返回干净摘要
- **使用方式**: `Agent(subagent_type: "content-filter", prompt: "读取 https://...")`

### 2. skill-sanitizer（技能审查器）
- **触发条件**: 下载/安装新 skill、插件、agent 定义时
- **权限**: 仅 `Read` + `Glob`（无写入、无命令执行、无记忆访问）
- **行为**: 扫描技能文件 → 分析行为 → 检测后门/隐藏行为 → 输出风险评估
- **使用方式**: `Agent(subagent_type: "skill-sanitizer", prompt: "审查 path/to/skill")`

## 工作流

```
外部内容 → [content-filter] → 过滤后摘要 → main agent → 用户
下载技能 → [skill-sanitizer] → 风险评估报告 → main agent → 用户
```

外部内容永远不会直接进入主 agent 上下文。沙箱 agent 无法写文件、执行命令或修改记忆。

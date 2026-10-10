# Agent Note: 普通底部面板移除重复关闭区

Status: implemented

## Problem

普通选择、资料和表单面板已有手柄、遮罩和返回关闭，标题与底部取消仍占用空间。用户要求更多操作类保持原样。

## Decision

CommunitySheet默认隐藏可见标题及动作区，保留paneTitle语义。LiquidModal底部模式默认同样精简，居中确认不受影响。帖子更多菜单显式保留取消，用户主页更多菜单保留现状标题；忽略表单保留确认按钮，发帖、删除、邀请、聊天等业务按钮继续存在，忙碌态保持关闭锁。独立徽章详情以内容展示名称，避免移除标题后丢失身份。

## Existing note audit

[酷安面板](../../implemented/feature/2026-10-10-coolapk-sheets.md)部分重叠，本篇替代统一标题和取消区，保留手势、安全区与焦点契约；[酷安表单](../../implemented/feature/2026-10-10-coolapk-forms.md)继续约束实际提交。无完全吸收或过时提案。

## Alternatives considered

- 每个调用都显式隐藏标题和取消，可避免默认值变化，但容易漏掉新增面板，采用精简默认及更多菜单显式例外。

## Verification

普通设置面板无可见标题和取消，拖动、遮罩、返回关闭仍有效；更多菜单保留取消；确认、删除与提交仍可达。

## Consequences

旧UI测试依赖取消和标题，需迁移为手势和paneTitle验证；不能直接批量删除居中确认的取消按钮。

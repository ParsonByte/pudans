# 旧项目前端 → pudans web 迁移清单（inventory）

> 生成时间：2026-11-16T03:00:30.269Z · 生成方式：`.dsh/migration/generate-inventory.mjs`（状态由文件系统推导，不手写完成状态）

| 项 | 值 |
| --- | --- |
| 旧项目（源） | `/Users/byte/Global/Reference/cloudreve/frontend` |
| 目标仓库 | `/Users/byte/Global/Project/pudans`（当前会话工作目录；`web/packages/components` 所在仓库） |
| 源文件总数（不含 node_modules） | 1290 |
| 源 src/ 文件 | 816 |
| 源 public/ 文件 | 461 |
| 源根配置 | 13 |
| 目标组件库组件目录 | 55 |
| 目标布局目录 | 4 |
| 目标 hooks | 0 |
| 目标 shared utils | 12 |

## 摘要（覆盖率与处理方式）

| 指标 | 数量 | 说明 |
| --- | --- | --- |
| 清单条目（源文件） | 1290 | 每个源文件恰好一个条目 |
| 已迁移/已封装（writes） | 309 | 目标文件已存在，含 251 图标集合 |
| 已复用（reuse） | 68 | 直接使用目标库既有能力 |
| 已保留应用层 | 7 | 架构层组合，不抽公共库 |
| 已登记（暂缓） | 906 | 明确原因与迁移计划，无静默遗漏 |
| 规划中/待建 | 0 | 已定目标路径，待本批完成后自动转为已完成 |
| 覆盖率 | 100.00% | 源文件全部登记 |
| src/ 完成率 | 45.8% | 迁移+封装+复用+保留 / src 文件数 |

### 处理方式分布

| 处理方式 | 数量 | 含义 |
| --- | --- | --- |
| 迁移 | 292 | 整体迁移到公共组件库/共享包 |
| 封装 | 17 | 抽取/合并为统一公共组件（多对一） |
| 复用 | 68 | 目标库已有等价能力，直接复用 |
| 保留应用层 | 7 | 属于应用架构组合，不进入公共库但已在应用层落地 |
| 暂缓 | 906 | 登记保留，附阻断原因与迁移计划 |

## 一、源项目 src/ 全量清单

> 目标路径相对 `/Users/byte/Global/Project/pudans/`。状态：已完成 / 已复用 / 已保留 / 规划中 / 已登记（暂缓）。

### component/Icons（251）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Icons/Add.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Add`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 2 | `src/component/Icons/AppFolder.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:AppFolder`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 3 | `src/component/Icons/AppGeneric.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:AppGeneric`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 4 | `src/component/Icons/AppsList.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:AppsList`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 5 | `src/component/Icons/AppsListOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:AppsListOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 6 | `src/component/Icons/Archive.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Archive`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 7 | `src/component/Icons/ArchiveArrow.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArchiveArrow`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 8 | `src/component/Icons/ArrowClockwise.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowClockwise`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 9 | `src/component/Icons/ArrowClockwiseFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowClockwiseFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 10 | `src/component/Icons/ArrowDown.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowDown`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 11 | `src/component/Icons/ArrowHookDownLeft.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowHookDownLeft`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 12 | `src/component/Icons/ArrowHookUpRight.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowHookUpRight`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 13 | `src/component/Icons/ArrowImport.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowImport`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 14 | `src/component/Icons/ArrowLeft.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowLeft`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 15 | `src/component/Icons/ArrowRepeatAll.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowRepeatAll`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 16 | `src/component/Icons/ArrowRepeatOff.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowRepeatOff`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 17 | `src/component/Icons/ArrowRepeatOne.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowRepeatOne`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 18 | `src/component/Icons/ArrowShuffle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowShuffle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 19 | `src/component/Icons/ArrowSort.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowSort`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 20 | `src/component/Icons/ArrowSortDownFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowSortDownFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 21 | `src/component/Icons/ArrowSync.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowSync`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 22 | `src/component/Icons/ArrowSyncCircleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ArrowSyncCircleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 23 | `src/component/Icons/BinFullOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BinFullOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 24 | `src/component/Icons/Book.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Book`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 25 | `src/component/Icons/BookInformation.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BookInformation`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 26 | `src/component/Icons/Border.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Border`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 27 | `src/component/Icons/BorderAll.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BorderAll`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 28 | `src/component/Icons/BorderInside.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BorderInside`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 29 | `src/component/Icons/Bot.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Bot`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 30 | `src/component/Icons/BoxMultiple.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BoxMultiple`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 31 | `src/component/Icons/BoxMultipleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BoxMultipleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 32 | `src/component/Icons/BranchCompare.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BranchCompare`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 33 | `src/component/Icons/BranchForkLink.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BranchForkLink`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 34 | `src/component/Icons/Broom.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Broom`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 35 | `src/component/Icons/BuildingShop.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BuildingShop`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 36 | `src/component/Icons/BuildingShopFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:BuildingShopFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 37 | `src/component/Icons/CalendarClock.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CalendarClock`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 38 | `src/component/Icons/CameraFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CameraFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 39 | `src/component/Icons/CameraRounded.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CameraRounded`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 40 | `src/component/Icons/CaretDown.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CaretDown`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 41 | `src/component/Icons/CaretRight.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CaretRight`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 42 | `src/component/Icons/Cart.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Cart`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 43 | `src/component/Icons/CheckCircleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CheckCircleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 44 | `src/component/Icons/CheckUnchecked.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CheckUnchecked`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 45 | `src/component/Icons/CheckboxChecked.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CheckboxChecked`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 46 | `src/component/Icons/Checkmark.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Checkmark`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 47 | `src/component/Icons/CheckmarkCircle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CheckmarkCircle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 48 | `src/component/Icons/CheckmarkCircleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CheckmarkCircleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 49 | `src/component/Icons/ChevronRight.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ChevronRight`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 50 | `src/component/Icons/CircleHintFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CircleHintFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 51 | `src/component/Icons/Clipboard.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Clipboard`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 52 | `src/component/Icons/ClockArrowDownload.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ClockArrowDownload`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 53 | `src/component/Icons/ClockFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ClockFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 54 | `src/component/Icons/CloudArrowIUp.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CloudArrowIUp`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 55 | `src/component/Icons/CloudDownload.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CloudDownload`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 56 | `src/component/Icons/CloudDownloadOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CloudDownloadOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 57 | `src/component/Icons/CloudFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CloudFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 58 | `src/component/Icons/CloudOff.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CloudOff`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 59 | `src/component/Icons/CoinStack.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CoinStack`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 60 | `src/component/Icons/Color.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Color`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 61 | `src/component/Icons/CommentMultiple.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CommentMultiple`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 62 | `src/component/Icons/CopperCoin.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CopperCoin`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 63 | `src/component/Icons/CopyOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CopyOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 64 | `src/component/Icons/Copyright.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Copyright`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 65 | `src/component/Icons/CubeSync.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CubeSync`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 66 | `src/component/Icons/CubeSyncFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CubeSyncFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 67 | `src/component/Icons/CubeTree.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CubeTree`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 68 | `src/component/Icons/CubeTreeFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:CubeTreeFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 69 | `src/component/Icons/Currency.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Currency`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 70 | `src/component/Icons/DarkTheme.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DarkTheme`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 71 | `src/component/Icons/DataBarVerticalStar.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DataBarVerticalStar`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 72 | `src/component/Icons/DataHistogram.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DataHistogram`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 73 | `src/component/Icons/DataHistogramFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DataHistogramFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 74 | `src/component/Icons/Delete.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Delete`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 75 | `src/component/Icons/DeleteOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DeleteOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 76 | `src/component/Icons/DesktopFlow.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DesktopFlow`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 77 | `src/component/Icons/Discord.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Discord`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 78 | `src/component/Icons/Dismiss.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Dismiss`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 79 | `src/component/Icons/DismissCircleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DismissCircleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 80 | `src/component/Icons/Divider.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Divider`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 81 | `src/component/Icons/Document.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Document`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 82 | `src/component/Icons/DocumentArrowDownFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentArrowDownFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 83 | `src/component/Icons/DocumentCopyFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentCopyFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 84 | `src/component/Icons/DocumentDataLink.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentDataLink`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 85 | `src/component/Icons/DocumentFlowchart.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentFlowchart`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 86 | `src/component/Icons/DocumentPDF.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentPDF`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 87 | `src/component/Icons/DocumentText.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentText`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 88 | `src/component/Icons/DocumentTextOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:DocumentTextOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 89 | `src/component/Icons/Download.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Download`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 90 | `src/component/Icons/Earth.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Earth`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 91 | `src/component/Icons/Edit.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Edit`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 92 | `src/component/Icons/EditSetting.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:EditSetting`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 93 | `src/component/Icons/EmailClock.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:EmailClock`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 94 | `src/component/Icons/EmojiEdit.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:EmojiEdit`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 95 | `src/component/Icons/Enter.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Enter`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 96 | `src/component/Icons/Eye.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Eye`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 97 | `src/component/Icons/EyeOff.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:EyeOff`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 98 | `src/component/Icons/FastForward.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FastForward`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 99 | `src/component/Icons/FileAdd.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FileAdd`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 100 | `src/component/Icons/FileExclBox.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FileExclBox`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 101 | `src/component/Icons/FilePowerPointBox.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FilePowerPointBox`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 102 | `src/component/Icons/FileWordBox.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FileWordBox`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 103 | `src/component/Icons/FilmstripImage.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FilmstripImage`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 104 | `src/component/Icons/Filter.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Filter`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 105 | `src/component/Icons/Fingerprint.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Fingerprint`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 106 | `src/component/Icons/Folder.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Folder`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 107 | `src/component/Icons/FolderAdd.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderAdd`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 108 | `src/component/Icons/FolderArrowRightOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderArrowRightOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 109 | `src/component/Icons/FolderArrowUp.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderArrowUp`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 110 | `src/component/Icons/FolderLink.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderLink`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 111 | `src/component/Icons/FolderOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 112 | `src/component/Icons/FolderZip.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FolderZip`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 113 | `src/component/Icons/FullScreenMaximize.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FullScreenMaximize`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 114 | `src/component/Icons/FullScreenMinimize.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:FullScreenMinimize`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 115 | `src/component/Icons/Gift.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Gift`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 116 | `src/component/Icons/Globe.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Globe`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 117 | `src/component/Icons/GlobeFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:GlobeFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 118 | `src/component/Icons/Grid.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Grid`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 119 | `src/component/Icons/GridOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:GridOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 120 | `src/component/Icons/HardDrive.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:HardDrive`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 121 | `src/component/Icons/HardDriveOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:HardDriveOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 122 | `src/component/Icons/HistoryOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:HistoryOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 123 | `src/component/Icons/Home.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Home`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 124 | `src/component/Icons/HomeOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:HomeOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 125 | `src/component/Icons/Icons.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Icons`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 126 | `src/component/Icons/Image.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Image`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 127 | `src/component/Icons/ImageAarowCounterclockwise.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageAarowCounterclockwise`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 128 | `src/component/Icons/ImageCopy.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageCopy`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 129 | `src/component/Icons/ImageCopyOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageCopyOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 130 | `src/component/Icons/ImageEdit.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageEdit`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 131 | `src/component/Icons/ImageOffOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageOffOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 132 | `src/component/Icons/ImageOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ImageOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 133 | `src/component/Icons/InPrivate.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:InPrivate`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 134 | `src/component/Icons/Info.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Info`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 135 | `src/component/Icons/InfoFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:InfoFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 136 | `src/component/Icons/IosArrow.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:IosArrow`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 137 | `src/component/Icons/IosArrowLeft.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:IosArrowLeft`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 138 | `src/component/Icons/LanguageC.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguageC`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 139 | `src/component/Icons/LanguageCPP.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguageCPP`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 140 | `src/component/Icons/LanguageGo.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguageGo`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 141 | `src/component/Icons/LanguageJS.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguageJS`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 142 | `src/component/Icons/LanguagePHP.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguagePHP`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 143 | `src/component/Icons/LanguagePython.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguagePython`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 144 | `src/component/Icons/LanguageRust.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LanguageRust`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 145 | `src/component/Icons/Link.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Link`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 146 | `src/component/Icons/LinkDismiss.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LinkDismiss`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 147 | `src/component/Icons/LinkEdit.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LinkEdit`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 148 | `src/component/Icons/LinkOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LinkOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 149 | `src/component/Icons/LinkSetting.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LinkSetting`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 150 | `src/component/Icons/LocationFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LocationFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 151 | `src/component/Icons/LockClosed.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LockClosed`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 152 | `src/component/Icons/LockClosedKey.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LockClosedKey`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 153 | `src/component/Icons/LockClosedOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:LockClosedOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 154 | `src/component/Icons/MagnetOn.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MagnetOn`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 155 | `src/component/Icons/MailOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MailOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 156 | `src/component/Icons/Markdown.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Markdown`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 157 | `src/component/Icons/Money.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Money`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 158 | `src/component/Icons/Moon.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Moon`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 159 | `src/component/Icons/MoreHorizontal.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MoreHorizontal`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 160 | `src/component/Icons/MoreVertical.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MoreVertical`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 161 | `src/component/Icons/MusicNote1.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MusicNote1`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 162 | `src/component/Icons/MusicNote1Outlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MusicNote1Outlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 163 | `src/component/Icons/MusicNote2.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MusicNote2`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 164 | `src/component/Icons/MusicNote2Play.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:MusicNote2Play`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 165 | `src/component/Icons/Notepad.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Notepad`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 166 | `src/component/Icons/Numbers.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Numbers`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 167 | `src/component/Icons/Open.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Open`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 168 | `src/component/Icons/OpenFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:OpenFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 169 | `src/component/Icons/Options.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Options`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 170 | `src/component/Icons/PackageOpen.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PackageOpen`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 171 | `src/component/Icons/Password.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Password`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 172 | `src/component/Icons/Pause.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Pause`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 173 | `src/component/Icons/Payment.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Payment`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 174 | `src/component/Icons/PaymentFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PaymentFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 175 | `src/component/Icons/People.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:People`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 176 | `src/component/Icons/PeopleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PeopleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 177 | `src/component/Icons/PeopleStar.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PeopleStar`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 178 | `src/component/Icons/PeopleTeam.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PeopleTeam`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 179 | `src/component/Icons/PeopleTeamOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PeopleTeamOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 180 | `src/component/Icons/Person.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Person`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 181 | `src/component/Icons/PersonLock.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PersonLock`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 182 | `src/component/Icons/PersonOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PersonOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 183 | `src/component/Icons/PersonPasskey.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PersonPasskey`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 184 | `src/component/Icons/PersonStar.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PersonStar`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 185 | `src/component/Icons/PhoneLaptop.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PhoneLaptop`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 186 | `src/component/Icons/PhoneLaptopOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PhoneLaptopOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 187 | `src/component/Icons/PinOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:PinOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 188 | `src/component/Icons/Play.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Play`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 189 | `src/component/Icons/Pulse.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Pulse`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 190 | `src/component/Icons/QQ.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:QQ`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 191 | `src/component/Icons/QuestionCircle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:QuestionCircle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 192 | `src/component/Icons/Raw.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Raw`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 193 | `src/component/Icons/RectangleLandscapeSync.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:RectangleLandscapeSync`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 194 | `src/component/Icons/RectangleLandscapeSyncOff.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:RectangleLandscapeSyncOff`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 195 | `src/component/Icons/RenameFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:RenameFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 196 | `src/component/Icons/RenameOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:RenameOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 197 | `src/component/Icons/Save.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Save`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 198 | `src/component/Icons/Savings.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Savings`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 199 | `src/component/Icons/Search.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Search`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 200 | `src/component/Icons/SendLogging.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SendLogging`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 201 | `src/component/Icons/SendLoggingFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SendLoggingFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 202 | `src/component/Icons/Server.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Server`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 203 | `src/component/Icons/ServerFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ServerFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 204 | `src/component/Icons/Setting.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Setting`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 205 | `src/component/Icons/SettingsOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SettingsOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 206 | `src/component/Icons/Share.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Share`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 207 | `src/component/Icons/ShareAndroid.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShareAndroid`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 208 | `src/component/Icons/ShareFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShareFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 209 | `src/component/Icons/ShareOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShareOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 210 | `src/component/Icons/Shield.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Shield`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 211 | `src/component/Icons/ShieldAdd.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShieldAdd`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 212 | `src/component/Icons/ShieldDismiss.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShieldDismiss`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 213 | `src/component/Icons/ShieldFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShieldFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 214 | `src/component/Icons/ShieldLock.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShieldLock`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 215 | `src/component/Icons/ShieldLockFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:ShieldLockFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 216 | `src/component/Icons/SignOut.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SignOut`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 217 | `src/component/Icons/SlideText.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SlideText`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 218 | `src/component/Icons/Sparkle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Sparkle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 219 | `src/component/Icons/SparkleFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SparkleFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 220 | `src/component/Icons/StarFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:StarFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 221 | `src/component/Icons/Storage.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Storage`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 222 | `src/component/Icons/StorageOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:StorageOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 223 | `src/component/Icons/Subtitles.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Subtitles`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 224 | `src/component/Icons/SunWithTime.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:SunWithTime`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 225 | `src/component/Icons/Sunny.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Sunny`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 226 | `src/component/Icons/TableSettings.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TableSettings`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 227 | `src/component/Icons/Tag.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Tag`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 228 | `src/component/Icons/Tags.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Tags`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 229 | `src/component/Icons/TaskList.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TaskList`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 230 | `src/component/Icons/TaskListOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TaskListOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 231 | `src/component/Icons/TaskListRegular.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TaskListRegular`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 232 | `src/component/Icons/Telegram.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Telegram`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 233 | `src/component/Icons/TextBulletListSquareEdiFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextBulletListSquareEdiFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 234 | `src/component/Icons/TextBulletListSquareEdit.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextBulletListSquareEdit`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 235 | `src/component/Icons/TextCaseTitle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextCaseTitle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 236 | `src/component/Icons/TextEditStyle.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextEditStyle`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 237 | `src/component/Icons/TextGrammarLighting.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextGrammarLighting`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 238 | `src/component/Icons/TextIndentIncrease.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TextIndentIncrease`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 239 | `src/component/Icons/TicketDiagonal.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:TicketDiagonal`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 240 | `src/component/Icons/Timer.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Timer`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 241 | `src/component/Icons/Translate.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Translate`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 242 | `src/component/Icons/Upload.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Upload`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 243 | `src/component/Icons/UploadFilled.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:UploadFilled`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 244 | `src/component/Icons/Video.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Video`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 245 | `src/component/Icons/VideoOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:VideoOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 246 | `src/component/Icons/WalletCreditCard.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:WalletCreditCard`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 247 | `src/component/Icons/Warning.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Warning`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 248 | `src/component/Icons/WarningOutlined.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:WarningOutlined`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 249 | `src/component/Icons/Whiteboard.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:Whiteboard`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 250 | `src/component/Icons/WindowApps.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:WindowApps`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |
| 251 | `src/component/Icons/WrenchSettings.tsx` | `web/packages/components/src/components/icon/cloudreve-icons.ts -> `cr:WrenchSettings`` | 迁移 | 已完成 | SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。 |

### component/Common（29）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Common/AutoHeight.tsx` | `web/packages/components/src/components/animate-height/animate-height.tsx` | 复用 | 已复用 | 目标库已有 AnimateHeight，旧 AutoHeight 包装逻辑由其受控/非受控高度动画覆盖。 |
| 2 | `src/component/Common/BorderLinearProgress.tsx` | `web/packages/components/src/components/progress/progress.tsx` | 迁移 | 已完成 | 合并为统一 Progress 组件（linear/circular），圆角与色带改用轨道 token。 |
| 3 | `src/component/Common/Captcha/CapCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。 |
| 4 | `src/component/Common/Captcha/Captcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。 |
| 5 | `src/component/Common/Captcha/DefaultCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。 |
| 6 | `src/component/Common/Captcha/ReCaptchaV2.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。 |
| 7 | `src/component/Common/Captcha/TurnstileCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。 |
| 8 | `src/component/Common/CircularProgress.tsx` | `web/packages/components/src/components/progress/progress.tsx` | 迁移 | 已完成 | 双层环形进度合并为 Progress variant=circular 的 determinate 模式。 |
| 9 | `src/component/Common/Code.tsx` | `web/packages/components/src/components/code/code.tsx` | 迁移 | 已完成 | 内联 Code 与 CodeBlock 合并到 code 组件，等宽字体与底色改 token。 |
| 10 | `src/component/Common/ErrorBoundary.tsx` | `web/packages/components/src/components/error-state/error-state.tsx` | 迁移 | 已完成 | 路由错误边界迁移为 ErrorBoundary + RouteError，错误态复用 EmptyState。 |
| 11 | `src/component/Common/FadeTransition.css` | `web/packages/components/src/components/motion/motion.tsx` | 封装 | 已完成 | FadeTransition 的时长/曲线并入 motion token 与 Fade/Slide 过渡组件。 |
| 12 | `src/component/Common/Form/EncodingSelector.tsx` | — | 暂缓 | 已登记（暂缓） | 文件下载编码选择依赖 cloudreve 下载流程，目标仓库暂无文件下载产品面；登记待文件应用迁移。 |
| 13 | `src/component/Common/Form/FileDisplayForm.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件展示表单强依赖 explorer 数据模型，待文件应用立项后迁移。 |
| 14 | `src/component/Common/Form/OutlineIconTextField.tsx` | `web/packages/components/src/components/input/input.tsx` | 复用 | 已复用 | 目标 Input 已内置 startIcon/endIcon 与 label，不再需要独立包装。 |
| 15 | `src/component/Common/Form/PathSelectorForm.tsx` | — | 暂缓 | 已登记（暂缓） | 路径选择依赖 cloudreve URI 与文件树数据，待文件应用迁移。 |
| 16 | `src/component/Common/LanguageSwitcher.tsx` | `web/packages/components/src/components/language-switcher/language-switcher.tsx` | 迁移 | 已完成 | 迁移为受控 LanguageSwitcher（languages/current/onChange 注入，不绑定 i18n 单例）。 |
| 17 | `src/component/Common/Logo.tsx` | `web/packages/components/src/components/logo/logo.tsx` | 迁移 | 已完成 | 迁移为支持亮/暗源、加载骨架与回退字标的通用 Logo；cloudreve 品牌素材不复制。 |
| 18 | `src/component/Common/Nothing.tsx` | `web/packages/components/src/components/empty-state/empty-state.tsx` | 迁移 | 已完成 | 迁移为 EmptyState（empty/not-found/error/permission 语义变体）。 |
| 19 | `src/component/Common/ResponsiveTabs.tsx` | `web/packages/components/src/components/responsive-tabs/responsive-tabs.tsx` | 迁移 | 已完成 | 迁移为 ResponsiveTabs：溢出折叠为下拉菜单，复用 Tabs 视觉 token。 |
| 20 | `src/component/Common/SizeInput.tsx` | `web/packages/components/src/components/size-input/size-input.tsx` | 迁移 | 已完成 | 迁移为 SizeInput：字节值 + 单位（B/KB/MB/GB/TB）换算，去掉 redux 依赖。 |
| 21 | `src/component/Common/Snackbar/FileIconSnackbar.tsx` | `web/packages/components/src/components/toast/toast.tsx` | 封装 | 已完成 | notistack 自定义内容合并进 Toast 的 icon 变体。 |
| 22 | `src/component/Common/Snackbar/LoadingSnackbar.tsx` | `web/packages/components/src/components/toast/toast.tsx` | 复用 | 已复用 | Loading snackbar 由 Toast loading 变体覆盖。 |
| 23 | `src/component/Common/Snackbar/snackbar.tsx` | `web/packages/components/src/components/toast/toast.tsx` | 复用 | 已复用 | Service worker 更新提示改用 useToast().info + action。 |
| 24 | `src/component/Common/StyledComponents.tsx` | `web/packages/components/src/components/form-field/form-field.tsx` | 封装 | 已完成 | DefaultButton/DenseFilledTextField/NoWrapTypography 等样式配方拆入 Button/Input/form-field 与 token。 |
| 25 | `src/component/Common/StyledFormControl.tsx` | `web/packages/components/src/components/form-field/form-field.tsx` | 迁移 | 已完成 | 迁移为 FormField（标题+控件+校验文案）与 FormSection。 |
| 26 | `src/component/Common/TimeBadge.tsx` | `web/packages/components/src/components/relative-time/relative-time.tsx` | 迁移 | 已完成 | 迁移为 RelativeTime：Intl.RelativeTimeFormat + 完整时间 Tooltip，去掉 timeago-react。 |
| 27 | `src/component/Common/User/UserAvatar.tsx` | `web/packages/components/src/components/avatar/avatar.tsx` | 复用 | 已复用 | Avatar 组件已覆盖图片/首字母/图标回退。 |
| 28 | `src/component/Common/User/UserBadge.tsx` | `web/packages/components/src/components/user/user-badge.tsx` | 迁移 | 已完成 | 迁移为 UserBadge：头像+名称+副标题+状态点，供 uc/console 复用。 |
| 29 | `src/component/Common/User/UserPopover.tsx` | `web/packages/components/src/components/user/user-popover.tsx` | 迁移 | 已完成 | 迁移为 UserPopover：用户摘要卡片与动作插槽，去掉 redux/session 依赖。 |

### component/Frame（22）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Frame/FrameManagerBundle.tsx` | `web/app/console/src/layouts` | 保留应用层 | 已保留 | Frame 组合由各应用自己的 layout 承担，不进入公共库。 |
| 2 | `src/component/Frame/HeadlessFrame.tsx` | — | 暂缓 | 已登记（暂缓） | HeadlessFrame 为云盘文件管理器事件编排层，待文件应用迁移。 |
| 3 | `src/component/Frame/NavBar/AppDrawer.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。 |
| 4 | `src/component/Frame/NavBar/AppMain.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。 |
| 5 | `src/component/Frame/NavBar/DarkThemeSwitcher.tsx` | `web/packages/components/src/components/theme-switch/theme-switch.tsx` | 迁移 | 已完成 | 迁移为 ThemeSwitch（light/system/dark 分段控件），直接消费 usePudansTheme。 |
| 6 | `src/component/Frame/NavBar/DrawerHeader.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。 |
| 7 | `src/component/Frame/NavBar/FileSelectedActions.tsx` | — | 暂缓 | 已登记（暂缓） | 文件多选操作条依赖文件管理器选中态，待文件应用迁移。 |
| 8 | `src/component/Frame/NavBar/NavBarMainActions.tsx` | — | 暂缓 | 已登记（暂缓） | 导航主操作与 cloudreve 上传/新建流程耦合，登记业务层待迁移。 |
| 9 | `src/component/Frame/NavBar/NavIconTransition.tsx` | `web/packages/components/src/components/motion/motion.tsx` | 封装 | 已完成 | 图标切换动效抽为 AnimatedSwap（TransitionGroup + Fade，token 化时长）。 |
| 10 | `src/component/Frame/NavBar/PageNavigation.tsx` | — | 暂缓 | 已登记（暂缓） | 页面导航条绑定 cloudreve 路由与文件路径，登记待迁移。 |
| 11 | `src/component/Frame/NavBar/SearchBar.tsx` | `web/packages/components/src/components/search-input/search-input.tsx` | 迁移 | 已完成 | 迁移为 SearchInput：快捷键提示、loading、清空，去掉 redux 全局搜索依赖。 |
| 12 | `src/component/Frame/NavBar/SideNavItem.tsx` | `web/packages/components/src/layouts/app-shell/side-nav-item.tsx` | 复用 | 已复用 | 目标库已有 SideNavItem，支持折叠/激活/图标过渡，直接复用。 |
| 13 | `src/component/Frame/NavBar/SplitHandle.tsx` | `web/packages/components/src/components/split-pane/split-pane.tsx` | 迁移 | 已完成 | 迁移为 SplitPane 可拖拽分隔条（pointer 事件，支持持久化 key）。 |
| 14 | `src/component/Frame/NavBar/StorageSummary.tsx` | — | 暂缓 | 已登记（暂缓） | 存储配额摘要依赖 cloudreve 容量 API，目标暂无文件产品面。 |
| 15 | `src/component/Frame/NavBar/TopAppBar.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。 |
| 16 | `src/component/Frame/NavBar/UserAction.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。 |
| 17 | `src/component/Frame/NavBarFrame.tsx` | `web/packages/components/src/layouts/app-shell/app-shell.tsx` | 复用 | 已复用 | 由 AppShell 统一承载导航框架。 |
| 18 | `src/component/Frame/OauthAppCard.tsx` | `web/packages/components/src/components/oauth-app-card/oauth-app-card.tsx` | 迁移 | 已完成 | 迁移为 OAuthAppCard（图标/名称/描述/加载骨架），供授权页与集成页复用。 |
| 19 | `src/component/Frame/PoweredBy.tsx` | `web/packages/components/src/components/powered-by/powered-by.tsx` | 迁移 | 已完成 | 迁移为通用 PoweredBy（品牌名/链接/logo 由 props 注入）。 |
| 20 | `src/component/Frame/RadiusFrame.tsx` | `web/packages/components/src/layouts/page-container/page-container.tsx` | 复用 | 已复用 | PageContainer 已实现圆角内容框架，直接复用。 |
| 21 | `src/component/Frame/assets/logo.svg` | — | 暂缓 | 已登记（暂缓） | cloudreve 品牌 logo 不迁移到 pudans；Logo 组件改为可注入源，品牌资产由应用层提供。 |
| 22 | `src/component/Frame/assets/logo_light.svg` | — | 暂缓 | 已登记（暂缓） | cloudreve 品牌 logo 不迁移到 pudans；Logo 组件改为可注入源，品牌资产由应用层提供。 |

### component/Dialogs（7）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Dialogs/AggregatedErrorDetail.tsx` | `web/packages/components/src/components/error-detail-dialog/error-detail-dialog.tsx` | 迁移 | 已完成 | 迁移为 ErrorDetailDialog：错误消息+聚合明细表+请求 ID，去掉 redux dialog 池。 |
| 2 | `src/component/Dialogs/BatchDownloadLog.tsx` | — | 暂缓 | 已登记（暂缓） | 批量下载日志依赖下载任务模型，目标暂无下载产品面。 |
| 3 | `src/component/Dialogs/Confirmation.tsx` | `web/packages/components/src/components/confirm-dialog/confirm-dialog.tsx` | 迁移 | 已完成 | 迁移为 ConfirmProvider + useConfirm() promise 确认框，移除全局 redux dialog。 |
| 4 | `src/component/Dialogs/DialogAccordion.tsx` | `web/packages/components/src/components/accordion/accordion.tsx` | 迁移 | 已完成 | 迁移为 Accordion（卡片/幽灵两种变体），过渡使用 motion token。 |
| 5 | `src/component/Dialogs/DraggableDialog.tsx` | `web/packages/components/src/components/dialog/dialog.tsx` | 迁移 | 已完成 | 对话框增加 draggable 能力（标题栏拖拽、触摸设备自动禁用），其余能力复用 Dialog。 |
| 6 | `src/component/Dialogs/GlobalDialogs.tsx` | `web/src/providers` | 保留应用层 | 已保留 | 全局对话框挂载点由应用 Provider 组合，不放进公共库；确认框/选择框改为 provider+hook API。 |
| 7 | `src/component/Dialogs/SelectOption.tsx` | `web/packages/components/src/components/select-dialog/select-dialog.tsx` | 迁移 | 已完成 | 迁移为 SelectDialog + useSelectOption()（选项列表/描述/回调 promise）。 |

### component/Pages（57）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Pages/Devices/AppPromotion.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 2 | `src/component/Pages/Devices/ConnectionInfoDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 3 | `src/component/Pages/Devices/CreateDAVAccountDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 4 | `src/component/Pages/Devices/DavAccountList.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 5 | `src/component/Pages/Devices/DavAccountRow.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 6 | `src/component/Pages/Devices/DesktopAppPromotion.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 7 | `src/component/Pages/Devices/Devices.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。 |
| 8 | `src/component/Pages/HomeRedirect.tsx` | — | 暂缓 | 已登记（暂缓） | 首页重定向逻辑由目标应用 router 自带重定向覆盖，无需迁移。 |
| 9 | `src/component/Pages/Login/Activate.tsx` | — | 暂缓 | 已登记（暂缓） | 邮件激活流程目标 auth 应用尚未提供页面，登记待后端激活接口对齐。 |
| 10 | `src/component/Pages/Login/Authorize.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 授权确认页待目标 OAuth provider 接入后迁移。 |
| 11 | `src/component/Pages/Login/Phases/Phase2FA.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 12 | `src/component/Pages/Login/Phases/PhaseCollectEmail.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 13 | `src/component/Pages/Login/Phases/PhaseCollectPassword.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 14 | `src/component/Pages/Login/Phases/PhaseConsent.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 15 | `src/component/Pages/Login/Phases/PhaseForgetPassword.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 16 | `src/component/Pages/Login/Phases/PhaseSignupNeeded.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。 |
| 17 | `src/component/Pages/Login/Reset.tsx` | `web/app/auth/src/modules/recovery` | 复用 | 已复用 | 目标 auth 已有 recovery 模块覆盖找回密码流程。 |
| 18 | `src/component/Pages/Login/SessionIntro.tsx` | `web/app/auth/src/layouts` | 复用 | 已复用 | 登录页会话介绍由 AuthLayout 插槽承担。 |
| 19 | `src/component/Pages/Login/SideTransition.css` | `web/packages/components/src/components/motion/motion.tsx` | 封装 | 已完成 | 侧栏切换过渡并入 motion token。 |
| 20 | `src/component/Pages/Login/Signin/DesktopCallback.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 目标 auth 登录模块已覆盖密码/OTP 流程；passkey 与第三方回调登记为待补。 |
| 21 | `src/component/Pages/Login/Signin/MobileCallback.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 目标 auth 登录模块已覆盖密码/OTP 流程；passkey 与第三方回调登记为待补。 |
| 22 | `src/component/Pages/Login/Signin/PasskeyLoginButton.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 目标 auth 登录模块已覆盖密码/OTP 流程；passkey 与第三方回调登记为待补。 |
| 23 | `src/component/Pages/Login/Signin/SignIn.tsx` | `web/app/auth/src/modules/login` | 复用 | 已复用 | 目标 auth 登录模块已覆盖密码/OTP 流程；passkey 与第三方回调登记为待补。 |
| 24 | `src/component/Pages/Login/Signup.tsx` | — | 暂缓 | 已登记（暂缓） | 目标暂未开放注册入口（管理员创建用户），登记待产品决策。 |
| 25 | `src/component/Pages/NoMatch.tsx` | `web/packages/components/src/components/empty-state/empty-state.tsx` | 迁移 | 已完成 | 迁移为 EmptyState variant=not-found（图标/标题/返回动作）。 |
| 26 | `src/component/Pages/PageContainer.tsx` | `web/packages/components/src/layouts/page-container/page-container.tsx` | 复用 | 已复用 | 目标库 PageContainer 已覆盖圆角容器与移动端适配。 |
| 27 | `src/component/Pages/PageHeader.tsx` | `web/packages/components/src/layouts/page-layout/page-layout.tsx` | 复用 | 已复用 | PageLayout 的 title/description/actions 已覆盖页头能力。 |
| 28 | `src/component/Pages/Pages.tsx` | `web/src/router` | 保留应用层 | 已保留 | 路由组合由 @web/router 与 app/*/router 承担。 |
| 29 | `src/component/Pages/Profile/Profile.tsx` | `web/app/uc/src/modules/profile` | 复用 | 已复用 | 目标 UC 已有 profile 模块，页面结构与交互已适配。 |
| 30 | `src/component/Pages/Setting/AvatarCropperDialog.tsx` | `web/packages/components/src/components/image-cropper/image-cropper.tsx` | 复用 | 已复用 | ImageCropper 已覆盖裁剪+输出 blob 能力。 |
| 31 | `src/component/Pages/Setting/AvatarSetting.tsx` | `web/app/uc/src/modules/profile` | 复用 | 已复用 | 目标 UC profile 模块已实现头像/资料编辑，复用其交互。 |
| 32 | `src/component/Pages/Setting/PreferenceSetting.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 偏好设置项（主题/语言/列表密度）分散在目标 console appearance 与全局 ThemeProvider，登记待对齐。 |
| 33 | `src/component/Pages/Setting/ProfileSetting.tsx` | `web/app/uc/src/modules/profile` | 复用 | 已复用 | 目标 UC profile 模块已实现头像/资料编辑，复用其交互。 |
| 34 | `src/component/Pages/Setting/ProfileSettingPopover.tsx` | `web/app/uc/src/modules/profile` | 复用 | 已复用 | 目标 UC profile 模块已实现头像/资料编辑，复用其交互。 |
| 35 | `src/component/Pages/Setting/Security/Disable2FADialog.tsx` | `web/app/uc/src/modules/security` | 复用 | 已复用 | 目标 UC security 模块覆盖 2FA/密码/会话安全设置；passkey 列表登记为待补。 |
| 36 | `src/component/Pages/Setting/Security/Enable2FADialog.tsx` | `web/app/uc/src/modules/security` | 复用 | 已复用 | 目标 UC security 模块覆盖 2FA/密码/会话安全设置；passkey 列表登记为待补。 |
| 37 | `src/component/Pages/Setting/Security/PasskeyList.tsx` | `web/app/uc/src/modules/security` | 复用 | 已复用 | 目标 UC security 模块覆盖 2FA/密码/会话安全设置；passkey 列表登记为待补。 |
| 38 | `src/component/Pages/Setting/Security/SecuritySetting.tsx` | `web/app/uc/src/modules/security` | 复用 | 已复用 | 目标 UC security 模块覆盖 2FA/密码/会话安全设置；passkey 列表登记为待补。 |
| 39 | `src/component/Pages/Setting/Setting.tsx` | `web/app/uc/src/layouts` | 复用 | 已复用 | UC 布局 + 模块路由已覆盖设置页壳层。 |
| 40 | `src/component/Pages/Setting/SettingForm.tsx` | `web/packages/components/src/components/form-field/form-field.tsx` | 复用 | 已复用 | 表单分组统一由 FormField/FormSection 承载。 |
| 41 | `src/component/Pages/Setting/SettingListItem.tsx` | `web/packages/components/src/components/form-field/form-field.tsx` | 迁移 | 已完成 | 迁移为 SettingItem（标题/描述/右侧动作），供 UC 与 Console 设置页复用。 |
| 42 | `src/component/Pages/Setting/StorageSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 存储配额设置依赖 cloudreve 容量接口，目标暂无对应用户设置面。 |
| 43 | `src/component/Pages/Shares/ShareCard.tsx` | — | 暂缓 | 已登记（暂缓） | 分享页依赖 cloudreve 分享 API，目标产品面未开放。 |
| 44 | `src/component/Pages/Shares/ShareList.tsx` | — | 暂缓 | 已登记（暂缓） | 分享页依赖 cloudreve 分享 API，目标产品面未开放。 |
| 45 | `src/component/Pages/Tasks/DownloadFileList.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 46 | `src/component/Pages/Tasks/DownloadList.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 47 | `src/component/Pages/Tasks/PieceProgress.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 48 | `src/component/Pages/Tasks/StepProgressBar.tsx` | `web/packages/components/src/components/progress-steps/progress-steps.tsx` | 迁移 | 已完成 | 迁移为公用的 ProgressSteps 步骤进度组件。 |
| 49 | `src/component/Pages/Tasks/StepProgressPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 50 | `src/component/Pages/Tasks/TaskCard.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 51 | `src/component/Pages/Tasks/TaskDetail.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 52 | `src/component/Pages/Tasks/TaskList.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 53 | `src/component/Pages/Tasks/TaskProgress.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 54 | `src/component/Pages/Tasks/TaskProgressStep.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 55 | `src/component/Pages/Tasks/TaskProps.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 56 | `src/component/Pages/Tasks/TaskSummaryStatus.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |
| 57 | `src/component/Pages/Tasks/TaskSummaryTitle.tsx` | — | 暂缓 | 已登记（暂缓） | 离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。 |

### component/Admin（165）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Admin/AdminBundle.tsx` | `web/app/console/src/router` | 保留应用层 | 已保留 | Admin 路由组合由 console router 承担。 |
| 2 | `src/component/Admin/Common/AdminCard.tsx` | `web/packages/components/src/components/settings-panel/settings-panel.tsx` | 复用 | 已复用 | 设置卡片统一由 SettingsPanel/Card 承载。 |
| 3 | `src/component/Admin/Common/EndpointInput.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 4 | `src/component/Admin/Common/GroupSelectionInput.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 5 | `src/component/Admin/Common/MagicVarDialog.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 6 | `src/component/Admin/Common/NodeSelectionInput.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 7 | `src/component/Admin/Common/ProDialog.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 8 | `src/component/Admin/Common/SharesInput.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 9 | `src/component/Admin/Common/SinglePolicySelectionInput.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。 |
| 10 | `src/component/Admin/Common/TablePagination.tsx` | `web/packages/components/src/components/pagination/pagination.tsx` | 复用 | 已复用 | 分页统一由 Pagination 承载。 |
| 11 | `src/component/Admin/Entity/EntityDeleteDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 12 | `src/component/Admin/Entity/EntityDialog/EntityDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 13 | `src/component/Admin/Entity/EntityDialog/EntityFileList.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 14 | `src/component/Admin/Entity/EntityDialog/EntityForm.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 15 | `src/component/Admin/Entity/EntityFilterPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 16 | `src/component/Admin/Entity/EntityRow.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 17 | `src/component/Admin/Entity/EntitySetting.tsx` | — | 暂缓 | 已登记（暂缓） | 实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。 |
| 18 | `src/component/Admin/File/FileDialog/FileDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 19 | `src/component/Admin/File/FileDialog/FileDirectLinks.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 20 | `src/component/Admin/File/FileDialog/FileEntity.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 21 | `src/component/Admin/File/FileDialog/FileForm.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 22 | `src/component/Admin/File/FileDialog/FileMetadata.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 23 | `src/component/Admin/File/FileFilterPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 24 | `src/component/Admin/File/FileRow.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 25 | `src/component/Admin/File/FileSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 26 | `src/component/Admin/File/ImportFileDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 27 | `src/component/Admin/File/UserSearchInput.tsx` | — | 暂缓 | 已登记（暂缓） | 文件管理后台依赖 cloudreve 文件 API 与文件应用。 |
| 28 | `src/component/Admin/FileSystem/CustomProps/CustomPropsSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 29 | `src/component/Admin/FileSystem/CustomProps/DraggableCustomPropsRow.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 30 | `src/component/Admin/FileSystem/CustomProps/EditPropsDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 31 | `src/component/Admin/FileSystem/Filesystem.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 32 | `src/component/Admin/FileSystem/FullTextSearch/FullTextSearchSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 33 | `src/component/Admin/FileSystem/HexColorInput.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 34 | `src/component/Admin/FileSystem/Icons/EmojiList.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 35 | `src/component/Admin/FileSystem/Icons/FileIconList.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 36 | `src/component/Admin/FileSystem/Icons/FileIcons.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 37 | `src/component/Admin/FileSystem/Parameters/AdvancedOptionsSection.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 38 | `src/component/Admin/FileSystem/Parameters/FileEncryptionSection.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 39 | `src/component/Admin/FileSystem/Parameters/FileEventPushSection.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 40 | `src/component/Admin/FileSystem/Parameters/FileSystemSection.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 41 | `src/component/Admin/FileSystem/Parameters/Parameters.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 42 | `src/component/Admin/FileSystem/Parameters/SearchQuerySection.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 43 | `src/component/Admin/FileSystem/ViewerSetting/DefaultViewerApps.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 44 | `src/component/Admin/FileSystem/ViewerSetting/FileViewerEditDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 45 | `src/component/Admin/FileSystem/ViewerSetting/FileViewerList.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 46 | `src/component/Admin/FileSystem/ViewerSetting/FileViewerRow.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 47 | `src/component/Admin/FileSystem/ViewerSetting/ImportWopiDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 48 | `src/component/Admin/FileSystem/ViewerSetting/ViewerSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。 |
| 49 | `src/component/Admin/Group/EditGroup/BasicInfoSection.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 50 | `src/component/Admin/Group/EditGroup/DefaultPinnedSection.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 51 | `src/component/Admin/Group/EditGroup/EditGroup.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 52 | `src/component/Admin/Group/EditGroup/FileManagementSection.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 53 | `src/component/Admin/Group/EditGroup/GroupForm.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 54 | `src/component/Admin/Group/EditGroup/GroupSettingWrapper.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 55 | `src/component/Admin/Group/EditGroup/MultipleNodeSelectionInput.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 56 | `src/component/Admin/Group/EditGroup/PolicySelectionInput.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 57 | `src/component/Admin/Group/EditGroup/ShareSection.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 58 | `src/component/Admin/Group/EditGroup/UploadDownloadSection.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 59 | `src/component/Admin/Group/GroupRow.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 60 | `src/component/Admin/Group/GroupSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 61 | `src/component/Admin/Group/NewGroupDIalog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 62 | `src/component/Admin/Home/Home.tsx` | `web/app/console/src/modules/overview` | 复用 | 已复用 | 控制台概览页已实现，复用其信息架构。 |
| 63 | `src/component/Admin/Home/SiteUrlWarning.tsx` | — | 暂缓 | 已登记（暂缓） | 站点 URL 告警依赖 cloudreve 后端站点配置，登记待设置页补齐。 |
| 64 | `src/component/Admin/Node/EditNode/BasicInfoSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 65 | `src/component/Admin/Node/EditNode/CapabilitiesSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 66 | `src/component/Admin/Node/EditNode/EditNode.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 67 | `src/component/Admin/Node/EditNode/NodeForm.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 68 | `src/component/Admin/Node/EditNode/NodeSettingWrapper.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 69 | `src/component/Admin/Node/EditNode/StoreFilesHintDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 70 | `src/component/Admin/Node/EditNode/index.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 71 | `src/component/Admin/Node/NewNode/NewNodeDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 72 | `src/component/Admin/Node/NodeCard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 73 | `src/component/Admin/Node/NodeSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。 |
| 74 | `src/component/Admin/OAuthClient/EditOAuthClient/EditOAuthClient.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。 |
| 75 | `src/component/Admin/OAuthClient/EditOAuthClient/OAuthClientForm.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。 |
| 76 | `src/component/Admin/OAuthClient/EditOAuthClient/OAuthClientSettingWrapper.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。 |
| 77 | `src/component/Admin/OAuthClient/OAuthClientCard.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。 |
| 78 | `src/component/Admin/OAuthClient/OAuthClientSetting.tsx` | — | 暂缓 | 已登记（暂缓） | OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。 |
| 79 | `src/component/Admin/Settings/Appearance/Appearance.tsx` | `web/app/console/src/modules/settings/components/appearance-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 80 | `src/component/Admin/Settings/Appearance/CustomHTML.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 81 | `src/component/Admin/Settings/Appearance/CustomNavItems.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 82 | `src/component/Admin/Settings/Appearance/ThemeOptionEditDialog.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 83 | `src/component/Admin/Settings/Appearance/ThemeOptions.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 84 | `src/component/Admin/Settings/Captcha/CapCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 85 | `src/component/Admin/Settings/Captcha/Captcha.tsx` | `web/app/console/src/modules/settings/components/captcha-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 86 | `src/component/Admin/Settings/Captcha/GraphicCaptcha.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 87 | `src/component/Admin/Settings/Captcha/ReCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 88 | `src/component/Admin/Settings/Captcha/TurnstileCaptcha.tsx` | `web/packages/components/src/components/captcha/captcha.tsx` | 封装 | 已完成 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 89 | `src/component/Admin/Settings/Email/Email.tsx` | `web/app/console/src/modules/settings/components/mail-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 90 | `src/component/Admin/Settings/Email/EmailTemplateEditor.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 91 | `src/component/Admin/Settings/Email/EmailTemplates.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。 |
| 92 | `src/component/Admin/Settings/Event/Events.tsx` | `web/app/console/src/modules/settings/components/events-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 93 | `src/component/Admin/Settings/Media/Extractors.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 94 | `src/component/Admin/Settings/Media/Generators.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 95 | `src/component/Admin/Settings/Media/Media.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 96 | `src/component/Admin/Settings/Queue/Queue.tsx` | `web/app/console/src/modules/settings/components/queue-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 97 | `src/component/Admin/Settings/Queue/QueueCard.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 98 | `src/component/Admin/Settings/Queue/QueueSettingDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 99 | `src/component/Admin/Settings/Server/ServerSetting.tsx` | `web/app/console/src/modules/settings/components/server-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 100 | `src/component/Admin/Settings/SettingWrapper.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端业务组件，目标 console 暂无对应实体/存储产品面；已在清单登记，待后端领域对齐后按模块迁移。 |
| 101 | `src/component/Admin/Settings/Settings.tsx` | — | 暂缓 | 已登记（暂缓） | cloudreve 管理端业务组件，目标 console 暂无对应实体/存储产品面；已在清单登记，待后端领域对齐后按模块迁移。 |
| 102 | `src/component/Admin/Settings/SiteInformation/GeneralImagePreview.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 103 | `src/component/Admin/Settings/SiteInformation/LogoPreview.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 104 | `src/component/Admin/Settings/SiteInformation/SiteInformation.tsx` | `web/app/console/src/modules/settings/components/site-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 105 | `src/component/Admin/Settings/SiteInformation/SiteURLInput.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 106 | `src/component/Admin/Settings/UserSession/SSOSettings.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 107 | `src/component/Admin/Settings/UserSession/UserSession.tsx` | `web/app/console/src/modules/settings/components/user-settings.tsx` | 复用 | 已复用 | 目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。 |
| 108 | `src/component/Admin/Settings/VAS/GiftCodes.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 109 | `src/component/Admin/Settings/VAS/GroupProducts.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 110 | `src/component/Admin/Settings/VAS/PaymentProviders.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 111 | `src/component/Admin/Settings/VAS/StorageProducts.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 112 | `src/component/Admin/Settings/VAS/VAS.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。 |
| 113 | `src/component/Admin/Share/ShareDialog/ShareDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 分享管理依赖 cloudreve 分享 API，目标产品面未开放。 |
| 114 | `src/component/Admin/Share/ShareDialog/ShareForm.tsx` | — | 暂缓 | 已登记（暂缓） | 分享管理依赖 cloudreve 分享 API，目标产品面未开放。 |
| 115 | `src/component/Admin/Share/ShareFilterPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 分享管理依赖 cloudreve 分享 API，目标产品面未开放。 |
| 116 | `src/component/Admin/Share/ShareList.tsx` | — | 暂缓 | 已登记（暂缓） | 分享管理依赖 cloudreve 分享 API，目标产品面未开放。 |
| 117 | `src/component/Admin/Share/ShareRow.tsx` | — | 暂缓 | 已登记（暂缓） | 分享管理依赖 cloudreve 分享 API，目标产品面未开放。 |
| 118 | `src/component/Admin/StoragePolicy/AddWizardDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 119 | `src/component/Admin/StoragePolicy/EditStoragePolicy/BucketACLInput.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 120 | `src/component/Admin/StoragePolicy/EditStoragePolicy/BucketCorsTable.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 121 | `src/component/Admin/StoragePolicy/EditStoragePolicy/EditStoragePolicy.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 122 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/BasicInfoSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 123 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/DownloadSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 124 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/EncryptionSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 125 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/MediaMetadataSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 126 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/OdSignInStatus.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 127 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/StorageAndUploadSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 128 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/ThumbnailsSection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 129 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/index.ts` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 130 | `src/component/Admin/StoragePolicy/EditStoragePolicy/FormSections/magicVars.ts` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 131 | `src/component/Admin/StoragePolicy/EditStoragePolicy/StoragePolicyForm.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 132 | `src/component/Admin/StoragePolicy/EditStoragePolicy/StoragePolicySettingWrapper.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 133 | `src/component/Admin/StoragePolicy/OauthCallback.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 134 | `src/component/Admin/StoragePolicy/SelectProvider.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 135 | `src/component/Admin/StoragePolicy/StoragePolicyCard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 136 | `src/component/Admin/StoragePolicy/StoragePolicySetting.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 137 | `src/component/Admin/StoragePolicy/TrafficDiagram.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 138 | `src/component/Admin/StoragePolicy/Wizards/COS/CosWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 139 | `src/component/Admin/StoragePolicy/Wizards/KS3/KS3Wizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 140 | `src/component/Admin/StoragePolicy/Wizards/Local/LocalWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 141 | `src/component/Admin/StoragePolicy/Wizards/OBS/ObsWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 142 | `src/component/Admin/StoragePolicy/Wizards/OSS/OssWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 143 | `src/component/Admin/StoragePolicy/Wizards/OneDrive/GraphEndpointSelection.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 144 | `src/component/Admin/StoragePolicy/Wizards/OneDrive/OneDriveAuthDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 145 | `src/component/Admin/StoragePolicy/Wizards/OneDrive/OneDriveWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 146 | `src/component/Admin/StoragePolicy/Wizards/Qiniu/QiniuWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 147 | `src/component/Admin/StoragePolicy/Wizards/Remote/RemoteWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 148 | `src/component/Admin/StoragePolicy/Wizards/S3/S3Wizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 149 | `src/component/Admin/StoragePolicy/Wizards/Upyun/UpyunWizard.tsx` | — | 暂缓 | 已登记（暂缓） | 存储策略为 cloudreve 存储架构专有，目标后端无此概念。 |
| 150 | `src/component/Admin/Task/TaskCleanupDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 151 | `src/component/Admin/Task/TaskContent.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 152 | `src/component/Admin/Task/TaskDialog/BlobErrors.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 153 | `src/component/Admin/Task/TaskDialog/TaskDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 154 | `src/component/Admin/Task/TaskDialog/TaskForm.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 155 | `src/component/Admin/Task/TaskFilterPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 156 | `src/component/Admin/Task/TaskList.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 157 | `src/component/Admin/Task/TaskRow.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 158 | `src/component/Admin/Task/TaskStatusSelector.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 159 | `src/component/Admin/Task/TaskTypeSelector.tsx` | — | 暂缓 | 已登记（暂缓） | 任务队列管理依赖 cloudreve 任务 API，目标暂无线程。 |
| 160 | `src/component/Admin/User/NewUserDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 161 | `src/component/Admin/User/UserDialog/UserDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 162 | `src/component/Admin/User/UserDialog/UserForm.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 163 | `src/component/Admin/User/UserFilterPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 164 | `src/component/Admin/User/UserRow.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |
| 165 | `src/component/Admin/User/UserSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。 |

### component/FileManager（129）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/FileManager/ContextMenu/CascadingMenu.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 2 | `src/component/FileManager/ContextMenu/ContextMenu.tsx` | `web/packages/components/src/components/dropdown-menu/dropdown-menu.tsx` | 复用 | 已复用 | DropdownMenu 已支持 contextMenu 坐标模式与菜单项语义，通用右键菜单复用。 |
| 3 | `src/component/FileManager/ContextMenu/HoverMenu.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 4 | `src/component/FileManager/ContextMenu/MoreMenuItems.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 5 | `src/component/FileManager/ContextMenu/NewFileTemplateMenuItems.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 6 | `src/component/FileManager/ContextMenu/OpenWithMenuItems.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 7 | `src/component/FileManager/ContextMenu/OrganizeMenuItems.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 8 | `src/component/FileManager/ContextMenu/TagMenuItems.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 9 | `src/component/FileManager/ContextMenu/useActionDisplayOpt.ts` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 10 | `src/component/FileManager/Dialogs/ChangeIcon.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 11 | `src/component/FileManager/Dialogs/CreateArchive.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 12 | `src/component/FileManager/Dialogs/CreateNew.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 13 | `src/component/FileManager/Dialogs/CreateRemoteDownload.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 14 | `src/component/FileManager/Dialogs/DeleteConfirmation.tsx` | `web/packages/components/src/components/confirm-dialog/confirm-dialog.tsx` | 封装 | 已完成 | 删除确认统一走 ConfirmDialog/useConfirm。 |
| 15 | `src/component/FileManager/Dialogs/DesktopMountSetup.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 16 | `src/component/FileManager/Dialogs/Dialogs.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 17 | `src/component/FileManager/Dialogs/DirectLinks/DirectLinkItem.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 18 | `src/component/FileManager/Dialogs/DirectLinks/DirectLinks.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 19 | `src/component/FileManager/Dialogs/DirectLinksControl.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 20 | `src/component/FileManager/Dialogs/ExtractArchive.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 21 | `src/component/FileManager/Dialogs/LockConflictDetails.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 22 | `src/component/FileManager/Dialogs/OpenWith.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 23 | `src/component/FileManager/Dialogs/PathSelection.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 24 | `src/component/FileManager/Dialogs/PinToSidebar.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 25 | `src/component/FileManager/Dialogs/Rename.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 26 | `src/component/FileManager/Dialogs/SaveAs.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 27 | `src/component/FileManager/Dialogs/Share/ManageShares.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 28 | `src/component/FileManager/Dialogs/Share/ShareDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 29 | `src/component/FileManager/Dialogs/Share/ShareSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 30 | `src/component/FileManager/Dialogs/StaleVersionConfirm.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 31 | `src/component/FileManager/Dialogs/Tags.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 32 | `src/component/FileManager/Dialogs/VersionControl.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 33 | `src/component/FileManager/Dnd/DisableDropDelay.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 34 | `src/component/FileManager/Dnd/DndWrappedFile.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 35 | `src/component/FileManager/Dnd/DragLayer.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 36 | `src/component/FileManager/Dnd/useDndScrolling.ts` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 37 | `src/component/FileManager/Explorer/EmojiIcon.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 38 | `src/component/FileManager/Explorer/EmptyFileList.tsx` | `web/packages/components/src/components/empty-state/empty-state.tsx` | 迁移 | 已完成 | 迁移为 EmptyState 的 file-drop 变体（可拖拽上传提示）。 |
| 39 | `src/component/FileManager/Explorer/Explorer.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 40 | `src/component/FileManager/Explorer/ExplorerError.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 41 | `src/component/FileManager/Explorer/FileIcon.tsx` | `web/packages/components/src/components/file-type-icon/file-type-icon.tsx` | 复用 | 已复用 | 文件图标统一由 FileTypeIcon 输出。 |
| 42 | `src/component/FileManager/Explorer/FileSmallIcon.tsx` | `web/packages/components/src/components/file-type-icon/file-type-icon.tsx` | 复用 | 已复用 | 小尺寸文件图标复用 FileTypeIcon size=sm。 |
| 43 | `src/component/FileManager/Explorer/FileTag.tsx` | — | 暂缓 | 已登记（暂缓） | 文件标签配色与 cloudreve 标签 API 绑定，登记待文件应用。 |
| 44 | `src/component/FileManager/Explorer/FileTagSummary.tsx` | — | 暂缓 | 已登记（暂缓） | 同上。 |
| 45 | `src/component/FileManager/Explorer/FileTypeIcon.tsx` | `web/packages/components/src/components/file-type-icon/file-type-icon.tsx` | 迁移 | 已完成 | 迁移为 FileTypeIcon：扩展名 -> cr: 图标 + 语义色 token，支持自定义映射。 |
| 46 | `src/component/FileManager/Explorer/GalleryView/GalleryImage.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 47 | `src/component/FileManager/Explorer/GalleryView/GalleryView.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 48 | `src/component/FileManager/Explorer/GridView/GridFile.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 49 | `src/component/FileManager/Explorer/GridView/GridView.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 50 | `src/component/FileManager/Explorer/ListView/AddColumn.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 51 | `src/component/FileManager/Explorer/ListView/Cell.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 52 | `src/component/FileManager/Explorer/ListView/Column.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 53 | `src/component/FileManager/Explorer/ListView/ColumnSetting.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 54 | `src/component/FileManager/Explorer/ListView/ListBody.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 55 | `src/component/FileManager/Explorer/ListView/ListHeader.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 56 | `src/component/FileManager/Explorer/ListView/ListThumbnail.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 57 | `src/component/FileManager/Explorer/ListView/ListView.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 58 | `src/component/FileManager/Explorer/ListView/Row.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 59 | `src/component/FileManager/Explorer/SingleFileView.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 60 | `src/component/FileManager/Explorer/UploadingTag.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 61 | `src/component/FileManager/Explorer/useInlineRename.ts` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 62 | `src/component/FileManager/FileBadge.tsx` | — | 暂缓 | 已登记（暂缓） | 文件徽标依赖 FileResponse 数据模型，待文件应用迁移。 |
| 63 | `src/component/FileManager/FileInfo/ColorCircle/CircleColorSelector.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 64 | `src/component/FileManager/FileInfo/FolderColorQuickAction.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 65 | `src/component/FileManager/FileManager.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 66 | `src/component/FileManager/FmIndexContext.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 67 | `src/component/FileManager/FolderPicker.tsx` | — | 暂缓 | 已登记（暂缓） | 目录选择器依赖 cloudreve 文件树，待文件应用迁移。 |
| 68 | `src/component/FileManager/NewButton.tsx` | `web/packages/components/src/components/button/button.tsx` | 复用 | 已复用 | 新建按钮复用 Button + DropdownMenu。 |
| 69 | `src/component/FileManager/Pagination/PaginationFooter.tsx` | `web/packages/components/src/components/pagination/pagination.tsx` | 复用 | 已复用 | 分页控件统一由 Pagination 承载。 |
| 70 | `src/component/FileManager/Pagination/PaginationItem.tsx` | `web/packages/components/src/components/pagination/pagination.tsx` | 复用 | 已复用 | 分页控件统一由 Pagination 承载。 |
| 71 | `src/component/FileManager/ReadMe/ReadMe.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 72 | `src/component/FileManager/ReadMe/ReadMeContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 73 | `src/component/FileManager/ReadMe/ReadMeDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 74 | `src/component/FileManager/ReadMe/ReadMeSideBar.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 75 | `src/component/FileManager/Search/AdvanceSearch/AddCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 76 | `src/component/FileManager/Search/AdvanceSearch/AdvanceSearch.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 77 | `src/component/FileManager/Search/AdvanceSearch/ConditionBox.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 78 | `src/component/FileManager/Search/AdvanceSearch/CustomPropsConditon.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 79 | `src/component/FileManager/Search/AdvanceSearch/DateTimeCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 80 | `src/component/FileManager/Search/AdvanceSearch/FileNameCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 81 | `src/component/FileManager/Search/AdvanceSearch/FileTypeCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 82 | `src/component/FileManager/Search/AdvanceSearch/MetadataCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 83 | `src/component/FileManager/Search/AdvanceSearch/SearchBaseCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 84 | `src/component/FileManager/Search/AdvanceSearch/SizeCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 85 | `src/component/FileManager/Search/AdvanceSearch/TagCondition.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 86 | `src/component/FileManager/Search/FullSearchOptions.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 87 | `src/component/FileManager/Search/FullTextSearchResult.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 88 | `src/component/FileManager/Search/FuzzySearchResult.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 89 | `src/component/FileManager/Search/SearchIndicator.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 90 | `src/component/FileManager/Search/SearchPopup.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 91 | `src/component/FileManager/Search/SearchResultItem.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 92 | `src/component/FileManager/Sidebar/BasicInfo.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 93 | `src/component/FileManager/Sidebar/CustomProps/AddButton.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 94 | `src/component/FileManager/Sidebar/CustomProps/BooleanPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 95 | `src/component/FileManager/Sidebar/CustomProps/CustomProps.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 96 | `src/component/FileManager/Sidebar/CustomProps/CustomPropsItem.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 97 | `src/component/FileManager/Sidebar/CustomProps/LinkPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 98 | `src/component/FileManager/Sidebar/CustomProps/MultiSelectPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 99 | `src/component/FileManager/Sidebar/CustomProps/NumberPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 100 | `src/component/FileManager/Sidebar/CustomProps/RatingPropsItem.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 101 | `src/component/FileManager/Sidebar/CustomProps/SelectPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 102 | `src/component/FileManager/Sidebar/CustomProps/TextPropsContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 103 | `src/component/FileManager/Sidebar/Data.tsx` | `web/packages/components/src/components/description-list/description-list.tsx` | 复用 | 已复用 | 基础信息面板复用 DescriptionList。 |
| 104 | `src/component/FileManager/Sidebar/Details.tsx` | `web/packages/components/src/components/description-list/description-list.tsx` | 复用 | 已复用 | 详情面板字段展示复用 DescriptionList。 |
| 105 | `src/component/FileManager/Sidebar/Header.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 106 | `src/component/FileManager/Sidebar/InfoRow.tsx` | `web/packages/components/src/components/description-list/description-list.tsx` | 迁移 | 已完成 | 迁移为 DescriptionList/DescriptionItem（label+value，响应式列）。 |
| 107 | `src/component/FileManager/Sidebar/Map/LeafletMapBox.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 108 | `src/component/FileManager/Sidebar/Map/MapLoader.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 109 | `src/component/FileManager/Sidebar/Map/Mapbox.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 110 | `src/component/FileManager/Sidebar/MediaInfo.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 111 | `src/component/FileManager/Sidebar/MediaMetaCard.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 112 | `src/component/FileManager/Sidebar/Sidebar.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 113 | `src/component/FileManager/Sidebar/SidebarContent.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 114 | `src/component/FileManager/Sidebar/SidebarDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 115 | `src/component/FileManager/Sidebar/Tags.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 116 | `src/component/FileManager/TopBar/Breadcrumb.tsx` | `web/packages/components/src/components/breadcrumbs/breadcrumbs.tsx` | 迁移 | 已完成 | 迁移为 Breadcrumbs：层级折叠、图标、可点击/可编辑路径，去掉 redux/URI 依赖。 |
| 117 | `src/component/FileManager/TopBar/BreadcrumbButton.tsx` | `web/packages/components/src/components/breadcrumbs/breadcrumbs.tsx` | 迁移 | 已完成 | 迁移为 Breadcrumbs：层级折叠、图标、可点击/可编辑路径，去掉 redux/URI 依赖。 |
| 118 | `src/component/FileManager/TopBar/BreadcrumbHiddenItem.tsx` | `web/packages/components/src/components/breadcrumbs/breadcrumbs.tsx` | 迁移 | 已完成 | 迁移为 Breadcrumbs：层级折叠、图标、可点击/可编辑路径，去掉 redux/URI 依赖。 |
| 119 | `src/component/FileManager/TopBar/MoreActionMenu.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 120 | `src/component/FileManager/TopBar/NavHeader.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 121 | `src/component/FileManager/TopBar/ShareInfoPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 122 | `src/component/FileManager/TopBar/SortMethodMenu.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 123 | `src/component/FileManager/TopBar/TopActions.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 124 | `src/component/FileManager/TopBar/TopActionsSecondary.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 125 | `src/component/FileManager/TopBar/ViewOptionPopover.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 126 | `src/component/FileManager/TreeView/Pinned.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 127 | `src/component/FileManager/TreeView/TreeFile.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 128 | `src/component/FileManager/TreeView/TreeFiles.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |
| 129 | `src/component/FileManager/TreeView/TreeNavigation.tsx` | — | 暂缓 | 已登记（暂缓） | 云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。 |

### component/Viewers（75）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Viewers/ArchivePreview/ArchivePreview.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 2 | `src/component/Viewers/CodeViewer/CodeViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 3 | `src/component/Viewers/CodeViewer/MonacoEditor.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 4 | `src/component/Viewers/CodeViewer/useWorker.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 5 | `src/component/Viewers/CustomViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 6 | `src/component/Viewers/DrawIO/DrawIOViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 7 | `src/component/Viewers/DrawIO/drawio.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 8 | `src/component/Viewers/EpubViewer/Epub.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 9 | `src/component/Viewers/EpubViewer/EpubViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 10 | `src/component/Viewers/Excalidraw/Excalidraw.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 11 | `src/component/Viewers/Excalidraw/ExcalidrawViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 12 | `src/component/Viewers/Excalidraw/excalidraw.css` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 13 | `src/component/Viewers/ImageViewer/ImageEditor.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 14 | `src/component/Viewers/ImageViewer/ImageViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 15 | `src/component/Viewers/ImageViewer/Lightbox.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 16 | `src/component/Viewers/ImageViewer/editor.css` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 17 | `src/component/Viewers/ImageViewer/react-photo-view/LICENSE.txt` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 18 | `src/component/Viewers/ImageViewer/react-photo-view/Photo.less` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 19 | `src/component/Viewers/ImageViewer/react-photo-view/Photo.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 20 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoBox.less` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 21 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoBox.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 22 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoProvider.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 23 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoSlider.less` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 24 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoSlider.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 25 | `src/component/Viewers/ImageViewer/react-photo-view/PhotoView.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 26 | `src/component/Viewers/ImageViewer/react-photo-view/components/ArrowLeft.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 27 | `src/component/Viewers/ImageViewer/react-photo-view/components/ArrowRight.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 28 | `src/component/Viewers/ImageViewer/react-photo-view/components/CloseIcon.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 29 | `src/component/Viewers/ImageViewer/react-photo-view/components/PreventScroll.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 30 | `src/component/Viewers/ImageViewer/react-photo-view/components/SlidePortal.less` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 31 | `src/component/Viewers/ImageViewer/react-photo-view/components/SlidePortal.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 32 | `src/component/Viewers/ImageViewer/react-photo-view/components/Spinner.less` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 33 | `src/component/Viewers/ImageViewer/react-photo-view/components/Spinner.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 34 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useAdjacentImages.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 35 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useAnimationOrigin.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 36 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useAnimationPosition.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 37 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useAnimationVisible.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 38 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useContinuousTap.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 39 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useDebounceCallback.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 40 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useEventListener.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 41 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useForkedVariable.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 42 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useInitial.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 43 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useIsomorphicLayoutEffect.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 44 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useMethods.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 45 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useMountedRef.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 46 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useScrollPosition.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 47 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useSetState.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 48 | `src/component/Viewers/ImageViewer/react-photo-view/hooks/useTargetScale.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 49 | `src/component/Viewers/ImageViewer/react-photo-view/index.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 50 | `src/component/Viewers/ImageViewer/react-photo-view/photo-context.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 51 | `src/component/Viewers/ImageViewer/react-photo-view/types.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 52 | `src/component/Viewers/ImageViewer/react-photo-view/utils/edgeHandle.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 53 | `src/component/Viewers/ImageViewer/react-photo-view/utils/getMultipleTouchPosition.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 54 | `src/component/Viewers/ImageViewer/react-photo-view/utils/getPositionOnMoveOrScale.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 55 | `src/component/Viewers/ImageViewer/react-photo-view/utils/getRotateSize.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 56 | `src/component/Viewers/ImageViewer/react-photo-view/utils/getSuitableImageSize.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 57 | `src/component/Viewers/ImageViewer/react-photo-view/utils/isTouchDevice.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 58 | `src/component/Viewers/ImageViewer/react-photo-view/utils/limitTarget.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 59 | `src/component/Viewers/ImageViewer/react-photo-view/variables.ts` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 60 | `src/component/Viewers/MarkdownEditor/Editor.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 61 | `src/component/Viewers/MarkdownEditor/MarkdownViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 62 | `src/component/Viewers/MarkdownEditor/editor.css` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 63 | `src/component/Viewers/MusicPlayer/MusicPlayer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 64 | `src/component/Viewers/MusicPlayer/PlayerPopup.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 65 | `src/component/Viewers/MusicPlayer/Playlist.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 66 | `src/component/Viewers/MusicPlayer/RepeatModePopover.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 67 | `src/component/Viewers/PdfViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 68 | `src/component/Viewers/Photopea/Photopea.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 69 | `src/component/Viewers/Photopea/SaveAsNewFormat.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 70 | `src/component/Viewers/Video/Artplayer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 71 | `src/component/Viewers/Video/SubtitleStyleDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 72 | `src/component/Viewers/Video/VideoViewer.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 73 | `src/component/Viewers/Video/artplayer.css` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 74 | `src/component/Viewers/ViewerDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |
| 75 | `src/component/Viewers/Wopi.tsx` | — | 暂缓 | 已登记（暂缓） | 文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。 |

### component/Uploader（34）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/component/Uploader/DropFile.tsx` | `web/packages/components/src/components/file-drop-zone/file-drop-zone.tsx` | 迁移 | 已完成 | 迁移为 FileDropZone（全屏遮罩/内嵌两种形态，拖拽高亮与键盘可达）。 |
| 2 | `src/component/Uploader/PasteUploadDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 3 | `src/component/Uploader/Popup/ConcurrentOptionDialog.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 4 | `src/component/Uploader/Popup/MoreActions.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 5 | `src/component/Uploader/Popup/TaskDetail.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 6 | `src/component/Uploader/Popup/TaskList.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 7 | `src/component/Uploader/Popup/UploadTask.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 8 | `src/component/Uploader/TaskListIconButton.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 9 | `src/component/Uploader/Uploader.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 10 | `src/component/Uploader/UseUpload.tsx` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 11 | `src/component/Uploader/core/api/index.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 12 | `src/component/Uploader/core/errors/index.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 13 | `src/component/Uploader/core/index.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 14 | `src/component/Uploader/core/logger.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 15 | `src/component/Uploader/core/types.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 16 | `src/component/Uploader/core/uploader/base.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 17 | `src/component/Uploader/core/uploader/chunk.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 18 | `src/component/Uploader/core/uploader/cos.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 19 | `src/component/Uploader/core/uploader/encrypt/blob.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 20 | `src/component/Uploader/core/uploader/ks3.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 21 | `src/component/Uploader/core/uploader/local.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 22 | `src/component/Uploader/core/uploader/obs.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 23 | `src/component/Uploader/core/uploader/onedrive.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 24 | `src/component/Uploader/core/uploader/oss.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 25 | `src/component/Uploader/core/uploader/placeholder.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 26 | `src/component/Uploader/core/uploader/qiniu.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 27 | `src/component/Uploader/core/uploader/remote.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 28 | `src/component/Uploader/core/uploader/s3.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 29 | `src/component/Uploader/core/uploader/upyun.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 30 | `src/component/Uploader/core/utils/helper.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 31 | `src/component/Uploader/core/utils/index.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 32 | `src/component/Uploader/core/utils/pool.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 33 | `src/component/Uploader/core/utils/request.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |
| 34 | `src/component/Uploader/core/utils/validator.ts` | — | 暂缓 | 已登记（暂缓） | 上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。 |

### api（9）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/api/api.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 2 | `src/api/dashboard.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 3 | `src/api/explorer.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 4 | `src/api/request.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 5 | `src/api/setting.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 6 | `src/api/share.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 7 | `src/api/site.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 8 | `src/api/user.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |
| 9 | `src/api/workflow.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。 |

### constants（1）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/constants/index.ts` | `web/packages/shared/src/constants.ts` | 封装 | 已完成 | 通用常量（版本/布局/键位）并入 @pudans/shared 常量；cloudreve 专有常量不迁移。 |

### hooks（5）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/hooks/areaSelection.ts` | — | 暂缓 | 已登记（暂缓） | 框选逻辑与文件网格 DOM 结构强耦合，待文件应用迁移。 |
| 2 | `src/hooks/delayedHover.tsx` | `web/packages/components/src/hooks/use-delayed-hover.ts` | 迁移 | 已完成 | 迁移为 useDelayedHover（可配置延迟，清理定时器，支持 popup state）。 |
| 3 | `src/hooks/useMediaSession.ts` | `web/packages/components/src/hooks/use-media-session.ts` | 迁移 | 已完成 | 迁移为 useMediaSession（泛化 MediaSession API，去掉 FileResponse 依赖）。 |
| 4 | `src/hooks/useNavigation.tsx` | — | 暂缓 | 已登记（暂缓） | 文件路径导航 thunk 编排，待文件应用迁移。 |
| 5 | `src/hooks/useOverflow.tsx` | `web/packages/components/src/hooks/use-overflow.ts` | 迁移 | 已完成 | 迁移为 useOverflow（ResizeObserver + 返回值）。 |

### redux（14）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/redux/fileManagerSlice.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 2 | `src/redux/globalStateSlice.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 3 | `src/redux/hooks.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 4 | `src/redux/siteConfigSlice.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 5 | `src/redux/store.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 6 | `src/redux/thunks/dialog.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 7 | `src/redux/thunks/download.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 8 | `src/redux/thunks/file.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 9 | `src/redux/thunks/filemanager.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 10 | `src/redux/thunks/session.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 11 | `src/redux/thunks/settings.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 12 | `src/redux/thunks/share.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 13 | `src/redux/thunks/site.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |
| 14 | `src/redux/thunks/viewer.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。 |

### router（2）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/router/PageTitle.tsx` | — | 暂缓 | 已登记（暂缓） | 文档标题副作用由各应用路由处理，登记待统一 title hook。 |
| 2 | `src/router/index.tsx` | `web/src/router` | 复用 | 已复用 | 路由组合由 @web/router（BrowserRouter + guards）与各 app router 承担。 |

### session（3）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/session/errors.ts` | `web/packages/api-client/src` | 复用 | 已复用 | 错误类与归一化由 @pudans/api-client 承担。 |
| 2 | `src/session/index.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve SessionManager（localStorage 键值 + 用户会话）与目标 Redux persisted store 架构不同；偏好键位登记至 shared。 |
| 3 | `src/session/utils.ts` | — | 暂缓 | 已登记（暂缓） | 会话工具与 cloudreve 登录态绑定。 |

### util（9）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/util/base64.ts` | `web/packages/shared/src/utils/base64.ts` | 迁移 | 已完成 | 迁移 base64Encode/base64Decode 到 @pudans/shared/utils。 |
| 2 | `src/util/boolset.ts` | `web/packages/shared/src/utils/boolset.ts` | 迁移 | 已完成 | 迁移 Boolset 位图工具到 @pudans/shared/utils 并补测试。 |
| 3 | `src/util/datetime.ts` | `web/packages/shared/src/utils/datetime.ts` | 迁移 | 已完成 | 迁移时间格式化/时长格式化（注入 locale/timezone，不依赖 i18n 单例）。 |
| 4 | `src/util/filesystem.ts` | `web/packages/shared/src/utils/filesystem.ts` | 迁移 | 已完成 | 迁移 File System Access API 工具（目录创建/流式写文件/权限校验）。 |
| 5 | `src/util/index.ts` | `web/packages/shared/src/index.ts` | 复用 | 已复用 | 通用工具汇总由 @pudans/shared 导出。 |
| 6 | `src/util/lru.ts` | `web/packages/shared/src/utils/lru.ts` | 迁移 | 已完成 | 迁移 LRUCache（带 blob URL 回收）到 @pudans/shared/utils。 |
| 7 | `src/util/permission.ts` | — | 暂缓 | 已登记（暂缓） | 文件复制/移动权限判定依赖 cloudreve URI 语义，待文件应用。 |
| 8 | `src/util/uri.ts` | — | 暂缓 | 已登记（暂缓） | cloudreve:// URI 解析器为云盘专有协议，目标无对应协议。 |
| 9 | `src/util/zip.js` | — | 暂缓 | 已登记（暂缓） | 压缩流实现与 cloudreve 归档 API 绑定，登记待文件应用。 |

### src 根文件（4）

| # | 源路径 | 目标路径 | 处理方式 | 状态 | 理由 |
| --- | --- | --- | --- | --- | --- |
| 1 | `src/App.tsx` | `web/src/providers` | 保留应用层 | 已保留 | 主题/Snackbar/全局对话框/ServiceWorker 编排已由目标 Providers + PudansThemeProvider + ToastProvider 重新实现。 |
| 2 | `src/i18n.ts` | `web/src/i18n` | 保留应用层 | 已保留 | i18n 初始化已迁移到 @web/i18n（资源合并策略不同）。 |
| 3 | `src/main.tsx` | `web/app/auth/src/main.tsx` | 保留应用层 | 已保留 | 各应用入口由 Vite MPA 拆分承担（auth/setup/uc/console 各自 main.tsx）。 |
| 4 | `src/vite-env.d.ts` | — | 暂缓 | 已登记（暂缓） | 未归类源码登记待迁移。 |

## 二、源项目 public/ 静态资源清单

| 目录 | 文件数 | 处理方式 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `public/assets/pdfjs/…` | 365 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/ar-AR/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/de-DE/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/en-US/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/es-ES/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/fr-FR/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/it-IT/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/ja-JP/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/ko-KR/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/pl-PL/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/pt-BR/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/ru-RU/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/zh-CN/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/locales/zh-TW/…` | 5 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/pdfviewer.html` | 1 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/service-worker.js` | 1 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |
| `public/static/img/…` | 29 | 暂缓 | 已登记（暂缓） | 见附录逐文件清单与理由 |

<details><summary>展开 public 全量文件路径（461）</summary>

**public/assets/pdfjs/…**

`public/assets/pdfjs/cmaps/78-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/78-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/78-H.bcmap`、`public/assets/pdfjs/cmaps/78-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/78-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/78-V.bcmap`、`public/assets/pdfjs/cmaps/78ms-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/78ms-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/83pv-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/90ms-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/90ms-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/90msp-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/90msp-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/90pv-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/90pv-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/Add-H.bcmap`、`public/assets/pdfjs/cmaps/Add-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/Add-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/Add-V.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-0.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-1.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-3.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-4.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-5.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-6.bcmap`、`public/assets/pdfjs/cmaps/Adobe-CNS1-UCS2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-0.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-1.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-3.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-4.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-5.bcmap`、`public/assets/pdfjs/cmaps/Adobe-GB1-UCS2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-0.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-1.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-3.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-4.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-5.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-6.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Japan1-UCS2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Korea1-0.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Korea1-1.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Korea1-2.bcmap`、`public/assets/pdfjs/cmaps/Adobe-Korea1-UCS2.bcmap`、`public/assets/pdfjs/cmaps/B5-H.bcmap`、`public/assets/pdfjs/cmaps/B5-V.bcmap`、`public/assets/pdfjs/cmaps/B5pc-H.bcmap`、`public/assets/pdfjs/cmaps/B5pc-V.bcmap`、`public/assets/pdfjs/cmaps/CNS-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/CNS-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/CNS1-H.bcmap`、`public/assets/pdfjs/cmaps/CNS1-V.bcmap`、`public/assets/pdfjs/cmaps/CNS2-H.bcmap`、`public/assets/pdfjs/cmaps/CNS2-V.bcmap`、`public/assets/pdfjs/cmaps/ETHK-B5-H.bcmap`、`public/assets/pdfjs/cmaps/ETHK-B5-V.bcmap`、`public/assets/pdfjs/cmaps/ETen-B5-H.bcmap`、`public/assets/pdfjs/cmaps/ETen-B5-V.bcmap`、`public/assets/pdfjs/cmaps/ETenms-B5-H.bcmap`、`public/assets/pdfjs/cmaps/ETenms-B5-V.bcmap`、`public/assets/pdfjs/cmaps/EUC-H.bcmap`、`public/assets/pdfjs/cmaps/EUC-V.bcmap`、`public/assets/pdfjs/cmaps/Ext-H.bcmap`、`public/assets/pdfjs/cmaps/Ext-RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/Ext-RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/Ext-V.bcmap`、`public/assets/pdfjs/cmaps/GB-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GB-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/GB-H.bcmap`、`public/assets/pdfjs/cmaps/GB-V.bcmap`、`public/assets/pdfjs/cmaps/GBK-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GBK-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/GBK2K-H.bcmap`、`public/assets/pdfjs/cmaps/GBK2K-V.bcmap`、`public/assets/pdfjs/cmaps/GBKp-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GBKp-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/GBT-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GBT-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/GBT-H.bcmap`、`public/assets/pdfjs/cmaps/GBT-V.bcmap`、`public/assets/pdfjs/cmaps/GBTpc-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GBTpc-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/GBpc-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/GBpc-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/H.bcmap`、`public/assets/pdfjs/cmaps/HKdla-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKdla-B5-V.bcmap`、`public/assets/pdfjs/cmaps/HKdlb-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKdlb-B5-V.bcmap`、`public/assets/pdfjs/cmaps/HKgccs-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKgccs-B5-V.bcmap`、`public/assets/pdfjs/cmaps/HKm314-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKm314-B5-V.bcmap`、`public/assets/pdfjs/cmaps/HKm471-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKm471-B5-V.bcmap`、`public/assets/pdfjs/cmaps/HKscs-B5-H.bcmap`、`public/assets/pdfjs/cmaps/HKscs-B5-V.bcmap`、`public/assets/pdfjs/cmaps/Hankaku.bcmap`、`public/assets/pdfjs/cmaps/Hiragana.bcmap`、`public/assets/pdfjs/cmaps/KSC-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/KSC-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/KSC-H.bcmap`、`public/assets/pdfjs/cmaps/KSC-Johab-H.bcmap`、`public/assets/pdfjs/cmaps/KSC-Johab-V.bcmap`、`public/assets/pdfjs/cmaps/KSC-V.bcmap`、`public/assets/pdfjs/cmaps/KSCms-UHC-H.bcmap`、`public/assets/pdfjs/cmaps/KSCms-UHC-HW-H.bcmap`、`public/assets/pdfjs/cmaps/KSCms-UHC-HW-V.bcmap`、`public/assets/pdfjs/cmaps/KSCms-UHC-V.bcmap`、`public/assets/pdfjs/cmaps/KSCpc-EUC-H.bcmap`、`public/assets/pdfjs/cmaps/KSCpc-EUC-V.bcmap`、`public/assets/pdfjs/cmaps/Katakana.bcmap`、`public/assets/pdfjs/cmaps/LICENSE`、`public/assets/pdfjs/cmaps/NWP-H.bcmap`、`public/assets/pdfjs/cmaps/NWP-V.bcmap`、`public/assets/pdfjs/cmaps/RKSJ-H.bcmap`、`public/assets/pdfjs/cmaps/RKSJ-V.bcmap`、`public/assets/pdfjs/cmaps/Roman.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UCS2-H.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UCS2-V.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF16-H.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF16-V.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF8-H.bcmap`、`public/assets/pdfjs/cmaps/UniCNS-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UCS2-H.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UCS2-V.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF16-H.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF16-V.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF8-H.bcmap`、`public/assets/pdfjs/cmaps/UniGB-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UCS2-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UCS2-HW-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UCS2-HW-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UCS2-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF16-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF16-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF8-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF16-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF16-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF8-H.bcmap`、`public/assets/pdfjs/cmaps/UniJIS2004-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/UniJISPro-UCS2-HW-V.bcmap`、`public/assets/pdfjs/cmaps/UniJISPro-UCS2-V.bcmap`、`public/assets/pdfjs/cmaps/UniJISPro-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/UniJISX0213-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniJISX0213-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniJISX02132004-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniJISX02132004-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UCS2-H.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UCS2-V.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF16-H.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF16-V.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF32-H.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF32-V.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF8-H.bcmap`、`public/assets/pdfjs/cmaps/UniKS-UTF8-V.bcmap`、`public/assets/pdfjs/cmaps/V.bcmap`、`public/assets/pdfjs/cmaps/WP-Symbol.bcmap`、`public/assets/pdfjs/images/altText_add.svg`、`public/assets/pdfjs/images/altText_disclaimer.svg`、`public/assets/pdfjs/images/altText_done.svg`、`public/assets/pdfjs/images/altText_spinner.svg`、`public/assets/pdfjs/images/altText_warning.svg`、`public/assets/pdfjs/images/annotation-check.svg`、`public/assets/pdfjs/images/annotation-comment.svg`、`public/assets/pdfjs/images/annotation-help.svg`、`public/assets/pdfjs/images/annotation-insert.svg`、`public/assets/pdfjs/images/annotation-key.svg`、`public/assets/pdfjs/images/annotation-newparagraph.svg`、`public/assets/pdfjs/images/annotation-noicon.svg`、`public/assets/pdfjs/images/annotation-note.svg`、`public/assets/pdfjs/images/annotation-paperclip.svg`、`public/assets/pdfjs/images/annotation-paragraph.svg`、`public/assets/pdfjs/images/annotation-pushpin.svg`、`public/assets/pdfjs/images/cursor-editorFreeHighlight.svg`、`public/assets/pdfjs/images/cursor-editorFreeText.svg`、`public/assets/pdfjs/images/cursor-editorInk.svg`、`public/assets/pdfjs/images/cursor-editorTextHighlight.svg`、`public/assets/pdfjs/images/editor-toolbar-delete.svg`、`public/assets/pdfjs/images/findbarButton-next.svg`、`public/assets/pdfjs/images/findbarButton-previous.svg`、`public/assets/pdfjs/images/gv-toolbarButton-download.svg`、`public/assets/pdfjs/images/loading-icon.gif`、`public/assets/pdfjs/images/loading.svg`、`public/assets/pdfjs/images/messageBar_closingButton.svg`、`public/assets/pdfjs/images/messageBar_warning.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-documentProperties.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-firstPage.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-handTool.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-lastPage.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-rotateCcw.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-rotateCw.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-scrollHorizontal.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-scrollPage.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-scrollVertical.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-scrollWrapped.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-selectTool.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-spreadEven.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-spreadNone.svg`、`public/assets/pdfjs/images/secondaryToolbarButton-spreadOdd.svg`、`public/assets/pdfjs/images/toolbarButton-bookmark.svg`、`public/assets/pdfjs/images/toolbarButton-currentOutlineItem.svg`、`public/assets/pdfjs/images/toolbarButton-download.svg`、`public/assets/pdfjs/images/toolbarButton-editorFreeText.svg`、`public/assets/pdfjs/images/toolbarButton-editorHighlight.svg`、`public/assets/pdfjs/images/toolbarButton-editorInk.svg`、`public/assets/pdfjs/images/toolbarButton-editorStamp.svg`、`public/assets/pdfjs/images/toolbarButton-menuArrow.svg`、`public/assets/pdfjs/images/toolbarButton-openFile.svg`、`public/assets/pdfjs/images/toolbarButton-pageDown.svg`、`public/assets/pdfjs/images/toolbarButton-pageUp.svg`、`public/assets/pdfjs/images/toolbarButton-presentationMode.svg`、`public/assets/pdfjs/images/toolbarButton-print.svg`、`public/assets/pdfjs/images/toolbarButton-search.svg`、`public/assets/pdfjs/images/toolbarButton-secondaryToolbarToggle.svg`、`public/assets/pdfjs/images/toolbarButton-sidebarToggle.svg`、`public/assets/pdfjs/images/toolbarButton-viewAttachments.svg`、`public/assets/pdfjs/images/toolbarButton-viewLayers.svg`、`public/assets/pdfjs/images/toolbarButton-viewOutline.svg`、`public/assets/pdfjs/images/toolbarButton-viewThumbnail.svg`、`public/assets/pdfjs/images/toolbarButton-zoomIn.svg`、`public/assets/pdfjs/images/toolbarButton-zoomOut.svg`、`public/assets/pdfjs/images/treeitem-collapsed.svg`、`public/assets/pdfjs/images/treeitem-expanded.svg`、`public/assets/pdfjs/locale/ach/viewer.ftl`、`public/assets/pdfjs/locale/af/viewer.ftl`、`public/assets/pdfjs/locale/an/viewer.ftl`、`public/assets/pdfjs/locale/ar/viewer.ftl`、`public/assets/pdfjs/locale/ast/viewer.ftl`、`public/assets/pdfjs/locale/az/viewer.ftl`、`public/assets/pdfjs/locale/be/viewer.ftl`、`public/assets/pdfjs/locale/bg/viewer.ftl`、`public/assets/pdfjs/locale/bn/viewer.ftl`、`public/assets/pdfjs/locale/bo/viewer.ftl`、`public/assets/pdfjs/locale/br/viewer.ftl`、`public/assets/pdfjs/locale/brx/viewer.ftl`、`public/assets/pdfjs/locale/bs/viewer.ftl`、`public/assets/pdfjs/locale/ca/viewer.ftl`、`public/assets/pdfjs/locale/cak/viewer.ftl`、`public/assets/pdfjs/locale/ckb/viewer.ftl`、`public/assets/pdfjs/locale/cs/viewer.ftl`、`public/assets/pdfjs/locale/cy/viewer.ftl`、`public/assets/pdfjs/locale/da/viewer.ftl`、`public/assets/pdfjs/locale/de/viewer.ftl`、`public/assets/pdfjs/locale/dsb/viewer.ftl`、`public/assets/pdfjs/locale/el/viewer.ftl`、`public/assets/pdfjs/locale/en-CA/viewer.ftl`、`public/assets/pdfjs/locale/en-GB/viewer.ftl`、`public/assets/pdfjs/locale/en-US/viewer.ftl`、`public/assets/pdfjs/locale/eo/viewer.ftl`、`public/assets/pdfjs/locale/es-AR/viewer.ftl`、`public/assets/pdfjs/locale/es-CL/viewer.ftl`、`public/assets/pdfjs/locale/es-ES/viewer.ftl`、`public/assets/pdfjs/locale/es-MX/viewer.ftl`、`public/assets/pdfjs/locale/et/viewer.ftl`、`public/assets/pdfjs/locale/eu/viewer.ftl`、`public/assets/pdfjs/locale/fa/viewer.ftl`、`public/assets/pdfjs/locale/ff/viewer.ftl`、`public/assets/pdfjs/locale/fi/viewer.ftl`、`public/assets/pdfjs/locale/fr/viewer.ftl`、`public/assets/pdfjs/locale/fur/viewer.ftl`、`public/assets/pdfjs/locale/fy-NL/viewer.ftl`、`public/assets/pdfjs/locale/ga-IE/viewer.ftl`、`public/assets/pdfjs/locale/gd/viewer.ftl`、`public/assets/pdfjs/locale/gl/viewer.ftl`、`public/assets/pdfjs/locale/gn/viewer.ftl`、`public/assets/pdfjs/locale/gu-IN/viewer.ftl`、`public/assets/pdfjs/locale/he/viewer.ftl`、`public/assets/pdfjs/locale/hi-IN/viewer.ftl`、`public/assets/pdfjs/locale/hr/viewer.ftl`、`public/assets/pdfjs/locale/hsb/viewer.ftl`、`public/assets/pdfjs/locale/hu/viewer.ftl`、`public/assets/pdfjs/locale/hy-AM/viewer.ftl`、`public/assets/pdfjs/locale/hye/viewer.ftl`、`public/assets/pdfjs/locale/ia/viewer.ftl`、`public/assets/pdfjs/locale/id/viewer.ftl`、`public/assets/pdfjs/locale/is/viewer.ftl`、`public/assets/pdfjs/locale/it/viewer.ftl`、`public/assets/pdfjs/locale/ja/viewer.ftl`、`public/assets/pdfjs/locale/ka/viewer.ftl`、`public/assets/pdfjs/locale/kab/viewer.ftl`、`public/assets/pdfjs/locale/kk/viewer.ftl`、`public/assets/pdfjs/locale/km/viewer.ftl`、`public/assets/pdfjs/locale/kn/viewer.ftl`、`public/assets/pdfjs/locale/ko/viewer.ftl`、`public/assets/pdfjs/locale/lij/viewer.ftl`、`public/assets/pdfjs/locale/lo/viewer.ftl`、`public/assets/pdfjs/locale/locale.json`、`public/assets/pdfjs/locale/lt/viewer.ftl`、`public/assets/pdfjs/locale/ltg/viewer.ftl`、`public/assets/pdfjs/locale/lv/viewer.ftl`、`public/assets/pdfjs/locale/meh/viewer.ftl`、`public/assets/pdfjs/locale/mk/viewer.ftl`、`public/assets/pdfjs/locale/mr/viewer.ftl`、`public/assets/pdfjs/locale/ms/viewer.ftl`、`public/assets/pdfjs/locale/my/viewer.ftl`、`public/assets/pdfjs/locale/nb-NO/viewer.ftl`、`public/assets/pdfjs/locale/ne-NP/viewer.ftl`、`public/assets/pdfjs/locale/nl/viewer.ftl`、`public/assets/pdfjs/locale/nn-NO/viewer.ftl`、`public/assets/pdfjs/locale/oc/viewer.ftl`、`public/assets/pdfjs/locale/pa-IN/viewer.ftl`、`public/assets/pdfjs/locale/pl/viewer.ftl`、`public/assets/pdfjs/locale/pt-BR/viewer.ftl`、`public/assets/pdfjs/locale/pt-PT/viewer.ftl`、`public/assets/pdfjs/locale/rm/viewer.ftl`、`public/assets/pdfjs/locale/ro/viewer.ftl`、`public/assets/pdfjs/locale/ru/viewer.ftl`、`public/assets/pdfjs/locale/sat/viewer.ftl`、`public/assets/pdfjs/locale/sc/viewer.ftl`、`public/assets/pdfjs/locale/scn/viewer.ftl`、`public/assets/pdfjs/locale/sco/viewer.ftl`、`public/assets/pdfjs/locale/si/viewer.ftl`、`public/assets/pdfjs/locale/sk/viewer.ftl`、`public/assets/pdfjs/locale/skr/viewer.ftl`、`public/assets/pdfjs/locale/sl/viewer.ftl`、`public/assets/pdfjs/locale/son/viewer.ftl`、`public/assets/pdfjs/locale/sq/viewer.ftl`、`public/assets/pdfjs/locale/sr/viewer.ftl`、`public/assets/pdfjs/locale/sv-SE/viewer.ftl`、`public/assets/pdfjs/locale/szl/viewer.ftl`、`public/assets/pdfjs/locale/ta/viewer.ftl`、`public/assets/pdfjs/locale/te/viewer.ftl`、`public/assets/pdfjs/locale/tg/viewer.ftl`、`public/assets/pdfjs/locale/th/viewer.ftl`、`public/assets/pdfjs/locale/tl/viewer.ftl`、`public/assets/pdfjs/locale/tr/viewer.ftl`、`public/assets/pdfjs/locale/trs/viewer.ftl`、`public/assets/pdfjs/locale/uk/viewer.ftl`、`public/assets/pdfjs/locale/ur/viewer.ftl`、`public/assets/pdfjs/locale/uz/viewer.ftl`、`public/assets/pdfjs/locale/vi/viewer.ftl`、`public/assets/pdfjs/locale/wo/viewer.ftl`、`public/assets/pdfjs/locale/xh/viewer.ftl`、`public/assets/pdfjs/locale/zh-CN/viewer.ftl`、`public/assets/pdfjs/locale/zh-TW/viewer.ftl`、`public/assets/pdfjs/standard_fonts/FoxitDingbats.pfb`、`public/assets/pdfjs/standard_fonts/FoxitFixed.pfb`、`public/assets/pdfjs/standard_fonts/FoxitFixedBold.pfb`、`public/assets/pdfjs/standard_fonts/FoxitFixedBoldItalic.pfb`、`public/assets/pdfjs/standard_fonts/FoxitFixedItalic.pfb`、`public/assets/pdfjs/standard_fonts/FoxitSerif.pfb`、`public/assets/pdfjs/standard_fonts/FoxitSerifBold.pfb`、`public/assets/pdfjs/standard_fonts/FoxitSerifBoldItalic.pfb`、`public/assets/pdfjs/standard_fonts/FoxitSerifItalic.pfb`、`public/assets/pdfjs/standard_fonts/FoxitSymbol.pfb`、`public/assets/pdfjs/standard_fonts/LICENSE_FOXIT`、`public/assets/pdfjs/standard_fonts/LICENSE_LIBERATION`、`public/assets/pdfjs/standard_fonts/LiberationSans-Bold.ttf`、`public/assets/pdfjs/standard_fonts/LiberationSans-BoldItalic.ttf`、`public/assets/pdfjs/standard_fonts/LiberationSans-Italic.ttf`、`public/assets/pdfjs/standard_fonts/LiberationSans-Regular.ttf`、`public/assets/pdfjs/viewer.css`、`public/assets/pdfjs/viewer.mjs`

**public/locales/ar-AR/…**

`public/locales/ar-AR/application.json`、`public/locales/ar-AR/common.json`、`public/locales/ar-AR/dashboard.json`、`public/locales/ar-AR/image_editor.json`、`public/locales/ar-AR/markdown_editor.json`

**public/locales/de-DE/…**

`public/locales/de-DE/application.json`、`public/locales/de-DE/common.json`、`public/locales/de-DE/dashboard.json`、`public/locales/de-DE/image_editor.json`、`public/locales/de-DE/markdown_editor.json`

**public/locales/en-US/…**

`public/locales/en-US/application.json`、`public/locales/en-US/common.json`、`public/locales/en-US/dashboard.json`、`public/locales/en-US/image_editor.json`、`public/locales/en-US/markdown_editor.json`

**public/locales/es-ES/…**

`public/locales/es-ES/application.json`、`public/locales/es-ES/common.json`、`public/locales/es-ES/dashboard.json`、`public/locales/es-ES/image_editor.json`、`public/locales/es-ES/markdown_editor.json`

**public/locales/fr-FR/…**

`public/locales/fr-FR/application.json`、`public/locales/fr-FR/common.json`、`public/locales/fr-FR/dashboard.json`、`public/locales/fr-FR/image_editor.json`、`public/locales/fr-FR/markdown_editor.json`

**public/locales/it-IT/…**

`public/locales/it-IT/application.json`、`public/locales/it-IT/common.json`、`public/locales/it-IT/dashboard.json`、`public/locales/it-IT/image_editor.json`、`public/locales/it-IT/markdown_editor.json`

**public/locales/ja-JP/…**

`public/locales/ja-JP/application.json`、`public/locales/ja-JP/common.json`、`public/locales/ja-JP/dashboard.json`、`public/locales/ja-JP/image_editor.json`、`public/locales/ja-JP/markdown_editor.json`

**public/locales/ko-KR/…**

`public/locales/ko-KR/application.json`、`public/locales/ko-KR/common.json`、`public/locales/ko-KR/dashboard.json`、`public/locales/ko-KR/image_editor.json`、`public/locales/ko-KR/markdown_editor.json`

**public/locales/pl-PL/…**

`public/locales/pl-PL/application.json`、`public/locales/pl-PL/common.json`、`public/locales/pl-PL/dashboard.json`、`public/locales/pl-PL/image_editor.json`、`public/locales/pl-PL/markdown_editor.json`

**public/locales/pt-BR/…**

`public/locales/pt-BR/application.json`、`public/locales/pt-BR/common.json`、`public/locales/pt-BR/dashboard.json`、`public/locales/pt-BR/image_editor.json`、`public/locales/pt-BR/markdown_editor.json`

**public/locales/ru-RU/…**

`public/locales/ru-RU/application.json`、`public/locales/ru-RU/common.json`、`public/locales/ru-RU/dashboard.json`、`public/locales/ru-RU/image_editor.json`、`public/locales/ru-RU/markdown_editor.json`

**public/locales/zh-CN/…**

`public/locales/zh-CN/application.json`、`public/locales/zh-CN/common.json`、`public/locales/zh-CN/dashboard.json`、`public/locales/zh-CN/image_editor.json`、`public/locales/zh-CN/markdown_editor.json`

**public/locales/zh-TW/…**

`public/locales/zh-TW/application.json`、`public/locales/zh-TW/common.json`、`public/locales/zh-TW/dashboard.json`、`public/locales/zh-TW/image_editor.json`、`public/locales/zh-TW/markdown_editor.json`

**public/pdfviewer.html**

`public/pdfviewer.html`

**public/service-worker.js**

`public/service-worker.js`

**public/static/img/…**

`public/static/img/appstore.svg`、`public/static/img/cloudreve.svg`、`public/static/img/cloudreve_ios.svg`、`public/static/img/cos.png`、`public/static/img/favicon.ico`、`public/static/img/ks3.png`、`public/static/img/lb.svg`、`public/static/img/local.png`、`public/static/img/logo.svg`、`public/static/img/logo192.png`、`public/static/img/logo512.png`、`public/static/img/logo_light.svg`、`public/static/img/marker-icon-2x.png`、`public/static/img/marker-icon.png`、`public/static/img/marker-shadow.png`、`public/static/img/obs.png`、`public/static/img/onedrive.png`、`public/static/img/oss.png`、`public/static/img/qiniu.png`、`public/static/img/remote.png`、`public/static/img/s3.png`、`public/static/img/upyun.png`、`public/static/img/viewers/artplayer.png`、`public/static/img/viewers/drawio.svg`、`public/static/img/viewers/excalidraw.svg`、`public/static/img/viewers/gdrive.png`、`public/static/img/viewers/m365.svg`、`public/static/img/viewers/monaco.svg`、`public/static/img/viewers/photopea.png`

</details>

## 三、目标仓库 web/packages/components 现有盘点

### 组件目录（55）

`accordion`、`alert`、`animate-height`、`avatar`、`badge`、`breadcrumbs`、`button`、`captcha`、`card`、`chart`、`checkbox`、`code`、`confirm-dialog`、`date-picker`、`description-list`、`dialog`、`dropdown-menu`、`empty-state`、`error-detail-dialog`、`error-state`、`file-drop-zone`、`file-type-icon`、`form-field`、`graph`、`highlight-text`、`icon`、`image-cropper`、`input`、`language-switcher`、`logo`、`motion`、`oauth-app-card`、`otp-input`、`pagination`、`powered-by`、`progress`、`progress-steps`、`relative-time`、`responsive-tabs`、`search-input`、`select`、`select-dialog`、`settings-panel`、`size-input`、`skeleton`、`split-pane`、`stat-card`、`switch`、`table`、`tabs`、`theme-switch`、`toast`、`tooltip`、`tree-view`、`user`

### 布局目录（4）

`app-shell`、`page-container`、`page-layout`、`sidebar-layout`

### hooks 目录（0）

（本批次新增）

### shared utils（12）

`async.ts`、`base64.ts`、`boolset.ts`、`classnames.ts`、`date.ts`、`datetime.ts`、`event-bus.ts`、`filesystem.ts`、`format.ts`、`lru.ts`、`object.ts`、`storage.ts`

## 四、目标应用页面/模块盘点

| 应用 | 模块 |
| --- | --- |
| auth | `legal`、`login`、`recovery` |
| setup | `setup` |
| uc | `authorizations`、`profile`、`security` |
| console | `audit`、`overview`、`roles`、`settings`、`users` |

## 五、缺口与合并分析（人工结论）

### 5.1 已有能力（复用）

- 主题：PudansThemeProvider / createPudansTheme / tokens.ts / variables.css 已提供亮暗主题、品牌色注入、MUI 桥接。
- 布局：AppShell / SidebarLayout / PageLayout / PageContainer 覆盖旧 Frame/Pages 的页框需求。
- 基础组件：Button/Input/Select/Dialog/DropdownMenu/Card/Tabs/Table/Badge/Alert/Toast/Skeleton/Avatar/Tooltip/Checkbox/Switch/Pagination/DatePicker/TreeView/Chart/Graph 等。

### 5.2 缺失（本批迁移/封装）

- 状态类：EmptyState（旧 Nothing/EmptyFileList/NoMatch）、ErrorState/ErrorBoundary（旧 ErrorBoundary）、Progress（旧 Linear/Circular）、ProgressSteps（旧 StepProgressBar）。
- 反馈类：ConfirmDialog/useConfirm（旧 Confirmation）、SelectDialog/useSelectOption（旧 SelectOption）、ErrorDetailDialog（旧 AggregatedErrorDetail）、Toast 内容变体。
- 导航/交互：Breadcrumbs（旧 Breadcrumb 三件套）、ResponsiveTabs、ThemeSwitch、SearchInput、SplitPane、Accordion（旧 DialogAccordion）、Draggable Dialog。
- 数据展示：DescriptionList（旧 InfoRow/Details/Data）、RelativeTime（旧 TimeBadge）、FileTypeIcon（旧 FileTypeIcon/FileIcon/FileSmallIcon）、SizeInput。
- 品牌/身份：Logo、PoweredBy、LanguageSwitcher、UserBadge、UserPopover、OAuthAppCard。
- 表单/布局：FormField/FormSection/SettingItem（旧 StyledFormControl/SettingListItem）。
- 动效：Fade/Slide/AnimatedSwap + motion token（旧 FadeTransition/NavIconTransition）。
- 上传：FileDropZone（旧 DropFile）。
- 图标：251 个 cloudreve SvgIcon -> cr: offline Iconify 集合。
- hooks：useDelayedHover / useOverflow / useMediaSession。
- shared utils：base64 / boolset / datetime / filesystem / lru。

### 5.3 可合并（重复实现合并）

- 旧 Captcha 5 个供应商包装 -> 1 个 Captcha；旧 Progress 2 个 -> 1 个；旧 Snackbar 3 件套 -> Toast；旧 Breadcrumb 3 件套 -> Breadcrumbs；旧 Pagination 2 件套 -> Pagination；旧 Dialog 变体（Confirmation/SelectOption/ErrorDetail）-> Dialog + 专用封装。

### 5.4 API 不兼容（需要适配说明）

| 旧 API | 新 API | 迁移说明 |
| --- | --- | --- |
| Nothing(primary, secondary, top, size) | EmptyState(title, description, icon, action, variant) | 文案字段改名；size -> sm/md/lg；新增语义 variant。 |
| DraggableDialog(dialogProps, showActions...) | Dialog(draggable, footer, ...) | 动作区改由 footer 传入；确认语义用 ConfirmDialog。 |
| Confirmation（redux promise） | useConfirm()（Provider promise） | 移除全局 redux 依赖；在 App Providers 内挂载 ConfirmProvider。 |
| SelectOption（redux） | useSelectOption() | 同上。 |
| TimeBadge(datetime, timeAgoThreshold) | RelativeTime(datetime, threshold, tooltip) | 改用 Intl.RelativeTimeFormat；tooltip 显示完整本地时间。 |
| ResponsiveTabs(tabs, value, onChange) | ResponsiveTabs(items, value, onChange) | 字段 label/value/icon 不变；溢出策略内置。 |
| DarkThemeSwitcher（redux 布尔） | ThemeSwitch（light/system/dark） | 直接消费 usePudansTheme()。 |
| BorderLinearProgress | Progress variant=linear | tone/size/value 标准化。 |
| SizeInput(onChange, value, min/max) | SizeInput（同名） | 取消 redux，单位换算纯函数化。 |
| SearchBar（全局搜索） | SearchInput | 去掉全局搜索状态，受控 value/onChange。 |
| SplitHandle（redux drawer） | SplitPane | 改为受控尺寸 + storageKey 持久化。 |

## 六、页面/路由映射

| 旧页面 | 目标应用/路由 | 状态 | 说明 |
| --- | --- | --- | --- |
| Pages/Login/* | web/app/auth -> /auth/ | 已迁移（部分待补） | 密码/OTP/找回已落地；passkey、激活、OAuth 授权登记待补。 |
| Pages/Setting/* | web/app/uc -> /uc/profile、/uc/security | 已迁移（部分待补） | 资料/安全/授权已落地；存储配额/偏好/passkey 列表登记待补。 |
| Admin/Home | web/app/console -> /console/ overview | 已迁移 | 概览卡片与布局已适配。 |
| Admin/Settings/* | /console/settings | 已迁移（子集） | appearance/captcha/mail/events/queue/server/site/user 已落地；media/VAS/SSO 等登记。 |
| Admin/User/* | /console/users | 部分迁移 | 列表+基础操作；高级用户对话框登记。 |
| Admin/Group/* | /console/usergroups（占位） | 暂缓 | 目标 RBAC/用户组模型未定。 |
| Admin/{Entity,File,FileSystem,Node,OAuthClient,Share,StoragePolicy,Task} | — | 暂缓 | 目标产品暂无对应管理面（后端领域不同）。 |
| FileManager/* | — | 暂缓（原子已抽） | 目标尚无文件管理应用；原子组件（图标/面包屑/空状态/描述列表/分页/右键菜单/确认框）已进公共库。 |
| Viewers/* | — | 暂缓 | 目标无文件预览应用；依赖清单已登记。 |
| Uploader/* | — | 暂缓（DropFile 已抽） | 上传协议与 cloudreve 存储强绑定。 |
| Pages/{Shares,Tasks,Devices} | — | 暂缓 | 目标无对应产品面。 |
| Pages/NoMatch | 公共库 EmptyState(not-found) | 已迁移 | 任意应用可复用。 |

## 七、设计变量（token）清单

> 完整定义见 `web/packages/components/src/styles/tokens.ts`、`variables.css`、`create-theme.ts`；规范说明见 `.dsh/migration/design-system.md`。

| 组 | token 前缀 | 覆盖 |
| --- | --- | --- |
| 品牌/语义色 | `--pd-brand-*, --pd-bg-*, --pd-text-*, --pd-border-*, --pd-state-*` | 亮/暗双主题、accent 注入 |
| 字体 | `--pd-font-*, --pd-text-*, --pd-leading-*, --pd-font-weight-*` | Roboto + mono，12-30px 字号 |
| 间距 | `--pd-space-*` | 4px 网格 |
| 圆角/阴影 | `--pd-radius-*, --pd-shadow-*` | 4-9999px / 5 级高度 |
| 动效 | `--pd-duration-*, --pd-ease-*, --pd-transition-*` | 150-375ms、标准/进出/强调曲线、reduced-motion |
| 状态 | `--pd-focus-ring-*, --pd-opacity-disabled, --pd-hover-*` | focus/hover/active/disabled/loading |
| 图表 | `--pd-chart-1…6` | 亮/暗双套 |

## 八、图标迁移

| 项 | 值 |
| --- | --- |
| 源图标文件 | 251 |
| 目标集合 | `web/packages/components/src/components/icon/cloudreve-icons.ts`（Iconify collection prefix `cr`） |
| 映射清单 | `.dsh/migration/icon-map.md` / `.dsh/migration/data/cloudreve-icons.json` |
| 使用方式 | `<Icon name="cr:Search" />`（离线注册，无网络请求） |

## 九、阻断与待办汇总

见 `.dsh/migration/report.md` 的「未完成项及阻断原因」章节；所有暂缓项均已在上表给出 reason，可在后续批次按目录整体启动。


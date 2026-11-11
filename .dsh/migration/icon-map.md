# Cloudreve legacy icon migration map

Source: `/Users/byte/Global/Reference/cloudreve/frontend/src/component/Icons/*.tsx` (251 `.tsx` files, `.DS_Store` ignored).
Target: `web/packages/components/src/components/icon/cloudreve-icons.ts` - offline Iconify collection with prefix `cr`.
Manifest: `.dsh/migration/data/cloudreve-icons.json`.

## Summary

- **Counts**: 251 source files -> 251 icons -> 251 `cr:` names. 0 duplicates, 0 untranslatable icons. Every file in the source directory is a single icon component: there are no helper or barrel files (`Icons.tsx` is itself an icon, `cr:Icons`).
- **Wrappers**: 213 use `<SvgIcon>` and 38 use `createSvgIcon(...)`; both default to a `0 0 24 24` viewBox.
- **Collection**: `prefix: "cr"`, `width: 24`, `height: 24`; exported as `cloudreveIconCollection` and registered offline via the idempotent `registerCloudreveIcons()`, which runs on module import.
- **Markup shapes**: 243 icons are a single `<path>`; 5 combine two or more `<path>` elements (Document, DocumentPDF, FolderZip, ImageOffOutlined, Password); 1 is a `<path>` plus a stroked `<line>` (ArrowRepeatOff); 2 wrap their paths in a root `<g>` (Border, Copyright).
- **View boxes**: 6 icons declare an explicit viewBox; 5 differ from 24x24 and carry per-icon width/height (Markdown 15x15, MusicNote2Play 20x20, QQ 1024x1024, TaskList 20x20, TaskListOutlined 20x20). MusicNote2 declares the 24x24 default explicitly.
- **Fill semantics**: MUI `<SvgIcon>` applies `fill: currentColor` at the SVG root, so 245 bodies receive an injected `fill="currentColor"` on their top-level unfilled shape(s). The 6 icons that declare their own fill keep it untouched (Border, BorderInside, Cart, CoinStack, CopperCoin, PackageOpen). No icon contains a hardcoded palette color, so all remain themeable through the Icon component color prop.
- **Normalization**: React camelCase SVG attributes converted to kebab-case in 1 icon(s) (ArrowRepeatOff: strokeWidth -> stroke-width, strokeLinecap -> stroke-linecap); inert `color="currentColor"` prop dropped in PackageOpen; trailing JSX whitespace expression removed in ShieldLockFilled; stray comma text nodes removed in MusicNote2, MusicNote2Play; one explicit `<path></path>` pair normalized to self-closing (QQ). `fill-rule` values are preserved in 7 icon(s): Copyright, Currency, Enter, SendLogging, SendLoggingFilled, Upload, UploadFilled.
- **Lost or approximated**: none. No artwork was invented, removed, or redrawn; only wrapper markup was dropped.

## Outliers

| Component | Treatment |
| --- | --- |
| Markdown | explicit `viewBox="0 0 15 15"` -> per-icon 15x15 |
| MusicNote2 | explicit `viewBox="0 0 24 24"` (matches the 24x24 default); dropped stray comma text node |
| MusicNote2Play | explicit `viewBox="0 0 20 20"` -> per-icon 20x20; dropped stray comma text node |
| QQ | explicit `viewBox="0 0 1024 1024"` -> per-icon 1024x1024; `<path></path>` normalized to a self-closing tag |
| TaskList | explicit `viewBox="0 0 20 20"` -> per-icon 20x20 |
| TaskListOutlined | explicit `viewBox="0 0 20 20"` -> per-icon 20x20 |
| Border | root `<g fill="none">` with 8 `fill="currentColor"` paths |
| Copyright | `createSvgIcon` wrapper (24x24 default); root `<g fill-rule="evenodd">` with 2 paths |
| ArrowRepeatOff | `<path>` plus stroked `<line>`; `strokeWidth -> stroke-width`, `strokeLinecap -> stroke-linecap` normalized to kebab-case |
| PackageOpen | stroke-only glyph (`fill="none"`, `stroke="currentColor"`); dropped inert `color="currentColor"` prop |
| ShieldLockFilled | dropped trailing JSX whitespace expression |

## Full mapping

| Component | Icon name | Source path | Notes |
| --- | --- | --- | --- |
| Add | `cr:Add` | `src/component/Icons/Add.tsx` | — |
| AppFolder | `cr:AppFolder` | `src/component/Icons/AppFolder.tsx` | — |
| AppGeneric | `cr:AppGeneric` | `src/component/Icons/AppGeneric.tsx` | — |
| AppsList | `cr:AppsList` | `src/component/Icons/AppsList.tsx` | — |
| AppsListOutlined | `cr:AppsListOutlined` | `src/component/Icons/AppsListOutlined.tsx` | — |
| Archive | `cr:Archive` | `src/component/Icons/Archive.tsx` | — |
| ArchiveArrow | `cr:ArchiveArrow` | `src/component/Icons/ArchiveArrow.tsx` | — |
| ArrowClockwise | `cr:ArrowClockwise` | `src/component/Icons/ArrowClockwise.tsx` | — |
| ArrowClockwiseFilled | `cr:ArrowClockwiseFilled` | `src/component/Icons/ArrowClockwiseFilled.tsx` | — |
| ArrowDown | `cr:ArrowDown` | `src/component/Icons/ArrowDown.tsx` | — |
| ArrowHookDownLeft | `cr:ArrowHookDownLeft` | `src/component/Icons/ArrowHookDownLeft.tsx` | — |
| ArrowHookUpRight | `cr:ArrowHookUpRight` | `src/component/Icons/ArrowHookUpRight.tsx` | — |
| ArrowImport | `cr:ArrowImport` | `src/component/Icons/ArrowImport.tsx` | — |
| ArrowLeft | `cr:ArrowLeft` | `src/component/Icons/ArrowLeft.tsx` | `createSvgIcon` wrapper (24x24 default) |
| ArrowRepeatAll | `cr:ArrowRepeatAll` | `src/component/Icons/ArrowRepeatAll.tsx` | — |
| ArrowRepeatOff | `cr:ArrowRepeatOff` | `src/component/Icons/ArrowRepeatOff.tsx` | `<path>` plus stroked `<line>`; `strokeWidth -> stroke-width`, `strokeLinecap -> stroke-linecap` normalized to kebab-case |
| ArrowRepeatOne | `cr:ArrowRepeatOne` | `src/component/Icons/ArrowRepeatOne.tsx` | — |
| ArrowShuffle | `cr:ArrowShuffle` | `src/component/Icons/ArrowShuffle.tsx` | — |
| ArrowSort | `cr:ArrowSort` | `src/component/Icons/ArrowSort.tsx` | — |
| ArrowSortDownFilled | `cr:ArrowSortDownFilled` | `src/component/Icons/ArrowSortDownFilled.tsx` | — |
| ArrowSync | `cr:ArrowSync` | `src/component/Icons/ArrowSync.tsx` | — |
| ArrowSyncCircleFilled | `cr:ArrowSyncCircleFilled` | `src/component/Icons/ArrowSyncCircleFilled.tsx` | — |
| BinFullOutlined | `cr:BinFullOutlined` | `src/component/Icons/BinFullOutlined.tsx` | — |
| Book | `cr:Book` | `src/component/Icons/Book.tsx` | — |
| BookInformation | `cr:BookInformation` | `src/component/Icons/BookInformation.tsx` | — |
| Border | `cr:Border` | `src/component/Icons/Border.tsx` | root `<g fill="none">` with 8 `fill="currentColor"` paths |
| BorderAll | `cr:BorderAll` | `src/component/Icons/BorderAll.tsx` | — |
| BorderInside | `cr:BorderInside` | `src/component/Icons/BorderInside.tsx` | explicit `fill="currentColor"` preserved |
| Bot | `cr:Bot` | `src/component/Icons/Bot.tsx` | — |
| BoxMultiple | `cr:BoxMultiple` | `src/component/Icons/BoxMultiple.tsx` | — |
| BoxMultipleFilled | `cr:BoxMultipleFilled` | `src/component/Icons/BoxMultipleFilled.tsx` | — |
| BranchCompare | `cr:BranchCompare` | `src/component/Icons/BranchCompare.tsx` | — |
| BranchForkLink | `cr:BranchForkLink` | `src/component/Icons/BranchForkLink.tsx` | — |
| Broom | `cr:Broom` | `src/component/Icons/Broom.tsx` | `createSvgIcon` wrapper (24x24 default) |
| BuildingShop | `cr:BuildingShop` | `src/component/Icons/BuildingShop.tsx` | — |
| BuildingShopFilled | `cr:BuildingShopFilled` | `src/component/Icons/BuildingShopFilled.tsx` | — |
| CalendarClock | `cr:CalendarClock` | `src/component/Icons/CalendarClock.tsx` | — |
| CameraFilled | `cr:CameraFilled` | `src/component/Icons/CameraFilled.tsx` | `createSvgIcon` wrapper (24x24 default) |
| CameraRounded | `cr:CameraRounded` | `src/component/Icons/CameraRounded.tsx` | `createSvgIcon` wrapper (24x24 default) |
| CaretDown | `cr:CaretDown` | `src/component/Icons/CaretDown.tsx` | — |
| CaretRight | `cr:CaretRight` | `src/component/Icons/CaretRight.tsx` | — |
| Cart | `cr:Cart` | `src/component/Icons/Cart.tsx` | explicit `fill="currentColor"` preserved |
| CheckCircleFilled | `cr:CheckCircleFilled` | `src/component/Icons/CheckCircleFilled.tsx` | — |
| CheckUnchecked | `cr:CheckUnchecked` | `src/component/Icons/CheckUnchecked.tsx` | — |
| CheckboxChecked | `cr:CheckboxChecked` | `src/component/Icons/CheckboxChecked.tsx` | — |
| Checkmark | `cr:Checkmark` | `src/component/Icons/Checkmark.tsx` | — |
| CheckmarkCircle | `cr:CheckmarkCircle` | `src/component/Icons/CheckmarkCircle.tsx` | — |
| CheckmarkCircleFilled | `cr:CheckmarkCircleFilled` | `src/component/Icons/CheckmarkCircleFilled.tsx` | — |
| ChevronRight | `cr:ChevronRight` | `src/component/Icons/ChevronRight.tsx` | — |
| CircleHintFilled | `cr:CircleHintFilled` | `src/component/Icons/CircleHintFilled.tsx` | — |
| Clipboard | `cr:Clipboard` | `src/component/Icons/Clipboard.tsx` | `createSvgIcon` wrapper (24x24 default) |
| ClockArrowDownload | `cr:ClockArrowDownload` | `src/component/Icons/ClockArrowDownload.tsx` | `createSvgIcon` wrapper (24x24 default) |
| ClockFilled | `cr:ClockFilled` | `src/component/Icons/ClockFilled.tsx` | `createSvgIcon` wrapper (24x24 default) |
| CloudArrowIUp | `cr:CloudArrowIUp` | `src/component/Icons/CloudArrowIUp.tsx` | — |
| CloudDownload | `cr:CloudDownload` | `src/component/Icons/CloudDownload.tsx` | — |
| CloudDownloadOutlined | `cr:CloudDownloadOutlined` | `src/component/Icons/CloudDownloadOutlined.tsx` | — |
| CloudFilled | `cr:CloudFilled` | `src/component/Icons/CloudFilled.tsx` | — |
| CloudOff | `cr:CloudOff` | `src/component/Icons/CloudOff.tsx` | — |
| CoinStack | `cr:CoinStack` | `src/component/Icons/CoinStack.tsx` | explicit `fill="currentColor"` preserved |
| Color | `cr:Color` | `src/component/Icons/Color.tsx` | — |
| CommentMultiple | `cr:CommentMultiple` | `src/component/Icons/CommentMultiple.tsx` | — |
| CopperCoin | `cr:CopperCoin` | `src/component/Icons/CopperCoin.tsx` | explicit `fill="currentColor"` preserved |
| CopyOutlined | `cr:CopyOutlined` | `src/component/Icons/CopyOutlined.tsx` | — |
| Copyright | `cr:Copyright` | `src/component/Icons/Copyright.tsx` | `createSvgIcon` wrapper (24x24 default); root `<g fill-rule="evenodd">` with 2 paths |
| CubeSync | `cr:CubeSync` | `src/component/Icons/CubeSync.tsx` | — |
| CubeSyncFilled | `cr:CubeSyncFilled` | `src/component/Icons/CubeSyncFilled.tsx` | — |
| CubeTree | `cr:CubeTree` | `src/component/Icons/CubeTree.tsx` | — |
| CubeTreeFilled | `cr:CubeTreeFilled` | `src/component/Icons/CubeTreeFilled.tsx` | — |
| Currency | `cr:Currency` | `src/component/Icons/Currency.tsx` | preserves `fill-rule="nonzero"` |
| DarkTheme | `cr:DarkTheme` | `src/component/Icons/DarkTheme.tsx` | — |
| DataBarVerticalStar | `cr:DataBarVerticalStar` | `src/component/Icons/DataBarVerticalStar.tsx` | — |
| DataHistogram | `cr:DataHistogram` | `src/component/Icons/DataHistogram.tsx` | — |
| DataHistogramFilled | `cr:DataHistogramFilled` | `src/component/Icons/DataHistogramFilled.tsx` | — |
| Delete | `cr:Delete` | `src/component/Icons/Delete.tsx` | — |
| DeleteOutlined | `cr:DeleteOutlined` | `src/component/Icons/DeleteOutlined.tsx` | — |
| DesktopFlow | `cr:DesktopFlow` | `src/component/Icons/DesktopFlow.tsx` | — |
| Discord | `cr:Discord` | `src/component/Icons/Discord.tsx` | — |
| Dismiss | `cr:Dismiss` | `src/component/Icons/Dismiss.tsx` | — |
| DismissCircleFilled | `cr:DismissCircleFilled` | `src/component/Icons/DismissCircleFilled.tsx` | — |
| Divider | `cr:Divider` | `src/component/Icons/Divider.tsx` | — |
| Document | `cr:Document` | `src/component/Icons/Document.tsx` | — |
| DocumentArrowDownFilled | `cr:DocumentArrowDownFilled` | `src/component/Icons/DocumentArrowDownFilled.tsx` | — |
| DocumentCopyFilled | `cr:DocumentCopyFilled` | `src/component/Icons/DocumentCopyFilled.tsx` | — |
| DocumentDataLink | `cr:DocumentDataLink` | `src/component/Icons/DocumentDataLink.tsx` | — |
| DocumentFlowchart | `cr:DocumentFlowchart` | `src/component/Icons/DocumentFlowchart.tsx` | `createSvgIcon` wrapper (24x24 default) |
| DocumentPDF | `cr:DocumentPDF` | `src/component/Icons/DocumentPDF.tsx` | — |
| DocumentText | `cr:DocumentText` | `src/component/Icons/DocumentText.tsx` | — |
| DocumentTextOutlined | `cr:DocumentTextOutlined` | `src/component/Icons/DocumentTextOutlined.tsx` | — |
| Download | `cr:Download` | `src/component/Icons/Download.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Earth | `cr:Earth` | `src/component/Icons/Earth.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Edit | `cr:Edit` | `src/component/Icons/Edit.tsx` | — |
| EditSetting | `cr:EditSetting` | `src/component/Icons/EditSetting.tsx` | — |
| EmailClock | `cr:EmailClock` | `src/component/Icons/EmailClock.tsx` | — |
| EmojiEdit | `cr:EmojiEdit` | `src/component/Icons/EmojiEdit.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Enter | `cr:Enter` | `src/component/Icons/Enter.tsx` | preserves `fill-rule="nonzero"` |
| Eye | `cr:Eye` | `src/component/Icons/Eye.tsx` | `createSvgIcon` wrapper (24x24 default) |
| EyeOff | `cr:EyeOff` | `src/component/Icons/EyeOff.tsx` | — |
| FastForward | `cr:FastForward` | `src/component/Icons/FastForward.tsx` | — |
| FileAdd | `cr:FileAdd` | `src/component/Icons/FileAdd.tsx` | — |
| FileExclBox | `cr:FileExclBox` | `src/component/Icons/FileExclBox.tsx` | — |
| FilePowerPointBox | `cr:FilePowerPointBox` | `src/component/Icons/FilePowerPointBox.tsx` | — |
| FileWordBox | `cr:FileWordBox` | `src/component/Icons/FileWordBox.tsx` | — |
| FilmstripImage | `cr:FilmstripImage` | `src/component/Icons/FilmstripImage.tsx` | — |
| Filter | `cr:Filter` | `src/component/Icons/Filter.tsx` | — |
| Fingerprint | `cr:Fingerprint` | `src/component/Icons/Fingerprint.tsx` | — |
| Folder | `cr:Folder` | `src/component/Icons/Folder.tsx` | — |
| FolderAdd | `cr:FolderAdd` | `src/component/Icons/FolderAdd.tsx` | — |
| FolderArrowRightOutlined | `cr:FolderArrowRightOutlined` | `src/component/Icons/FolderArrowRightOutlined.tsx` | — |
| FolderArrowUp | `cr:FolderArrowUp` | `src/component/Icons/FolderArrowUp.tsx` | — |
| FolderLink | `cr:FolderLink` | `src/component/Icons/FolderLink.tsx` | `createSvgIcon` wrapper (24x24 default) |
| FolderOutlined | `cr:FolderOutlined` | `src/component/Icons/FolderOutlined.tsx` | — |
| FolderZip | `cr:FolderZip` | `src/component/Icons/FolderZip.tsx` | — |
| FullScreenMaximize | `cr:FullScreenMaximize` | `src/component/Icons/FullScreenMaximize.tsx` | `createSvgIcon` wrapper (24x24 default) |
| FullScreenMinimize | `cr:FullScreenMinimize` | `src/component/Icons/FullScreenMinimize.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Gift | `cr:Gift` | `src/component/Icons/Gift.tsx` | — |
| Globe | `cr:Globe` | `src/component/Icons/Globe.tsx` | — |
| GlobeFilled | `cr:GlobeFilled` | `src/component/Icons/GlobeFilled.tsx` | — |
| Grid | `cr:Grid` | `src/component/Icons/Grid.tsx` | — |
| GridOutlined | `cr:GridOutlined` | `src/component/Icons/GridOutlined.tsx` | — |
| HardDrive | `cr:HardDrive` | `src/component/Icons/HardDrive.tsx` | — |
| HardDriveOutlined | `cr:HardDriveOutlined` | `src/component/Icons/HardDriveOutlined.tsx` | — |
| HistoryOutlined | `cr:HistoryOutlined` | `src/component/Icons/HistoryOutlined.tsx` | — |
| Home | `cr:Home` | `src/component/Icons/Home.tsx` | — |
| HomeOutlined | `cr:HomeOutlined` | `src/component/Icons/HomeOutlined.tsx` | — |
| Icons | `cr:Icons` | `src/component/Icons/Icons.tsx` | — |
| Image | `cr:Image` | `src/component/Icons/Image.tsx` | — |
| ImageAarowCounterclockwise | `cr:ImageAarowCounterclockwise` | `src/component/Icons/ImageAarowCounterclockwise.tsx` | — |
| ImageCopy | `cr:ImageCopy` | `src/component/Icons/ImageCopy.tsx` | — |
| ImageCopyOutlined | `cr:ImageCopyOutlined` | `src/component/Icons/ImageCopyOutlined.tsx` | — |
| ImageEdit | `cr:ImageEdit` | `src/component/Icons/ImageEdit.tsx` | `createSvgIcon` wrapper (24x24 default) |
| ImageOffOutlined | `cr:ImageOffOutlined` | `src/component/Icons/ImageOffOutlined.tsx` | — |
| ImageOutlined | `cr:ImageOutlined` | `src/component/Icons/ImageOutlined.tsx` | — |
| InPrivate | `cr:InPrivate` | `src/component/Icons/InPrivate.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Info | `cr:Info` | `src/component/Icons/Info.tsx` | `createSvgIcon` wrapper (24x24 default) |
| InfoFilled | `cr:InfoFilled` | `src/component/Icons/InfoFilled.tsx` | `createSvgIcon` wrapper (24x24 default) |
| IosArrow | `cr:IosArrow` | `src/component/Icons/IosArrow.tsx` | — |
| IosArrowLeft | `cr:IosArrowLeft` | `src/component/Icons/IosArrowLeft.tsx` | — |
| LanguageC | `cr:LanguageC` | `src/component/Icons/LanguageC.tsx` | — |
| LanguageCPP | `cr:LanguageCPP` | `src/component/Icons/LanguageCPP.tsx` | — |
| LanguageGo | `cr:LanguageGo` | `src/component/Icons/LanguageGo.tsx` | — |
| LanguageJS | `cr:LanguageJS` | `src/component/Icons/LanguageJS.tsx` | — |
| LanguagePHP | `cr:LanguagePHP` | `src/component/Icons/LanguagePHP.tsx` | — |
| LanguagePython | `cr:LanguagePython` | `src/component/Icons/LanguagePython.tsx` | — |
| LanguageRust | `cr:LanguageRust` | `src/component/Icons/LanguageRust.tsx` | — |
| Link | `cr:Link` | `src/component/Icons/Link.tsx` | — |
| LinkDismiss | `cr:LinkDismiss` | `src/component/Icons/LinkDismiss.tsx` | `createSvgIcon` wrapper (24x24 default) |
| LinkEdit | `cr:LinkEdit` | `src/component/Icons/LinkEdit.tsx` | — |
| LinkOutlined | `cr:LinkOutlined` | `src/component/Icons/LinkOutlined.tsx` | — |
| LinkSetting | `cr:LinkSetting` | `src/component/Icons/LinkSetting.tsx` | `createSvgIcon` wrapper (24x24 default) |
| LocationFilled | `cr:LocationFilled` | `src/component/Icons/LocationFilled.tsx` | — |
| LockClosed | `cr:LockClosed` | `src/component/Icons/LockClosed.tsx` | — |
| LockClosedKey | `cr:LockClosedKey` | `src/component/Icons/LockClosedKey.tsx` | `createSvgIcon` wrapper (24x24 default) |
| LockClosedOutlined | `cr:LockClosedOutlined` | `src/component/Icons/LockClosedOutlined.tsx` | — |
| MagnetOn | `cr:MagnetOn` | `src/component/Icons/MagnetOn.tsx` | — |
| MailOutlined | `cr:MailOutlined` | `src/component/Icons/MailOutlined.tsx` | — |
| Markdown | `cr:Markdown` | `src/component/Icons/Markdown.tsx` | explicit `viewBox="0 0 15 15"` -> per-icon 15x15 |
| Money | `cr:Money` | `src/component/Icons/Money.tsx` | — |
| Moon | `cr:Moon` | `src/component/Icons/Moon.tsx` | — |
| MoreHorizontal | `cr:MoreHorizontal` | `src/component/Icons/MoreHorizontal.tsx` | — |
| MoreVertical | `cr:MoreVertical` | `src/component/Icons/MoreVertical.tsx` | `createSvgIcon` wrapper (24x24 default) |
| MusicNote1 | `cr:MusicNote1` | `src/component/Icons/MusicNote1.tsx` | — |
| MusicNote1Outlined | `cr:MusicNote1Outlined` | `src/component/Icons/MusicNote1Outlined.tsx` | — |
| MusicNote2 | `cr:MusicNote2` | `src/component/Icons/MusicNote2.tsx` | explicit `viewBox="0 0 24 24"` (matches the 24x24 default); dropped stray comma text node |
| MusicNote2Play | `cr:MusicNote2Play` | `src/component/Icons/MusicNote2Play.tsx` | explicit `viewBox="0 0 20 20"` -> per-icon 20x20; dropped stray comma text node |
| Notepad | `cr:Notepad` | `src/component/Icons/Notepad.tsx` | — |
| Numbers | `cr:Numbers` | `src/component/Icons/Numbers.tsx` | — |
| Open | `cr:Open` | `src/component/Icons/Open.tsx` | `createSvgIcon` wrapper (24x24 default) |
| OpenFilled | `cr:OpenFilled` | `src/component/Icons/OpenFilled.tsx` | — |
| Options | `cr:Options` | `src/component/Icons/Options.tsx` | — |
| PackageOpen | `cr:PackageOpen` | `src/component/Icons/PackageOpen.tsx` | stroke-only glyph (`fill="none"`, `stroke="currentColor"`); dropped inert `color="currentColor"` prop |
| Password | `cr:Password` | `src/component/Icons/Password.tsx` | — |
| Pause | `cr:Pause` | `src/component/Icons/Pause.tsx` | — |
| Payment | `cr:Payment` | `src/component/Icons/Payment.tsx` | — |
| PaymentFilled | `cr:PaymentFilled` | `src/component/Icons/PaymentFilled.tsx` | — |
| People | `cr:People` | `src/component/Icons/People.tsx` | `createSvgIcon` wrapper (24x24 default) |
| PeopleFilled | `cr:PeopleFilled` | `src/component/Icons/PeopleFilled.tsx` | `createSvgIcon` wrapper (24x24 default) |
| PeopleStar | `cr:PeopleStar` | `src/component/Icons/PeopleStar.tsx` | `createSvgIcon` wrapper (24x24 default) |
| PeopleTeam | `cr:PeopleTeam` | `src/component/Icons/PeopleTeam.tsx` | — |
| PeopleTeamOutlined | `cr:PeopleTeamOutlined` | `src/component/Icons/PeopleTeamOutlined.tsx` | — |
| Person | `cr:Person` | `src/component/Icons/Person.tsx` | — |
| PersonLock | `cr:PersonLock` | `src/component/Icons/PersonLock.tsx` | `createSvgIcon` wrapper (24x24 default) |
| PersonOutlined | `cr:PersonOutlined` | `src/component/Icons/PersonOutlined.tsx` | — |
| PersonPasskey | `cr:PersonPasskey` | `src/component/Icons/PersonPasskey.tsx` | — |
| PersonStar | `cr:PersonStar` | `src/component/Icons/PersonStar.tsx` | — |
| PhoneLaptop | `cr:PhoneLaptop` | `src/component/Icons/PhoneLaptop.tsx` | — |
| PhoneLaptopOutlined | `cr:PhoneLaptopOutlined` | `src/component/Icons/PhoneLaptopOutlined.tsx` | — |
| PinOutlined | `cr:PinOutlined` | `src/component/Icons/PinOutlined.tsx` | — |
| Play | `cr:Play` | `src/component/Icons/Play.tsx` | — |
| Pulse | `cr:Pulse` | `src/component/Icons/Pulse.tsx` | `createSvgIcon` wrapper (24x24 default) |
| QQ | `cr:QQ` | `src/component/Icons/QQ.tsx` | explicit `viewBox="0 0 1024 1024"` -> per-icon 1024x1024; `<path></path>` normalized to a self-closing tag |
| QuestionCircle | `cr:QuestionCircle` | `src/component/Icons/QuestionCircle.tsx` | — |
| Raw | `cr:Raw` | `src/component/Icons/Raw.tsx` | — |
| RectangleLandscapeSync | `cr:RectangleLandscapeSync` | `src/component/Icons/RectangleLandscapeSync.tsx` | — |
| RectangleLandscapeSyncOff | `cr:RectangleLandscapeSyncOff` | `src/component/Icons/RectangleLandscapeSyncOff.tsx` | — |
| RenameFilled | `cr:RenameFilled` | `src/component/Icons/RenameFilled.tsx` | — |
| RenameOutlined | `cr:RenameOutlined` | `src/component/Icons/RenameOutlined.tsx` | — |
| Save | `cr:Save` | `src/component/Icons/Save.tsx` | — |
| Savings | `cr:Savings` | `src/component/Icons/Savings.tsx` | — |
| Search | `cr:Search` | `src/component/Icons/Search.tsx` | — |
| SendLogging | `cr:SendLogging` | `src/component/Icons/SendLogging.tsx` | preserves `fill-rule="nonzero"` |
| SendLoggingFilled | `cr:SendLoggingFilled` | `src/component/Icons/SendLoggingFilled.tsx` | preserves `fill-rule="nonzero"` |
| Server | `cr:Server` | `src/component/Icons/Server.tsx` | — |
| ServerFilled | `cr:ServerFilled` | `src/component/Icons/ServerFilled.tsx` | — |
| Setting | `cr:Setting` | `src/component/Icons/Setting.tsx` | — |
| SettingsOutlined | `cr:SettingsOutlined` | `src/component/Icons/SettingsOutlined.tsx` | — |
| Share | `cr:Share` | `src/component/Icons/Share.tsx` | `createSvgIcon` wrapper (24x24 default) |
| ShareAndroid | `cr:ShareAndroid` | `src/component/Icons/ShareAndroid.tsx` | — |
| ShareFilled | `cr:ShareFilled` | `src/component/Icons/ShareFilled.tsx` | — |
| ShareOutlined | `cr:ShareOutlined` | `src/component/Icons/ShareOutlined.tsx` | — |
| Shield | `cr:Shield` | `src/component/Icons/Shield.tsx` | — |
| ShieldAdd | `cr:ShieldAdd` | `src/component/Icons/ShieldAdd.tsx` | — |
| ShieldDismiss | `cr:ShieldDismiss` | `src/component/Icons/ShieldDismiss.tsx` | — |
| ShieldFilled | `cr:ShieldFilled` | `src/component/Icons/ShieldFilled.tsx` | — |
| ShieldLock | `cr:ShieldLock` | `src/component/Icons/ShieldLock.tsx` | — |
| ShieldLockFilled | `cr:ShieldLockFilled` | `src/component/Icons/ShieldLockFilled.tsx` | dropped trailing JSX whitespace expression |
| SignOut | `cr:SignOut` | `src/component/Icons/SignOut.tsx` | — |
| SlideText | `cr:SlideText` | `src/component/Icons/SlideText.tsx` | — |
| Sparkle | `cr:Sparkle` | `src/component/Icons/Sparkle.tsx` | — |
| SparkleFilled | `cr:SparkleFilled` | `src/component/Icons/SparkleFilled.tsx` | — |
| StarFilled | `cr:StarFilled` | `src/component/Icons/StarFilled.tsx` | — |
| Storage | `cr:Storage` | `src/component/Icons/Storage.tsx` | — |
| StorageOutlined | `cr:StorageOutlined` | `src/component/Icons/StorageOutlined.tsx` | — |
| Subtitles | `cr:Subtitles` | `src/component/Icons/Subtitles.tsx` | — |
| SunWithTime | `cr:SunWithTime` | `src/component/Icons/SunWithTime.tsx` | — |
| Sunny | `cr:Sunny` | `src/component/Icons/Sunny.tsx` | — |
| TableSettings | `cr:TableSettings` | `src/component/Icons/TableSettings.tsx` | — |
| Tag | `cr:Tag` | `src/component/Icons/Tag.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Tags | `cr:Tags` | `src/component/Icons/Tags.tsx` | `createSvgIcon` wrapper (24x24 default) |
| TaskList | `cr:TaskList` | `src/component/Icons/TaskList.tsx` | explicit `viewBox="0 0 20 20"` -> per-icon 20x20 |
| TaskListOutlined | `cr:TaskListOutlined` | `src/component/Icons/TaskListOutlined.tsx` | explicit `viewBox="0 0 20 20"` -> per-icon 20x20 |
| TaskListRegular | `cr:TaskListRegular` | `src/component/Icons/TaskListRegular.tsx` | — |
| Telegram | `cr:Telegram` | `src/component/Icons/Telegram.tsx` | — |
| TextBulletListSquareEdiFilled | `cr:TextBulletListSquareEdiFilled` | `src/component/Icons/TextBulletListSquareEdiFilled.tsx` | — |
| TextBulletListSquareEdit | `cr:TextBulletListSquareEdit` | `src/component/Icons/TextBulletListSquareEdit.tsx` | — |
| TextCaseTitle | `cr:TextCaseTitle` | `src/component/Icons/TextCaseTitle.tsx` | — |
| TextEditStyle | `cr:TextEditStyle` | `src/component/Icons/TextEditStyle.tsx` | `createSvgIcon` wrapper (24x24 default) |
| TextGrammarLighting | `cr:TextGrammarLighting` | `src/component/Icons/TextGrammarLighting.tsx` | — |
| TextIndentIncrease | `cr:TextIndentIncrease` | `src/component/Icons/TextIndentIncrease.tsx` | — |
| TicketDiagonal | `cr:TicketDiagonal` | `src/component/Icons/TicketDiagonal.tsx` | — |
| Timer | `cr:Timer` | `src/component/Icons/Timer.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Translate | `cr:Translate` | `src/component/Icons/Translate.tsx` | — |
| Upload | `cr:Upload` | `src/component/Icons/Upload.tsx` | preserves `fill-rule="nonzero"` |
| UploadFilled | `cr:UploadFilled` | `src/component/Icons/UploadFilled.tsx` | preserves `fill-rule="nonzero"` |
| Video | `cr:Video` | `src/component/Icons/Video.tsx` | — |
| VideoOutlined | `cr:VideoOutlined` | `src/component/Icons/VideoOutlined.tsx` | — |
| WalletCreditCard | `cr:WalletCreditCard` | `src/component/Icons/WalletCreditCard.tsx` | `createSvgIcon` wrapper (24x24 default) |
| Warning | `cr:Warning` | `src/component/Icons/Warning.tsx` | — |
| WarningOutlined | `cr:WarningOutlined` | `src/component/Icons/WarningOutlined.tsx` | — |
| Whiteboard | `cr:Whiteboard` | `src/component/Icons/Whiteboard.tsx` | `createSvgIcon` wrapper (24x24 default) |
| WindowApps | `cr:WindowApps` | `src/component/Icons/WindowApps.tsx` | — |
| WrenchSettings | `cr:WrenchSettings` | `src/component/Icons/WrenchSettings.tsx` | `createSvgIcon` wrapper (24x24 default) |

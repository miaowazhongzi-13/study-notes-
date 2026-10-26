# 学习笔记 App

个人学习笔记应用：年 → 月 → 日历 → 当天笔记，支持分类文件夹、拍照、手绘画板、图文混排富文本、图片缩放锁定、艾宾浩斯遗忘曲线复习提醒、多设备云同步（邮箱验证码登录）、夜间模式、PWA 离线安装，以及 Android 壳 APK。

## 版本

| 版本 | 内容 | 标签 |
|------|------|------|
| v1.0 | 网页版（本地存储） + Android 壳 APK | `v1.0` |
| v1.1 | 网页版（云同步/登录/详情弹层/编辑与删除/夜间模式） + Android 壳完整源码 + 两个 APK | `v1.1` |

切换版本：`git checkout v1.0` 或 `git checkout v1.1`

## 目录结构

```
web/       网页源码（单文件 HTML + manifest + Service Worker + 图标）
android/   Android 壳工程（WebView 加载线上地址，含离线兜底页、拍照桥、退出确认）
releases/  打包好的 APK
```

## 网页版使用

任意静态服务器托管 `web/` 即可，例如：

```
cd web && python -m http.server 8080
```

支持「添加到主屏幕」以 PWA 方式运行；断网时由 Service Worker 兜底。

## Android 打包

```
cd android
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17、Android SDK（minSdk 24 / target 34）。修改 `MainActivity.java` 里的 `START_URL` 可指向自己的部署地址。

## 技术要点

- 纯 HTML+CSS+JS 零依赖，数据存 localStorage
- 云同步：Last-Write-Wins + 删除墓碑（tombstone），多端合并不丢数据
- 复习计划：艾宾浩斯节点 1/2/4/7/15/30/60 天
- 富文本：contenteditable + 选区记忆，图片用 `wbimg://` 协议标记、渲染时换签名链接

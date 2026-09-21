# NewsPlay 学院新闻循环展示系统

本项目仅上传 PDF。一页 PDF 对应一篇新闻。管理员在后台上传、预览、加入播放、下架、排序并调整大厅展示设置。

## 当前开发环境

- 前端：Vue 3 + Element Plus，开发地址 `http://127.0.0.1:5173/admin`
- 后端：Spring Boot，地址 `http://127.0.0.1:8080`
- 数据库：本地 MySQL，数据库名 `newsplay`
- PDF 渲染：Python 3、`pypdfium2`、`Pillow`
- 管理员：首次启动时创建 `admin`，初始密码读取 `.env` 的 `NEWSPLAY_INITIAL_PASSWORD`

`.env` 保存本机数据库和初始账号密码，已加入 `.gitignore`。修改管理员初始密码配置只影响首次建号，不会重置已有账号。

## 启动

1. 确保 MySQL 正在运行，创建 `newsplay` 数据库：

   ```sql
   CREATE DATABASE IF NOT EXISTS newsplay CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 复制 `.env.example` 为 `.env`，设置数据库密码及初始管理员密码。本机开发配置已创建。
3. 安装 PDF 处理依赖：`python -m pip install pypdfium2 Pillow`。
4. 在项目根目录运行 `powershell -File .\start-backend.ps1`。后端会自动建表。
5. 在 `frontend` 目录运行 `npm install`，再运行 `npm run dev`。
6. 打开 `http://127.0.0.1:5173/admin` 登录；大厅预览地址为 `http://127.0.0.1:5173/display`。

前端正式构建：在 `frontend` 目录运行 `npm run build`。后端构建：在 `backend` 目录运行 `mvn package`。

## 目录

`frontend/` 是管理与大厅页面；`backend/` 是接口、数据库和 `converter/convert.py`；`uploads/` 保存原 PDF、展示图、缩略图和资源图片。上传数据不会进入版本控制。

## 操作顺序

登录后台 → 上传 PDF → 等待转换完成 → 预览各页 → 加入播放 → 打开大厅展示页。大厅每约 30 秒检查播放列表及设置变化。

## 日志与故障排查

后端运行日志统一写入项目根目录的 `logs/newsplay.log`，控制台中也会同步显示。日志记录服务启动、接口访问、管理员登录结果、PDF 上传与转换、新闻上下架和排序、文件删除、展示设置修改以及异常堆栈；登录密码不会写入日志。

日志不按级别拆分文件。单个文件达到 20MB 或日期变化时自动归档到 `logs/archive/`，归档文件保留 30 天，总大小最多 1GB。日志目录可通过 `.env` 中的 `NEWSPLAY_LOG_DIR` 修改。

出现问题时，先查看日志末尾：

```powershell
.\view-logs.ps1
```

持续观察新日志：

```powershell
.\view-logs.ps1 -Tail 50 -Wait
```

日志使用带编码标记的 UTF-8。`start-backend.ps1` 会在后端完整启动前自动检查并补充编码标记，避免 Windows 编辑器按系统默认编码打开后出现中文乱码。

## 部署提示

在学院服务器安装 Java 17、MySQL、Python 与 PDF 处理依赖，配置环境变量和持久化存储目录。大厅电脑用 Edge 全屏模式打开 `/display`。部署时将前端构建产物交给静态 Web 服务，并把 `/api` 转发到后端；也可以复制 `frontend/dist` 内容到后端的 `src/main/resources/static` 后重新构建。

# ============================================
#  ElytraSlot 仓库整理脚本 (Windows PowerShell)
#  功能: 清理仓库垃圾文件, 初始化git, 推送到GitHub
#  用法: 放到 D:\Minecraftmod\ElytraSlot 目录下运行
# ============================================
Set-Location $PSScriptRoot

Write-Host "[1/6] 创建 .gitignore ..."
$gitignore = @'
build/
.gradle/
_decomp/
.idea/
*.iml
.vscode/
.DS_Store
Thumbs.db
*.class
'@
[System.IO.File]::WriteAllText("$PWD\.gitignore", $gitignore, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[2/6] 创建 LICENSE (MIT) ..."
$mit = @'
MIT License

Copyright (c) 2026 Bla-ck-swan

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
'@
[System.IO.File]::WriteAllText("$PWD\LICENSE", $mit, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[3/6] 创建 README.md ..."
$readme = @'
# ElytraSlot（鞘翅槽）

为 Minecraft 26.2 / Java 25 / Fabric 开发的鞘翅槽模组：在副手槽正上方新增专用鞘翅槽（仅能放鞘翅），胸甲槽禁止放鞘翅。

## 功能
- 专用鞘翅槽，右键穿戴/互换
- 胸甲与鞘翅同时显示
- 创造模式点击/拖拽支持
- Mending 经验修补兼容

## 构建
需要 Java 25。

    set JAVA_HOME=<你的Java路径>
    gradlew build

构建产物位于 build/libs/。

## 许可
MIT
'@
[System.IO.File]::WriteAllText("$PWD\README.md", $readme, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[4/6] 初始化 git 仓库 ..."
if (-not (Test-Path .git)) { git init }

# 提交者信息：改成你自己的名字和邮箱
git config user.name "Bla-ck-swan"
git config user.email "你的GitHub邮箱@example.com"

Write-Host "[5/6] 暂存文件并检查 ..."
git add .
Write-Host "----- 将提交的文件列表 -----"
git status --short
Write-Host "----- 垃圾文件检查（正常应无输出）-----"
$bad = git ls-files | Select-String -Pattern '(^|/)(build|_decomp|\.gradle)/|\.html$|\.class$'
if ($bad) { $bad; Write-Host "!! 发现垃圾文件，已中断，请检查 .gitignore"; exit 1 } else { Write-Host "OK，无垃圾文件" }

Write-Host "[6/6] 提交并推送 ..."
git commit -m "clean up: 只保留源码与构建配置"
git branch -M main
git remote add origin https://github.com/Bla-ck-swan/Minecraftmod.git 2>$null
git push -u origin main --force

Write-Host ""
Write-Host "完成! 刷新 GitHub 页面确认语言统计。"
pause

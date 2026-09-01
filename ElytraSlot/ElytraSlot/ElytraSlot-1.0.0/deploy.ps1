$modName = "elytraslot-1.0.0.jar"
$src = "D:\我的世界\ElytraSlot\build\libs\$modName"

# 复制到服务端
$serverDest = "D:\我的世界\MinecraftServer\mods\$modName"
Copy-Item $src $serverDest -Force
"服务端: OK -> $serverDest"

# 自动找客户端目录
$baseDir = Get-ChildItem D:\ -Directory -Depth 0 | Where-Object {
    ($_.Name.Length -gt 0) -and ($_.Name[0] -gt 127)
} | Where-Object {
    Test-Path (Join-Path $_.FullName "26.2-Fabric\mods")
}

if ($baseDir) {
    $clientDest = Join-Path $baseDir.FullName "26.2-Fabric\mods\$modName"
    Copy-Item $src $clientDest -Force
    "客户端: OK -> $clientDest"
} else {
    "客户端: 未自动找到，请手动复制"
}

"全部完成！"

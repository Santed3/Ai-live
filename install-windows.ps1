# ScreenBubble - installera på Windows utan SmartScreen-varning.
# Körs med:  irm https://raw.githubusercontent.com/Santed3/Ai-live/main/install-windows.ps1 | iex
$ErrorActionPreference = 'Stop'
$url = 'https://github.com/Santed3/Ai-live/releases/download/latest-build/ScreenBubble-Setup.exe'
$out = Join-Path $env:TEMP 'ScreenBubble-Setup.exe'
Write-Host 'Laddar ner ScreenBubble...'
Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
Write-Host 'Startar installationen...'
Start-Process -FilePath $out -Wait
Remove-Item $out -Force -ErrorAction SilentlyContinue
Write-Host 'Klart. ScreenBubble finns i Startmenyn.'

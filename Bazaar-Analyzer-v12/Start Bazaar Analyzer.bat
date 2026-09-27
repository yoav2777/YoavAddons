@echo off
title Bazaar Analyzer
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0serve.ps1"
if errorlevel 1 (
  echo.
  echo Something went wrong. See the message above.
  pause
)

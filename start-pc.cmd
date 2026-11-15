@echo off
rem Claude Tablet motor a PC-n: a tablet appból elérhető ugyanazon a Wi-Fin (Menü → ＋ Gép).
rem Használat: start-pc.cmd [port]   (alap: 8765; ha az foglalt a gépen, adj meg mást, pl. 8766)
rem Első indításkor a Windows tűzfal rákérdez: engedélyezd a hálózati hozzáférést.
chcp 65001 >nul
cd /d "%~dp0"
title Claude Tablet motor (PC)
set HOST=0.0.0.0
if not "%~1"=="" set PORT=%~1
node "%~dp0server.js"
pause

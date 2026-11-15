@echo off
rem Claude Tablet motor a PC-n: a tablet appból elérhető ugyanazon a Wi-Fin (Menü → ＋ Gép).
rem Első indításkor a Windows tűzfal rákérdez: engedélyezd a "Magánhálózat"-on.
set HOST=0.0.0.0
node "%~dp0server.js"
pause

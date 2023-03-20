@echo off
chcp 1251>nul
setlocal enabledelayedexpansion enableextensions
:againn
FOR /F "usebackq delims=~" %%a IN (`netstat -e`) DO (
 set res=%%a
 if /I "!res:~0,4!"=="Байт" (set fres=%%a)
)
timeout /t 1 /NOBREAK>nul
FOR /F "usebackq delims=~" %%a IN (`netstat -e`) DO (
 set res=%%a
 if /I "!res:~0,4!"=="Байт" (set sres=%%a)
)
FOR /F "usebackq tokens=1,2* delims= " %%a IN ('!fres!') DO (
 set fress=%%b
)
FOR /F "usebackq tokens=1,2* delims= " %%a IN ('!sres!') DO (
 set sress=%%b
)
set /a sresss=!sress:~-9!
set /a fresss=!fress:~-9!
set /a speed=!sresss!-!fresss!
set /a speedk=!speed!/1024
set /a speedm=!speedk!/1024
if !speedk! GEQ 1 (
 if !speedk! LEQ 1023 (
  cls
  echo Используемая скорость !speedk! Кбайт/сек (!speed! Байт/сек^)
 )
)
if !speed! LEQ 1023 (
 cls
 echo Используемая скорость !speed! Байт/сек
)
if !speedm! GEQ 1 (
 cls
 echo Используемая скорость !speedm! Мбайт/сек ^(!speedk! Кбайт/сек^)
)
goto :againn
pause>nul
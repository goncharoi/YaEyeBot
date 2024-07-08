@echo off

:: Имитация нажатия клавиш
echo Set WshShell = WScript.CreateObject("WScript.Shell") > %temp%\keypress.vbs
echo WshShell.SendKeys "^+{F1}" >> %temp%\keypress.vbs  :: Ctrl+Shift+F1
cscript //nologo %temp%\keypress.vbs

:: Удаление временного файла
del %temp%\keypress.vbs
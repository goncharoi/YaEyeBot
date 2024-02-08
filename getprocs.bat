@echo off
chcp 866
mode con cols=999 lines=999
powershell -command "Get-Process | Select-Object -Property Id, ProcessName, {'|'}, SI, {'%%'}, Path, {'#'}, Description, {'^'}, FileVersion -Unique | Format-Table -AutoSize"
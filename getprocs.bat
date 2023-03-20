@echo off
chcp 866
mode con cols=999 lines=999
powershell -command "Get-Process | Select-Object -Property ProcessName, {'|'}, SI, {'&'}, Path, {'#'}, Description -Unique | Format-Table -AutoSize"
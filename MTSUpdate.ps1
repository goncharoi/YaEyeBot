Write-Host "wait 2 minutes until MTS initially launched, because bot may be launched first"
Start-Sleep -Seconds 120

Write-Host "Stop guest launched MTS"
Get-Process -Name rds-watcher -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name rds-wrtc -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "Wait for guest launched MTS stopped"
Wait-Process -Name rds-watcher, rds-wrtc.exe -ErrorAction SilentlyContinue 
Write-Host "Wait for guest launched MTS stopped 146%"
Start-Sleep -Seconds 30

Write-Host "launch MTS from admin to autostart updating"
runas /user:Gamer /savecred "D:\MTS Remote play\bin\rds-wrtc.exe"
Write-Host "wait 5 minutes until MTS will be updated"
Start-Sleep -Seconds 300


Write-Host "Stop admin launched MTS"
runas /user:Gamer /savecred "taskkill -f -im  rds-watcher.exe"
runas /user:Gamer /savecred "taskkill -f -im  rds-wrtc.exe"
Write-Host "Wait for admin launched MTS stopped"
Wait-Process -Name rds-watcher, rds-wrtc.exe -ErrorAction SilentlyContinue 
Write-Host "Wait for admin launched MTS stopped 146%"
Start-Sleep -Seconds 30

Write-Host "launch updated MTS from guest"
Start-Process -FilePath "D:\MTS Remote play\bin\rds-wrtc.exe" -WorkingDirectory "D:\MTS Remote play\bin"
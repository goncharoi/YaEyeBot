# script to get the current CPU temperature, needs to run as admin/system
# requires an external DLL from the GitHub-Project "LibreHardwareMonitor"
# on first execution the script downloads the DLL from the gitHub-Project
# carsten.giese@googlemail.com

cls
$dll = "lhm\LibreHardwareMonitorLib.dll"
Add-Type -LiteralPath $dll

$monitor = [LibreHardwareMonitor.Hardware.Computer]::new()
$monitor.IsGPUEnabled = $true
$monitor.Open()
foreach ($sensor in $monitor.Hardware.Sensors) {
    if ($sensor.SensorType -eq 'Temperature' -and $sensor.Name -eq 'GPU Core'){
        write-host 'GPU_temperature' $sensor.Value -f y
    }
    if ($sensor.SensorType -eq 'Load' -and $sensor.Name -eq 'GPU Core'){
        write-host 'GPU_load' $sensor.Value -f y
    }
}
$monitor.Close()

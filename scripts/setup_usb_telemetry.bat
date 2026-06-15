@echo off
set ADB_PATH="%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if exist %ADB_PATH% (
    echo [INFO] Found Android SDK ADB at %ADB_PATH%
    %ADB_PATH% reverse tcp:5005 tcp:5005
    echo [SUCCESS] Telemetry port 5005 has been forwarded over USB!
) else (
    echo [ERROR] Could not find Android SDK at default path.
    echo Please run 'adb reverse tcp:5005 tcp:5005' manually.
)
pause

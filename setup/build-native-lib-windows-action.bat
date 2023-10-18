
@set BASEDIR=%~dp0
@powershell -ExecutionPolicy Bypass -File %BASEDIR%\build-native-lib-windows-action.ps1 %1 %2
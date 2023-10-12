@setlocal ENABLEDELAYEDEXPANSION
@echo OFF

set BASEDIR=%~dp0
cd %BASEDIR%\..
set PROJECTDIR=%CD%

set BUILDOS=%1
set BUILDARCH=%2

if "%BUILDOS%"=="" (
  echo Usage: script [buildos] [buildarch]
  exit /B
)

if "%BUILDARCH%"=="" (
  echo Usage: script [buildos] [buildarch]
  exit /B
)

echo
echo Project Dir: %PROJECTDIR%
echo Target OS: %BUILDOS%
echo Target Arch: %BUILDARCH%
echo

set VCVARSALLARCH=x64
if "%BUILDARCH%"=="arm64" (
  set VCVARSALLARCH=x64_arm64
)

@REM can we install visual studio variables?
@call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvarsall.bat" %VCVARSALLARCH%

@REM we need to swap in VCToolsInstallDir (but it has a trailing slash)
set VCTOOLSDIR=%VCToolsInstallDir:~0,-1%

mkdir target
rsync -avrt --delete ./native/ ./target/

set

cd .\target\tkrzw

@REM we need the correct path to visual studio tools and the windows sdk we are compiling against
@REM C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.34.31933
@REM C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0
@REM we can do this with environment vars set by visual studio "vcvarsall.bat"
@REM we need NO trailing slash on the tools dir like vcvarsall will include
set VCPATH=%VCToolsInstallDir:~0,-1%
set SDKPATH=%WindowsSdkDir%Lib\%WindowsSDKLibVersion%
@REM for x64 or arm64
set TGTARCH=%VSCMD_ARG_TGT_ARCH%
@REM we need NO trailing slash on the sdk path like WindowsSDKLibVersion will include
set SDKPATH=%SDKPATH:~0,-1%

@REM https://stackoverflow.com/questions/44262083/which-sdk-do-i-need-to-ensure-windows-7-compatibility-in-visual-studio-c-2017
@REM 0x0601 will be windows 7 and up
@REM 0x0A00 will be windows 10 and up
set WINDOWS_VERSION_TARGET=0x0601

@REM for sed to work with a path being replaced, we need to escape the \ chars twice
set VCPATH_ESCAPED=%VCPATH:\=\\\%
set SDKPATH_ESCAPED=%SDKPATH:\=\\\%

echo Will use VCPATH %VCPATH%
echo Will use SDKPATH %SDKPATH%

sed -i -e 's/VCPATH = .*/VCPATH = %VCPATH_ESCAPED%/' VCMakefile
sed -i -e 's/SDKPATH = .*/SDKPATH = %SDKPATH_ESCAPED%/' VCMakefile
sed -i -e 's#CLFLAGS = /nologo#CLFLAGS = /nologo /DWINVER=%WINDOWS_VERSION_TARGET% /D_WIN32_WINNT=%WINDOWS_VERSION_TARGET%#' VCMakefile
sed -i -e 's/x64/%TGTARCH%/' VCMakefile

cat VCMakefile

nmake -f VCMakefile

cd ..\tkrzw-java

echo Copying %PROJECTDIR%\setup\VCMakefile-jtkrzw

copy "%PROJECTDIR%\setup\VCMakefile-jtkrzw" .\VCMakefile
sed -i -e 's/VCPATH = .*/VCPATH = %VCPATH_ESCAPED%/' VCMakefile
sed -i -e 's/SDKPATH = .*/SDKPATH = %SDKPATH_ESCAPED%/' VCMakefile
sed -i -e 's#CLFLAGS = /nologo#CLFLAGS = /nologo /DWINVER=%WINDOWS_VERSION_TARGET% /D_WIN32_WINNT=%WINDOWS_VERSION_TARGET%#' VCMakefile
sed -i -e 's/x64/%TGTARCH%/' VCMakefile

@REM nmake -f VCMakefile headers
nmake -f VCMakefile

copy .\jtkrzw.dll ..\..\tkrzw-%BUILDOS%-%BUILDARCH%\src\main\resources\jne\%BUILDOS%\%BUILDARCH%\
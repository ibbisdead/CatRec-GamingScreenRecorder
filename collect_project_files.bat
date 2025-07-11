@echo off
setlocal

:: Output zip name
set ZIP_NAME=android_project_full_%DATE:~10,4%-%DATE:~4,2%-%DATE:~7,2%.zip

:: Project root (adjust if needed)
set PROJECT_ROOT=%CD%

:: Temporary folder
set TEMP_FOLDER=%TEMP%\android_project_full_collect
if exist "%TEMP_FOLDER%" rmdir /s /q "%TEMP_FOLDER%"
mkdir "%TEMP_FOLDER%"

:: Collect app/src/main
echo Copying app/src/main ...
xcopy "%PROJECT_ROOT%\app\src\main" "%TEMP_FOLDER%\app\src\main" /s /i /y

:: Collect Gradle files
echo Copying Gradle scripts ...
xcopy "%PROJECT_ROOT%\*.gradle*" "%TEMP_FOLDER%\" /i /y
xcopy "%PROJECT_ROOT%\gradle.properties" "%TEMP_FOLDER%\" /i /y
xcopy "%PROJECT_ROOT%\settings.gradle*" "%TEMP_FOLDER%\" /i /y

:: Collect ProGuard rules
echo Copying ProGuard rules ...
if exist "%PROJECT_ROOT%\app\proguard-rules.pro" xcopy "%PROJECT_ROOT%\app\proguard-rules.pro" "%TEMP_FOLDER%\" /i /y

:: Zip
echo Zipping...
powershell Compress-Archive -Path "%TEMP_FOLDER%\*" -DestinationPath "%PROJECT_ROOT%\%ZIP_NAME%" -Force

:: Cleanup
rmdir /s /q "%TEMP_FOLDER%"

echo Done! %ZIP_NAME% created in your project folder. Upload it here.
pause

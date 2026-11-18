@echo off
REM Run a black-box API suite against a running application (Windows).
REM   run-newman.bat smoke            -> environments\local
REM   run-newman.bat regression ci
setlocal
cd /d "%~dp0"
if "%~1"=="" (echo usage: run-newman.bat smoke^|sanity^|regression [environment] & exit /b 1)
set SUITE=%~1
set ENVIRONMENT=%~2
if "%ENVIRONMENT%"=="" set ENVIRONMENT=local
set EXTRA=
if "%SUITE%"=="smoke" set EXTRA=--bail
if not exist reports mkdir reports
call npx --yes newman run "%SUITE%\%SUITE%.postman_collection.json" -e "environments\%ENVIRONMENT%.postman_environment.json" --timeout-request 15000 --reporters cli,junit --reporter-junit-export "reports\%SUITE%-results.xml" %EXTRA%
exit /b %ERRORLEVEL%

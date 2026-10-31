@echo off
setlocal
set "MVN_CMD="
where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set "MVN_CMD=mvn"
    goto execute
)
for /d %%A in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9*") do (
    for /d %%B in ("%%A\*") do (
        if exist "%%B\bin\mvn.cmd" (
            set "MVN_CMD=%%B\bin\mvn.cmd"
            goto execute
        )
        for /d %%C in ("%%B\*") do (
            if exist "%%C\bin\mvn.cmd" (
                set "MVN_CMD=%%C\bin\mvn.cmd"
                goto execute
            )
        )
    )
)
:execute
if not "%MVN_CMD%"=="" (
    call "%MVN_CMD%" %*
    exit /b %ERRORLEVEL%
)
echo [ERROR] Maven executable could not be found.
exit /b 1

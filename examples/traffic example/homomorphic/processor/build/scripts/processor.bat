@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  processor startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Add default JVM options here. You can also use JAVA_OPTS and PROCESSOR_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\processor.jar;%APP_HOME%\lib\ope-0.0.1-SNAPSHOT.jar;%APP_HOME%\lib\devicehive-java-3.1.2.jar;%APP_HOME%\lib\javallier_2.10-0.6.0.jar;%APP_HOME%\lib\junit-4.13.jar;%APP_HOME%\lib\devicehive-java-client-3.1.2.jar;%APP_HOME%\lib\devicehive-java-websocket-3.1.2.jar;%APP_HOME%\lib\devicehive-java-rest-3.1.2.jar;%APP_HOME%\lib\scala-library-2.10.4.jar;%APP_HOME%\lib\logback-classic-1.0.13.jar;%APP_HOME%\lib\commons-cli-1.3.1.jar;%APP_HOME%\lib\commons-codec-1.10.jar;%APP_HOME%\lib\jnagmp-2.0.0.jar;%APP_HOME%\lib\jackson-databind-2.7.0.jar;%APP_HOME%\lib\hamcrest-core-1.3.jar;%APP_HOME%\lib\converter-gson-2.3.0.jar;%APP_HOME%\lib\converter-scalars-2.3.0.jar;%APP_HOME%\lib\retrofit-2.3.0.jar;%APP_HOME%\lib\logging-interceptor-3.9.0.jar;%APP_HOME%\lib\okhttp-3.9.0.jar;%APP_HOME%\lib\joda-time-2.9.9.jar;%APP_HOME%\lib\logback-core-1.0.13.jar;%APP_HOME%\lib\slf4j-api-1.7.5.jar;%APP_HOME%\lib\jna-4.0.0.jar;%APP_HOME%\lib\jackson-annotations-2.7.0.jar;%APP_HOME%\lib\jackson-core-2.7.0.jar;%APP_HOME%\lib\gson-2.7.jar;%APP_HOME%\lib\okio-1.13.0.jar

@rem Execute processor
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %PROCESSOR_OPTS%  -classpath "%CLASSPATH%" com.n1analytics.paillier.Main %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable PROCESSOR_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%PROCESSOR_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

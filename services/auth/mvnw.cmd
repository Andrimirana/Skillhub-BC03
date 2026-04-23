@REM Maven Wrapper Script (Windows)
@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET DP0=%~dp0

@SET WRAPPER_JAR="%DP0%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET MAVEN_PROJECTBASEDIR=%DP0%

@IF EXIST %WRAPPER_JAR% (
  @"%JAVA_HOME%\bin\java.exe" ^
    -classpath %WRAPPER_JAR% ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    %MAVEN_OPTS% %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
) ELSE (
  @echo [ERROR] maven-wrapper.jar introuvable : %WRAPPER_JAR%
  @exit /B 1
)


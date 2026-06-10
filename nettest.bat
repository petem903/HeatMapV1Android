@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d "%~dp0"
"%JAVA_HOME%\bin\java.exe" NetTest.java
echo --- IPv4 forced ---
"%JAVA_HOME%\bin\java.exe" -Djava.net.preferIPv4Stack=true NetTest.java
echo --- TLS1.2 + IPv4 ---
"%JAVA_HOME%\bin\java.exe" -Djava.net.preferIPv4Stack=true -Dhttps.protocols=TLSv1.2 -Djdk.tls.client.protocols=TLSv1.2 NetTest.java
endlocal

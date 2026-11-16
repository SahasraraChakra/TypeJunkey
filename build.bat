@echo off
rem Plain-JDK build (no Maven needed): build.bat  ->  dist\TypeJunKey.jar  (FlatLaf from lib\ is bundled in)
if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
if exist stage rmdir /s /q stage
mkdir out
mkdir dist
mkdir stage
dir /s /b src\main\java\*.java > sources.txt
javac --release 17 -encoding UTF-8 -cp lib\flatlaf-3.8.jar -d out @sources.txt || goto :eof
del sources.txt
xcopy /e /i /y /q out stage >nul
xcopy /e /i /y /q src\main\resources stage >nul
pushd stage
jar xf ..\lib\flatlaf-3.8.jar
del /q META-INF\*.SF META-INF\*.DSA META-INF\*.RSA META-INF\MANIFEST.MF 2>nul
popd
echo Main-Class: com.typejunkey.Main> stage\manifest.txt
echo Multi-Release: true>> stage\manifest.txt
jar --create --file dist\TypeJunKey.jar --manifest stage\manifest.txt -C stage .
rmdir /s /q stage
echo Built dist\TypeJunKey.jar  (run: java -jar dist\TypeJunKey.jar)

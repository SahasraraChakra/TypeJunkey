#!/bin/sh
# Plain-JDK build (no Maven needed): ./build.sh  ->  dist/TypeJunKey.jar  (FlatLaf from lib/ is bundled in)
set -e
rm -rf out dist stage && mkdir -p out dist stage
javac --release 17 -encoding UTF-8 -cp lib/flatlaf-3.8.jar -d out $(find src/main/java -name '*.java')
cp -r out/. stage/
cp -r src/main/resources/. stage/
(cd stage && jar xf ../lib/flatlaf-3.8.jar && rm -f META-INF/*.SF META-INF/*.DSA META-INF/*.RSA META-INF/MANIFEST.MF)
printf 'Main-Class: com.typejunkey.Main\nMulti-Release: true\n' > stage/manifest.txt
jar --create --file dist/TypeJunKey.jar --manifest stage/manifest.txt -C stage .
rm -rf stage
echo "Built dist/TypeJunKey.jar  (run: java -jar dist/TypeJunKey.jar)"

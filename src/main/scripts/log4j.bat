@echo off
color 0a

echo thisDir     : %~dp0
echo filename is : %~n0.bat
cd %~dp0

cp ../lib/${project.artifactId}-${project.version}.jar ../
cd ../
java -jar ${project.artifactId}-${project.version}.jar
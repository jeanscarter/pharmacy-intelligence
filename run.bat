@echo off
echo Compilando e iniciando Pharmacy Intelligence...
call mvnw.cmd clean compile exec:java
pause

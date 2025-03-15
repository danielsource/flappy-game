#!/bin/bash

set -e

sources='Game.java'

for s in $sources; do
	obj=out/"${s%.java}".class
	if [ "$s" -nt "$obj" ]; then
		echo javac "$s" >&2
		javac -Xlint:-serial -d out "$s"
	fi
done

java -cp out Game

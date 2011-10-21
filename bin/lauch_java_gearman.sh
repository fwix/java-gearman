#!/usr/bin/env zsh

MemLimit="$1"
port="$2"

if [[ -n "$1" && -n "$2" ]]; then
    echo "memlimit $MemLimit, port $port"
else
    echo "Either memory limit or port were not specified"
    exit
fi

java -Xmx $1 -jar Java-gearman.jar -p $port

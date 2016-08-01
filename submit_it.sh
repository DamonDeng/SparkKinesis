#!/bin/bash

spark-submit --master spark://172.31.28.217:7077 ./target/scala-2.10/KinesisTest-assembly-1.0.jar


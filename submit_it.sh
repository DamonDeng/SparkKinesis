#!/bin/bash

spark-submit ./target/scala-2.10/KinesisTest-assembly-1.0.jar ${STREAM_NAME} ${STREAM_APP_NAME} ${KINESIS_END_POINT}


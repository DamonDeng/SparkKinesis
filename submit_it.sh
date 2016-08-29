#!/bin/bash

echo "usage: spark-submit ./target/scala-2.10/KinesisTest-assembly-1.0.jar ${STREAM_NAME} ${STREAM_APP_NAME} ${KINESIS_END_POINT} ${DYNAMO_REGION} ${WINDOW_INTERVAL} ${WINDOW_WIDTH}"

spark-submit ./target/scala-2.10/KinesisTest-assembly-1.0.jar ${STREAM_NAME} ${STREAM_APP_NAME} ${KINESIS_END_POINT} ${DYNAMO_REGION} ${WINDOW_INTERVAL} ${WINDOW_WIDTH}




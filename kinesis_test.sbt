name := "KinesisTest"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.4.1"

libraryDependencies += "org.apache.spark" % "spark-streaming_2.10" % "1.5.1"

libraryDependencies += "org.apache.spark" % "spark-streaming-kinesis-asl_2.10" % "1.5.1"

assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard
case PathList(ps @ _*) if ps.last endsWith ".class" => MergeStrategy.first 
    case x =>
       val oldStrategy = (assemblyMergeStrategy in assembly).value
       oldStrategy(x)
}


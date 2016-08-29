
/* SimpleApp.scala */

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.{Logging, SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Seconds, Milliseconds, StreamingContext}
import org.apache.spark.streaming.dstream.DStream.toPairDStreamFunctions
import org.apache.spark.streaming.kinesis.KinesisUtils
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.auth.{DefaultAWSCredentialsProviderChain, BasicAWSCredentials}
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.services.kinesis.model.PutRecordRequest
import org.wltea.analyzer.lucene.IKAnalyzer

import scala.collection.mutable.ListBuffer

import java.io.StringReader

import com.amazonaws.services.dynamodbv2.document.{AttributeUpdate, DynamoDB, Item}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.Table

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.auth.InstanceProfileCredentialsProvider




object KinesisTest {

  def seperateWithIKAnalyzer(inputString:String): Array[String] ={

    val iKAnalyzer = new IKAnalyzer(true)

    val inputStreamReader = new StringReader(inputString)

    val ts:TokenStream = iKAnalyzer.tokenStream("",inputStreamReader)
    val term:CharTermAttribute=ts.getAttribute(classOf[CharTermAttribute])

    var result = new ListBuffer[String]()


    while(ts.incrementToken()){
      //System.out.print(term.toString()+"|")
      result+=term.toString
    }
    iKAnalyzer.close()
    inputStreamReader.close()

    result.toArray


  }

  def writeToDynamoDB(dynamoRegionName:String, inputKey:String, inputString:String): String ={

    //create the dynamoDB Client and table to store the result
    val dynamoDBClient = new AmazonDynamoDBClient(new InstanceProfileCredentialsProvider())
    dynamoDBClient.setRegion(Region.getRegion(Regions.fromName(dynamoRegionName)))
    val dynamoDB = new DynamoDB(dynamoDBClient)
    val targetTable = dynamoDB.getTable("VoteCount")


    val item = new Item().withPrimaryKey("Target", inputKey).withString("Result", inputString)

    targetTable.putItem(item)

    "Done!"+inputKey

  }


  def main(args: Array[String]) {

    val streamName=args(0)  // the first arg should be stream name such as  "testingStream"
    val appName=args(1) // the second arg should be kcl app name such as "baseonSample"
    val endpointUrl=args(2) // the kinesis endpoint such as the end point for sig: "kinesis.ap-southeast-1.amazonaws.com"
    val dynamoRegionName=args(3) // the region name of the Kinesis and DynamoDB, such as "ap-southeast-1"
    val streamWindowIntervalStr:String = args(4)
    val streamWindowWidthStr:String = args(5)

    val streamWindowInternval = streamWindowIntervalStr.toInt
    val streamWindowWidth = streamWindowWidthStr.toInt


    val credentials = new DefaultAWSCredentialsProviderChain().getCredentials()
    require(credentials != null,
      "No AWS credentials found. Please specify credentials using one of the methods specified " +
        "in http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html")
    val kinesisClient = new AmazonKinesisClient(credentials)
    kinesisClient.setEndpoint(endpointUrl)
    val numShards = kinesisClient.describeStream(streamName).getStreamDescription().getShards().size





    // In this example, we're going to create 1 Kinesis Receiver/input DStream for each shard.
    // This is not a necessity; if there are less receivers/DStreams than the number of shards,
    // then the shards will be automatically distributed among the receivers and each receiver
    // will receive data from multiple shards.
    val numStreams = numShards

    // Spark Streaming batch interval
    val batchInterval = Milliseconds(2000)

    // Kinesis checkpoint interval is the interval at which the DynamoDB is updated with information
    // on sequence number of records that have been received. Same as batchInterval for this
    // example.
    val kinesisCheckpointInterval = batchInterval

    // Get the region name from the endpoint URL to save Kinesis Client Library metadata in
    // DynamoDB of the same region as the Kinesis stream
    val regionName = RegionUtils.getRegionByEndpoint(endpointUrl).getName()

    // Setup the SparkConfig and StreamingContext
    val sparkConfig = new SparkConf().setMaster("local[4]").setAppName("KinesisSample")
    val ssc = new StreamingContext(sparkConfig, batchInterval)

    println("new version 1.0.1.5")


    // Create the Kinesis DStreams
    val kinesisStreams = (0 until numStreams).map { i =>
      KinesisUtils.createStream(ssc, appName, streamName, endpointUrl, regionName,
        InitialPositionInStream.LATEST, kinesisCheckpointInterval, StorageLevel.MEMORY_AND_DISK_2)
    }

    // Union all the streams
    val unionStreams = ssc.union(kinesisStreams)

    // Convert each line of Array[Byte] to String, and split into words
    //val lines = unionStreams.map(input => "Starting of the String" + new String(input) + "end of the string.")

    val lines =unionStreams.map(byteArray => new String(byteArray))

    val votes = lines.filter(line => line.startsWith("vot|")).map(line => line.substring(4))

    val messages = lines.filter(line => line.startsWith("msg|")).map(line => line.substring(4))

   // val stopWords = StopWords.getStopWords();

    //val words = messages.flatMap(line => seperateWithIKAnalyzer(line)).filter(word => !stopWords.contains(word))

    val words = messages.flatMap(line => seperateWithIKAnalyzer(line))


    // Map each word to a (word, 1) tuple so we can reduce by key to count the words
    //val wordCounts = words.map(word => (word, 1)).reduceByKey(_ + _)
    val wordCounts = words.map(word => (word, 1)).reduceByKeyAndWindow((a:Int,b:Int) => (a + b),Seconds(streamWindowWidth),Seconds(streamWindowInternval) )

    val sortedWordCounts = wordCounts.transform(rdd => {
      val list = rdd.sortBy(_._2, false).map(input => input._1).take(10)
      val newRDD = rdd.filter(inputItem => list.contains(inputItem._1))
      newRDD
    })


    //val voteCounts = votes.map(word => (word,1)).reduceByKey(_ + _)
    val voteCounts = votes.map(word => (word,1)).reduceByKeyAndWindow((a:Int,b:Int) => (a + b),Seconds(streamWindowWidth),Seconds(streamWindowInternval) )


    val voteResult = voteCounts.map(eachVoteCount => ("vote","\"" + eachVoteCount._1 +"\":"+eachVoteCount._2)).reduceByKey((a:String,b:String) => a +","+b)

    val wordResult = sortedWordCounts.map(eachWordCount => ("word","\"" + eachWordCount._1 +"\":"+eachWordCount._2)).reduceByKey((a:String,b:String) => a +","+b)

    val voteResultTrigger = voteResult.map(voteResultRecord => writeToDynamoDB(dynamoRegionName,voteResultRecord._1, voteResultRecord._2))

    val wordResultTrigger = wordResult.map(wordResultRecord => writeToDynamoDB(dynamoRegionName,wordResultRecord._1, wordResultRecord._2))


    // Print the first 10 wordCounts
    sortedWordCounts.print()
    voteCounts.print()
    voteResultTrigger.print()
    wordResultTrigger.print()




    //lines.print()

    // try to print a line here, it doesn't happend in the stream:
    println("end of the program")

    // Start the streaming context and await termination
    ssc.start()
    ssc.awaitTermination()
 }
}


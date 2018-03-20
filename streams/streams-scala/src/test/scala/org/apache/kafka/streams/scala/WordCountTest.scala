/**
  * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
  */

package org.apache.kafka.streams.scala

import java.util.regex.Pattern

import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import org.junit.Test

import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.scala.kstream._

import ImplicitConversions._
import com.typesafe.scalalogging.LazyLogging

import net.manub.embeddedkafka._
import ConsumerExtensions._
import streams._

class WordCountTest extends JUnitSuite with WordCountTestData with LazyLogging with EmbeddedKafkaStreamsAllInOne {

  @Test def testShouldCountWords(): Unit = {

    import DefaultSerdes._

    val streamBuilder = new StreamsBuilder
    val textLines = streamBuilder.stream[String, String](inputTopic)

    val pattern = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS)

    val wordCounts: KTable[String, Long] =
      textLines.flatMapValues(v => pattern.split(v.toLowerCase))
        .groupBy((k, v) => v)
        .count()

    wordCounts.toStream.to(outputTopic)

    runStreams(
      topicsToCreate = Seq(inputTopic, outputTopic),
      topology = streamBuilder.build()){ 

      implicit val ks = stringSerde.serializer()
      implicit val kds = stringSerde.deserializer()
      implicit val vds = longSerde.deserializer()

      inputValues.foreach { value =>
        publishStringMessageToKafka(inputTopic, value)
      }

      withConsumer[String, Long, Unit] { consumer =>
        implicit val cr = ConsumerRetryConfig(10, 3000)
        val consumedMessages = consumer.consumeLazily(outputTopic)
        assertEquals(consumedMessages.take(expectedWordCounts.size).sortBy(_.key).map(r => new KeyValue(r.key, r.value)), 
          expectedWordCounts.sortBy(_.key))
      }
    }
  }
}

trait WordCountTestData {
  val inputTopic = s"inputTopic.${scala.util.Random.nextInt(100)}"
  val outputTopic = s"outputTopic.${scala.util.Random.nextInt(100)}"
  val brokers = "localhost:9092"
  val localStateDir = "local_state_data"

  val inputValues = List(
    "Hello Kafka Streams",
    "All streams lead to Kafka",
    "Join Kafka Summit",
    "И теперь пошли русские слова"
  )

  val expectedWordCounts: List[KeyValue[String, Long]] = List(
    new KeyValue("hello", 1L),
    new KeyValue("all", 1L),
    new KeyValue("streams", 2L),
    new KeyValue("lead", 1L),
    new KeyValue("to", 1L),
    new KeyValue("join", 1L),
    new KeyValue("kafka", 3L),
    new KeyValue("summit", 1L),
    new KeyValue("и", 1L),
    new KeyValue("теперь", 1L),
    new KeyValue("пошли", 1L),
    new KeyValue("русские", 1L),
    new KeyValue("слова", 1L)
  )
}


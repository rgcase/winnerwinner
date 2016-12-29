package com.rgcase.winnerwinner

import java.time.{ LocalDateTime, ZoneId, ZonedDateTime }
import java.time.format.DateTimeFormatter

import org.asynchttpclient._
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest

import scala.compat.java8.FutureConverters
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml.XML

object WinnerWinner extends App {

  case class Event(id: String, name: String, startDate: String, color: String)

  def fetch(): Unit = {

    import scala.concurrent.ExecutionContext.Implicits.global
    val snsClient = new AmazonSNSClient()
    val id1 = sys.env("ID1")
    val phone1 = sys.env("PHONE1")
    val id2 = sys.env("ID2")
    val phone2 = sys.env("PHONE2")

    val zone = ZoneId.of("Canada/Eastern")
    val now = ZonedDateTime.now(zone)
    val today = now.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val client = new DefaultAsyncHttpClient()

    Await.result(FutureConverters.toScala(
      client.prepareGet("https://qbcf.sweepstakesforthecure.ca/v3/monthly-master/events.xml")
      .execute()
      .toCompletableFuture
    )
      .map { res ⇒
        val xmlEvents = XML.loadString(res.getResponseBody.drop(3)) \ "event"

        val todayEvents =
          xmlEvents
            .filter(e ⇒ (e \ "startdate").text == today)
            .map { e ⇒
              Event(
                (e \ "id").text,
                (e \ "name").text,
                (e \ "startdate").text,
                (e \ "color").text
              )
            }.toList

        println(s"Events for $today: \n" + todayEvents)

        val smsText1 = createSms(todayEvents, id1, phone1)
        val smsText2 = createSms(todayEvents, id2, phone2)

        println(s"SMS text for id $id1: \n" + smsText1)
        println(s"SMS text for id $id2: \n" + smsText2)

        val snsResult1 = snsClient.publish(
          new PublishRequest()
            .withMessage(smsText1)
            .withPhoneNumber(phone1)
        )

        val snsResult2 = snsClient.publish(
          new PublishRequest()
            .withMessage(smsText2)
            .withPhoneNumber(phone2)
        )

        println(s"Result for id $id1: \n" + snsResult1)
        println(s"Result for id $id2: \n" + snsResult2)

      }.recover {
        case ex ⇒
          snsClient.publish(
            new PublishRequest()
              .withPhoneNumber(phone1)
              .withMessage(
                "WinnerWinner failed on " + now +
                  " with exception: \n" + ex.getMessage
              )
          )
          println(s"Failure on ${LocalDateTime.now()} with exception: \n" + ex.getMessage)
      }, 10.seconds)

    client.close()

  }

  def createSms(todayEvents: List[Event], id: String, phone: String): String =
    todayEvents.map { e ⇒
      if (e.id == id) {
        s"Your number $id won ${e.color} today!"
      } else {
        s"Your number $id didn't win ${e.color} today :(. The winner was number ${e.id}."
      }
    }.mkString("\n\n")

  fetch()

}

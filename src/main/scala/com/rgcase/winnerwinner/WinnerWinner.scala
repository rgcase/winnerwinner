package com.rgcase.winnerwinner

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.xml.XML

object WinnerWinner {

  case class Event(id: String, name: String, startDate: String, color: String)

  def fetch(): Unit = {

    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    val client = AhcWSClient()
    val snsClient = new AmazonSNSClient()
    val id1 = sys.props.get("ID1").get
    val phone1 = sys.props.get("PHONE1").get
    val id2 = sys.props.get("ID2").get
    val phone2 = sys.props.get("PHONE2").get

    val result = client.url("https://qbcf.sweepstakesforthecure.ca/v3/monthly-master/events.xml").get().map { res ⇒
      val xmlEvents = XML.loadString(res.body.drop(3)) \ "event"

      val today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
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

      println(s"SMS sent for id $id1: \n" + smsText1)
      println(s"SMS sent for id $id2: \n" + smsText2)

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
              "WinnerWinner failed on " + LocalDateTime.now() +
                " with exception: \n" + ex.getMessage
            )
        )
        println(s"Failure on ${LocalDateTime.now()} with exception: \n" + ex.getMessage)
    }.flatMap(_ ⇒
      system.terminate().map { _ ⇒
        client.close()
        mat.shutdown()
      })

    Await.result(result, 10.seconds)

  }

  def createSms(todayEvents: List[Event], id: String, phone: String): String =
    todayEvents.map { e ⇒
      if (e.id == id) {
        s"Your number $id won ${e.color} today!"
      } else {
        s"Your number $id didn't win ${e.color} today :(. The winner was number ${e.id}."
      }
    }.mkString("\n\n")

}

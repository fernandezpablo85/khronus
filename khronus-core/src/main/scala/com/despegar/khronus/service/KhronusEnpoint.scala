package com.despegar.khronus.service

import akka.actor.Props
import com.despegar.khronus.model.MetricBatch
import com.despegar.khronus.store.MetricMeasurementStoreSupport
import com.despegar.khronus.util.JacksonJsonSupport
import com.despegar.khronus.util.log.Logging
import spray.http.StatusCodes._
import spray.httpx.encoding.{ Gzip, NoEncoding }
import spray.routing._

import scala.util.Failure
import scala.util.control.NonFatal

class KhronusActor extends HttpServiceActor with KhronusEnpoint with KhronusHandlerException {
  def receive = runRoute(metricsRoute)
}

object KhronusActor {
  val Name = "khronus-actor"
  val Path = "khronus/metrics"

  def props = Props[KhronusActor]
}

trait KhronusEnpoint extends HttpService with MetricMeasurementStoreSupport with JacksonJsonSupport with Logging {

  override def loggerName = classOf[KhronusEnpoint].getName

  val metricsRoute: Route =
    decompressRequest(Gzip, NoEncoding) {
      post {
        entity(as[MetricBatch]) { metricBatch ⇒
          complete {
            metricStore.storeMetricMeasurements(metricBatch.metrics)
            OK
          }
        }
      }
    }

}

object SprayMetrics extends Logging {

  import spray.routing.directives.BasicDirectives._

  def around(before: RequestContext ⇒ (RequestContext, Any ⇒ Any)): Directive0 =
    mapInnerRoute { inner ⇒
      ctx ⇒
        val (ctxForInnerRoute, after) = before(ctx)
        try inner(ctxForInnerRoute.withRouteResponseMapped(after))
        catch {
          case NonFatal(ex) ⇒ after(Failure(ex))
        }
    }

  def buildAfter(name: String, start: Long): Any ⇒ Any = { possibleRsp: Any ⇒
    possibleRsp match {
      case _ ⇒
        log.info(s"$name time spent ${System.currentTimeMillis() - start} ms")
    }
    possibleRsp
  }

  def time(name: String): Directive0 =
    around { ctx ⇒
      val timerContext = System.currentTimeMillis()
      (ctx, buildAfter(name, timerContext))
    }

}
/**
 * Copyright 2016 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.metrics.sink

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.codahale.metrics.MetricRegistry
import com.izettle.metrics.influxdb.{InfluxDbHttpSender, InfluxDbReporter, InfluxDbSender}
import org.apache.spark.{SecurityManager, SparkConf, SparkEnv}
import org.apache.spark.metrics.MetricsSystem
import org.apache.spark.util.Utils

import scala.collection.JavaConversions

/**
  * @author tstearns
  * based on GraphiteSink.scala from the Spark codebase
  */
class InfluxDbSink(val property: Properties, val registry: MetricRegistry,
    securityMgr: SecurityManager) extends Sink {
  val INFLUX_DEFAULT_TIMEOUT = 1000 // milliseconds
  val INFLUX_DEFAULT_PERIOD = 10
  val INFLUX_DEFAULT_UNIT = TimeUnit.SECONDS
  val INFLUX_DEFAULT_PROTOCOL = "https"
  val INFLUX_DEFAULT_PREFIX = ""
  val INFLUX_DEFAULT_TAGS = ""

  val INFLUX_KEY_PROTOCOL = "protocol"
  val INFLUX_KEY_HOST = "host"
  val INFLUX_KEY_PORT = "port"
  val INFLUX_KEY_PERIOD = "period"
  val INFLUX_KEY_UNIT = "unit"
  val INFLUX_KEY_DATABASE = "database"
  val INFLUX_KEY_AUTH = "auth"
  val INFLUX_KEY_PREFIX = "prefix"
  val INFLUX_KEY_TAGS = "tags"

  def propertyToOption(prop: String): Option[String] = Option(property.getProperty(prop))

  if (propertyToOption(INFLUX_KEY_HOST).isEmpty) {
    throw new Exception("InfluxDb sink requires 'host' property.")
  }

  if (propertyToOption(INFLUX_KEY_PORT).isEmpty) {
    throw new Exception("InfluxDb sink requires 'port' property.")
  }

  if (propertyToOption(INFLUX_KEY_DATABASE).isEmpty) {
    throw new Exception("InfluxDb sink requires 'database' property.")
  }

  val protocol = propertyToOption(INFLUX_KEY_PROTOCOL).getOrElse(INFLUX_DEFAULT_PROTOCOL)
  val host = propertyToOption(INFLUX_KEY_HOST).get
  val port = propertyToOption(INFLUX_KEY_PORT).get.toInt
  val database = propertyToOption(INFLUX_KEY_DATABASE).get
  val auth = property.getProperty(INFLUX_KEY_AUTH)
  val prefix = propertyToOption(INFLUX_KEY_PREFIX).getOrElse(INFLUX_DEFAULT_PREFIX)
  val tags = propertyToOption(INFLUX_KEY_TAGS).getOrElse(INFLUX_DEFAULT_TAGS)

  val applicationId = {
    // On the driver, the application id is not on the default SparkConf, so attempt to get from the SparkEnv
    // On executors, the SparkEnv will not be initialized by the time the metrics get initialized.
    // If all else fails, simply get the process name.
    val env = SparkEnv.get
    val conf = if (env != null) {
      env.conf
    } else {
      new SparkConf()
    }
    val appFromRegistry = JavaConversions.asScalaSet(registry.getNames)
      .filter(name => name != null)
      .find(name => name.startsWith("app") && name.contains("."))
      .map(name => name.substring(0, name.indexOf('.')))
    conf.getOption("spark.app.id").orElse(appFromRegistry).getOrElse(Utils.getProcessName())
  }

  val defaultTags = Seq(
    "host" -> Utils.localHostName(),
    "appId" -> applicationId)

  // example custom tag input string: "product:my_product,parent:my_service"
  val customTags = tags.split(",")
    .filter(pair => pair.contains(":"))
    .map(pair => (pair.substring(0, pair.indexOf(":")), pair.substring(pair.indexOf(":") + 1, pair.length())))
    .filter { case (k, v) => !k.isEmpty() && !v.isEmpty() }

  val allTags = (defaultTags ++ customTags).toMap

  val pollPeriod: Int = propertyToOption(INFLUX_KEY_PERIOD)
    .map(_.toInt)
    .getOrElse(INFLUX_DEFAULT_PERIOD)

  val pollUnit: TimeUnit = propertyToOption(INFLUX_KEY_UNIT)
    .map(s => TimeUnit.valueOf(s.toUpperCase))
    .getOrElse(INFLUX_DEFAULT_UNIT)

  MetricsSystem.checkMinimalPollingPeriod(pollUnit, pollPeriod)

  val sender : InfluxDbSender = new InfluxDbHttpSender(protocol, host, port, database, auth,
    TimeUnit.MILLISECONDS, INFLUX_DEFAULT_TIMEOUT, INFLUX_DEFAULT_TIMEOUT, prefix)

  val reporter: InfluxDbReporter = InfluxDbReporter.forRegistry(registry)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .convertRatesTo(TimeUnit.SECONDS)
      .withTags(JavaConversions.mapAsJavaMap(allTags))
      .groupGauges(true)
      .build(sender)

  override def start() {
      reporter.start(pollPeriod, pollUnit)
  }

  override def stop() {
      reporter.stop()
  }

  override def report() {
      reporter.report()
  }
}

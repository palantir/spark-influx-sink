spark-influx-sink
============
A spark metrics sink that pushes to InfluxDb

## Why is this useful?

Collecting diagnostic metrics from Apache Spark can be difficult because of the distributed nature of Spark. Polling Spark executor processes or scraping logs becomes tedious when executors run on an arbitrary number of remote hosts. This package instead uses a "push" method of sending metrics to a central host running InfluxDb, where they can be centrally analyzed.

## How to deploy

1. Run `./gradlew build`
2. Copy the JAR that is output to a path where Spark can read it, and add it to Spark's `extraClassPath`, along with [izettle/metrics-influxdb](https://github.com/iZettle/dropwizard-metrics-influxdb/) ([available on maven](https://repo1.maven.org/maven2/com/izettle/metrics-influxdb/1.1.8/metrics-influxdb-1.1.8.jar))
3. Add your new sink to Spark's `conf/metrics.properties`

Example metrics.properties snippet:

    *.sink.influx.class=org.apache.spark.metrics.sink.InfluxDbSink
    *.sink.influx.protocol=https
    *.sink.influx.host=localhost
    *.sink.influx.port=8086
    *.sink.influx.database=my_metrics
    *.sink.influx.auth=metric_client:PASSWORD
    *.sink.influx.tags=product:my_product,parent:my_service

## Notes

- This takes a dependency on the Apache2-licensed `com.izettle.dropwizard-metrics-influxdb` library, which is an improved version of Dropwizard's upstream InfluxDb support, which exists only in the DropWizard Metrics 4.0 branch.
- The package that this code lives in is `org.apache.spark.metrics.sink`, which is necessary because Spark makes its Sink interface package-private.

License
-------

This project is made available under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).

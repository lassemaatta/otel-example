(defproject otel-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [ring/ring-core "1.14.1"]
                 [ring/ring-jetty-adapter "1.14.1"]
                 [io.opentelemetry/opentelemetry-exporter-logging]
                 [io.opentelemetry/opentelemetry-exporter-otlp]
                 [io.opentelemetry.semconv/opentelemetry-semconv]
                 [io.opentelemetry.semconv/opentelemetry-semconv-incubating "1.30.0-alpha"]
                 [com.google.cloud.opentelemetry/propagators-gcp "0.33.0-alpha"]]
  :bom {:import [[io.opentelemetry.instrumentation/opentelemetry-instrumentation-bom "2.13.2"]]}
  :plugins [[lein-bom "0.2.0-SNAPSHOT"]]
  :main ^:skip-aot otel-example.core
  :repl-options {:init-ns otel-example.core}
  ;; Remove or comment out (with `;;`) the following line to disable the agent
  ;; note we use the 2.13.3 agent from
  ;; https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.13.3/opentelemetry-javaagent.jar
  :jvm-opts       ["-Dotel.resource.attributes=service.name=foo-service" "-javaagent:opentelemetry-javaagent.jar"]
  )

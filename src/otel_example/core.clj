(ns otel-example.core
  (:require
   [clojure.string :as str]
   [ring.adapter.jetty :as jetty])
  (:import
   [com.google.cloud.opentelemetry.propagators XCloudTraceContextPropagator]
   [io.opentelemetry.api.baggage.propagation W3CBaggagePropagator]
   [io.opentelemetry.api.trace Tracer]
   [io.opentelemetry.api.trace Span]
   [io.opentelemetry.api.trace SpanKind]
   [io.opentelemetry.api.trace StatusCode]
   [io.opentelemetry.api.trace.propagation W3CTraceContextPropagator]
   [io.opentelemetry.context.propagation ContextPropagators]
   [io.opentelemetry.context.propagation TextMapPropagator]
   [io.opentelemetry.exporter.logging LoggingMetricExporter]
   [io.opentelemetry.exporter.logging LoggingSpanExporter]
   [io.opentelemetry.exporter.otlp.http.metrics OtlpHttpMetricExporter]
   [io.opentelemetry.exporter.otlp.http.trace OtlpHttpSpanExporter]
   [io.opentelemetry.sdk OpenTelemetrySdk]
   [io.opentelemetry.sdk.metrics SdkMeterProvider]
   [io.opentelemetry.sdk.metrics.export PeriodicMetricReader]
   [io.opentelemetry.sdk.resources Resource]
   [io.opentelemetry.sdk.trace SdkTracerProvider]
   [io.opentelemetry.sdk.trace.export BatchSpanProcessor]
   [io.opentelemetry.sdk.trace.export SimpleSpanProcessor]
   [io.opentelemetry.sdk.trace.samplers Sampler]
   [io.opentelemetry.semconv HttpAttributes]
   [io.opentelemetry.semconv ServiceAttributes]
   [io.opentelemetry.semconv.incubating
    CodeIncubatingAttributes
    DbIncubatingAttributes
    HttpIncubatingAttributes
    ThreadIncubatingAttributes]))

(set! *warn-on-reflection* true)

(defn init-manual
  ^OpenTelemetrySdk [{:keys [exporter]}]
  (let [;; A resource represents an entity which produces telemetry (e.g. a Java application)
        r          (-> (Resource/getDefault)
                       .toBuilder
                       (.put ServiceAttributes/SERVICE_NAME "foo-service")
                       (.put ServiceAttributes/SERVICE_VERSION "1.00")
                       .build)
        tracer     (-> (SdkTracerProvider/builder)
                       (.addSpanProcessor (let [span-exporter (case (:type exporter)
                                                                #_#_:grpc (-> (OtlpGrpcSpanExporter/builder)
                                                                              (.setEndpoint (or (:endpoint exporter) "http://localhost:4317"))
                                                                              .build)
                                                                :http     (-> (OtlpHttpSpanExporter/builder)
                                                                              (.setEndpoint (or (:endpoint exporter) "http://localhost:4318/v1/traces"))
                                                                              .build)
                                                                :logging  (LoggingSpanExporter/create))]
                                            (case (:export exporter)
                                              :batch  (-> (BatchSpanProcessor/builder span-exporter) .build)
                                              :simple (SimpleSpanProcessor/create span-exporter))))
                       (.setSampler (Sampler/alwaysOn))
                       (.setResource r)
                       .build)
        meter      (-> (SdkMeterProvider/builder)
                       (.registerMetricReader (-> (PeriodicMetricReader/builder (case (:type exporter)
                                                                                  #_#_:grpc (-> (OtlpGrpcMetricExporter/builder)
                                                                                                (.setEndpoint (or (:endpoint exporter) "http://localhost:4317"))
                                                                                                .build)
                                                                                  :http     (-> (OtlpHttpMetricExporter/builder)
                                                                                                (.setEndpoint (or (:endpoint exporter) "http://localhost:4318/v1/traces"))
                                                                                                .build)
                                                                                  :logging  (LoggingMetricExporter/create)))
                                                  .build))
                       (.setResource r)
                       .build)
        propagator (-> (ContextPropagators/create
                         (TextMapPropagator/composite
                           ^"[Lio.opentelemetry.context.propagation.TextMapPropagator;"
                           (into-array TextMapPropagator [(W3CTraceContextPropagator/getInstance)
                                                          (W3CBaggagePropagator/getInstance)
                                                          (XCloudTraceContextPropagator. false)]))))]
    (-> (OpenTelemetrySdk/builder)
        (.setTracerProvider tracer)
        (.setMeterProvider meter)
        (.setPropagators propagator)
        .build)))

(def ^OpenTelemetrySdk telemetry (init-manual {:exporter {:type   :http
                                                          :export :batch}}))

(defn get-tracer
  (^Tracer []
   (get-tracer {}))
  (^Tracer [{:keys [scope-name scope-version]
             :or   {scope-name    "foo-service"
                    scope-version "1.0.0"}}]
   (.getTracer telemetry scope-name scope-version)))

(defn- set-attr
  [^Span span attr-key attr-val]
  (when attr-val
    (case attr-key
      :span-name                nil
      :telemetry/code-line      (.setAttribute span CodeIncubatingAttributes/CODE_LINENO (long attr-val))
      :telemetry/code-file      (.setAttribute span CodeIncubatingAttributes/CODE_FILEPATH attr-val)
      :telemetry/code-namespace (.setAttribute span CodeIncubatingAttributes/CODE_NAMESPACE attr-val)
      :telemetry/thread-id      (.setAttribute span ThreadIncubatingAttributes/THREAD_ID (long attr-val))
      :telemetry/thread-name    (.setAttribute span ThreadIncubatingAttributes/THREAD_NAME attr-val)
      :telemetry/http-method    (.setAttribute span HttpAttributes/HTTP_REQUEST_METHOD (str/upper-case attr-val))
      :telemetry/http-url       (.setAttribute span HttpIncubatingAttributes/HTTP_URL attr-val)
      :telemetry/http-status    (.setAttribute span HttpAttributes/HTTP_RESPONSE_STATUS_CODE (long attr-val))
      :telemetry/http-client-ip (.setAttribute span HttpIncubatingAttributes/HTTP_CLIENT_IP attr-val)
      :telemetry/db-operation   (.setAttribute span DbIncubatingAttributes/DB_OPERATION attr-val)
      :telemetry/db-mongo-coll  (.setAttribute span DbIncubatingAttributes/DB_MONGODB_COLLECTION attr-val)
      nil)))

(defn set-span-attrs
  [^Span span attrs]
  (doseq [[k v] attrs]
    (set-attr span k v)))

(defn start-span
  (^Span [opts]
   (start-span (get-tracer) opts))
  (^Span [^Tracer tracer {:keys [span-name]
                          :or   {span-name "unknown"}
                          :as   opts}]
   (when tracer
     (let [builder (.spanBuilder tracer span-name)]
       (.setSpanKind builder SpanKind/SERVER)
       (let [^Span span (.startSpan builder)]
         (set-span-attrs span opts)
         span)))))

(defmacro with-span
  [name-or-opts & body]
  `(let [opts#    (if (string? ~name-or-opts) {:span-name ~name-or-opts} ~name-or-opts)
         thread#  (Thread/currentThread)
         opts#    (merge {:telemetry/code-file      ~*file*
                          :telemetry/code-line      ~(:line (meta &form))
                          :telemetry/code-namespace ~(str *ns*)
                          :telemetry/thread-id      (.getId thread#)
                          :telemetry/thread-name    (.getName thread#)}
                         opts#)
         ^Span s# (and (not (:disabled? opts#))
                       (start-span opts#))]
     (if s#
       (try
         (with-open [scope# (.makeCurrent s#)]
           ~@body)
         (catch Exception e#
           (.setStatus s# StatusCode/ERROR)
           (.recordException s# e#)
           (throw e#))
         (finally
           (.end s#)))
       (do ~@body))))

(defn- handler
  [request]
  (with-span "outer span in handler"
    (Thread/sleep 10)
    (with-span "inner span in handler"
      (Thread/sleep 10)
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    "Hello World"})))

(defn -main
  "I don't do a whole lot."
  []
  (println "Starting jetty")
  (jetty/run-jetty handler {:port 8080}))

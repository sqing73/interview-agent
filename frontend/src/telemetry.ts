import { ZoneContextManager } from "@opentelemetry/context-zone";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { resourceFromAttributes } from "@opentelemetry/resources";
import {
  BatchSpanProcessor,
  WebTracerProvider,
} from "@opentelemetry/sdk-trace-web";
import { ATTR_SERVICE_NAME } from "@opentelemetry/semantic-conventions";

let initialized = false;

/**
 * Initializes OpenTelemetry browser tracing and automatic W3C `traceparent`
 * injection on all `fetch()` API calls.
 */
export function initTelemetry(): void {
  if (initialized) {
    return;
  }
  initialized = true;

  const collectorUrl =
    import.meta.env.VITE_OTEL_EXPORTER_OTLP_TRACES_ENDPOINT ||
    "http://localhost:4318/v1/traces";

  const exporter = new OTLPTraceExporter({ url: collectorUrl });

  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({
      [ATTR_SERVICE_NAME]: "interview-frontend",
    }),
    spanProcessors: [new BatchSpanProcessor(exporter)],
  });

  provider.register({
    contextManager: new ZoneContextManager(),
  });

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: [/.*/],
        clearTimingResources: true,
      }),
    ],
  });
}

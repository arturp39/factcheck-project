from prometheus_client import Counter, Histogram

# Prometheus metrics for monitoring
REQUEST_COUNT = Counter(
    "nlp_requests_total",
    "Total number of NLP requests",
    ["endpoint"],
)

REQUEST_LATENCY = Histogram(
    "nlp_request_latency_seconds",
    "Request latency in seconds",
    ["endpoint"],
)

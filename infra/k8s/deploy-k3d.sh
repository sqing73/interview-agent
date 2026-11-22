#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="interview-agent"
NAMESPACE="interview-agent"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--stop|-s] [--help|-h]

Options:
  (default)     Create/start k3d cluster '${CLUSTER_NAME}', build & import images, and deploy workloads.
  --stop, -s    Stop k3d cluster '${CLUSTER_NAME}' (preserves cluster state and Postgres PVC).
  --help, -h    Show this help message.
EOF
}

ACTION="deploy"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop|-s)
      ACTION="stop"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "${ACTION}" == "stop" ]]; then
  if k3d cluster list -o json | grep -q "\"name\":\"${CLUSTER_NAME}\""; then
    echo "==> Stopping k3d cluster '${CLUSTER_NAME}'..."
    k3d cluster stop "${CLUSTER_NAME}"
    echo "==> Cluster '${CLUSTER_NAME}' stopped (Postgres PVC and cluster state preserved)."
  else
    echo "==> Cluster '${CLUSTER_NAME}' does not exist; nothing to stop."
  fi
  exit 0
fi

echo "==> 1. Ensuring multi-node k3d cluster '${CLUSTER_NAME}' exists (1 server + 2 agents)..."
if ! k3d cluster list -o json | grep -q "\"name\":\"${CLUSTER_NAME}\""; then
  k3d cluster create "${CLUSTER_NAME}" \
    --agents 2 \
    -p "8088:80@loadbalancer"
else
  echo "    Cluster '${CLUSTER_NAME}' already exists, ensuring it is started and switching kubeconfig..."
  k3d cluster start "${CLUSTER_NAME}"
  k3d kubeconfig merge "${CLUSTER_NAME}" --kubeconfig-switch-context
fi

echo "==> 2. Building production Docker images..."
docker build -t interview-agent/ai-service:latest "${ROOT_DIR}/ai-service"
docker build -t interview-agent/orchestrator:latest "${ROOT_DIR}/orchestrator"
docker build -t interview-agent/frontend:latest "${ROOT_DIR}/frontend"

echo "==> 3. Importing images into k3d cluster '${CLUSTER_NAME}'..."
k3d image import \
  interview-agent/ai-service:latest \
  interview-agent/orchestrator:latest \
  interview-agent/frontend:latest \
  -c "${CLUSTER_NAME}"

echo "==> 4. Applying namespace, secrets, configmaps, host Ollama service, and Postgres init.sql..."
kubectl apply -f "${ROOT_DIR}/infra/k8s/namespace-config.yaml"
HOST_GATEWAY_IP="$(docker network inspect "k3d-${CLUSTER_NAME}" --format '{{range .IPAM.Config}}{{.Gateway}}{{end}}')"
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Endpoints
metadata:
  name: ollama-host
  namespace: ${NAMESPACE}
subsets:
  - addresses:
      - ip: ${HOST_GATEWAY_IP}
    ports:
      - name: http
        port: 11434
EOF
kubectl create configmap postgres-init-sql \
  --namespace "${NAMESPACE}" \
  --from-file=01-init.sql="${ROOT_DIR}/infra/postgres/init.sql" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> 5. Applying Kubernetes workloads & Traefik Ingress..."
kubectl apply -f "${ROOT_DIR}/infra/k8s/postgres.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/observability.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/ollama-host.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/ai-service.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/orchestrator.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/frontend.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/ingress.yaml"
kubectl rollout restart deployment/ai-service -n "${NAMESPACE}"

echo "==> 6. Waiting for deployments to become ready..."
for deploy in postgres jaeger otel-collector prometheus ai-service orchestrator frontend; do
  kubectl rollout status "deployment/${deploy}" -n "${NAMESPACE}" --timeout=180s
done

echo ""
echo "==> Deployment complete! Cluster status:"
kubectl get nodes -o wide
echo ""
kubectl get pods,svc,ingress,pvc -n "${NAMESPACE}" -o wide
echo ""
echo "App UI:        http://localhost:8088"
echo "Jaeger UI:     http://localhost:8088/jaeger/"
echo "Prometheus UI: http://localhost:8088/prometheus/graph"

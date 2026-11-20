#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="interview-agent"
NAMESPACE="interview-agent"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "==> 1. Ensuring multi-node k3d cluster '${CLUSTER_NAME}' exists (1 server + 2 agents)..."
if ! k3d cluster list -o json | grep -q "\"name\":\"${CLUSTER_NAME}\""; then
  k3d cluster create "${CLUSTER_NAME}" \
    --agents 2 \
    -p "8088:80@loadbalancer"
else
  echo "    Cluster '${CLUSTER_NAME}' already exists, switching kubeconfig..."
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

echo "==> 4. Applying namespace, secrets, configmaps, and Postgres init.sql..."
kubectl apply -f "${ROOT_DIR}/infra/k8s/00-namespace-config.yaml"
kubectl create configmap postgres-init-sql \
  --namespace "${NAMESPACE}" \
  --from-file=01-init.sql="${ROOT_DIR}/infra/postgres/init.sql" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> 5. Applying Kubernetes workloads & Traefik Ingress..."
kubectl apply -f "${ROOT_DIR}/infra/k8s/01-postgres.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/02-observability.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/03-ai-service.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/04-orchestrator.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/05-frontend.yaml"
kubectl apply -f "${ROOT_DIR}/infra/k8s/06-ingress.yaml"

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
echo "App UI:       http://localhost:8088"
echo "Jaeger UI:    kubectl port-forward -n ${NAMESPACE} svc/jaeger 16686:16686"
echo "Prometheus:   kubectl port-forward -n ${NAMESPACE} svc/prometheus 9090:9090"

{{- define "erg-spring.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "erg-spring.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "erg-spring.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "erg-spring.labels" -}}
app.kubernetes.io/name: {{ include "erg-spring.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "erg-spring.selectorLabels" -}}
app.kubernetes.io/name: {{ include "erg-spring.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "erg-spring.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "erg-spring.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "erg-spring.validateConnectionBudget" -}}
{{- $replicas := ternary (int .Values.replicaCount) (int .Values.autoscaling.maxReplicas) (eq .Values.autoscaling.mode "disabled") -}}
{{- $poolBudget := mul $replicas (int .Values.dbPool.perPodMaxConnections) -}}
{{- $available := sub (int .Values.dbPool.postgresMaxConnections) (int .Values.dbPool.reservedConnections) -}}
{{- if gt $poolBudget $available -}}
{{- fail (printf "unsafe DB pool budget: replicas(%d) * perPodMaxConnections(%d) = %d exceeds available PostgreSQL connections %d after reservedConnections" $replicas (int .Values.dbPool.perPodMaxConnections) $poolBudget $available) -}}
{{- end -}}
{{- if gt (int .Values.dbPool.perPodMinIdleConnections) (int .Values.dbPool.perPodMaxConnections) -}}
{{- fail "dbPool.perPodMinIdleConnections must not exceed perPodMaxConnections" -}}
{{- end -}}
{{- end }}


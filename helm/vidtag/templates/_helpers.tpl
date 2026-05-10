{{/*
Expand the name of the chart.
*/}}
{{- define "vidtag.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "vidtag.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "vidtag.labels" -}}
helm.sh/chart: {{ include "vidtag.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
App selector labels
*/}}
{{- define "vidtag.appSelectorLabels" -}}
app.kubernetes.io/name: {{ include "vidtag.fullname" . }}
app.kubernetes.io/component: app
{{- end }}

{{/*
Redis selector labels
*/}}
{{- define "vidtag.redisSelectorLabels" -}}
app.kubernetes.io/name: {{ include "vidtag.fullname" . }}
app.kubernetes.io/component: redis
{{- end }}

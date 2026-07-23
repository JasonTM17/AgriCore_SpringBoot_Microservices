{{- define "agricore.iotTimescalePreflight.secretName" -}}
{{- default .Values.postgres.databaseSecretName .Values.iot.timescalePreflight.credentialSecretName -}}
{{- end -}}

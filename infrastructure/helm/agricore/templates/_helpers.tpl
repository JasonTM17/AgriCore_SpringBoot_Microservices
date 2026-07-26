{{- define "agricore.iotTimescalePreflight.secretName" -}}
{{- default .Values.postgres.databaseSecretName .Values.iot.timescalePreflight.credentialSecretName -}}
{{- end -}}

{{- define "agricore.imageRef" -}}
{{- $image := required "service image repository or digest is required" .image -}}
{{- if contains "@" $image -}}
  {{- if not (regexMatch `@sha256:[a-fA-F0-9]{64}$` $image) -}}
    {{- fail "digest image references must end with @sha256 followed by 64 hexadecimal characters" -}}
  {{- end -}}
  {{- $image -}}
{{- else -}}
  {{- $tag := required "global.imageTag is required for tag-based image references" .tag -}}
  {{- if eq (lower $tag) "latest" -}}
    {{- fail "global.imageTag must be an immutable release tag or commit SHA; latest is not allowed" -}}
  {{- end -}}
  {{- printf "%s:%s" $image $tag -}}
{{- end -}}
{{- end -}}

{{- define "agricore.iotTimescalePreflight.secretName" -}}
{{- required "iot.timescalePreflight.credentialSecretName is required" .Values.iot.timescalePreflight.credentialSecretName -}}
{{- end -}}

{{- define "agricore.imageRef" -}}
{{- $image := required "service image repository or digest is required" .image -}}
{{- if contains "@" $image -}}
  {{- if not (regexMatch `@sha256:[a-fA-F0-9]{64}$` $image) -}}
    {{- fail "digest image references must end with @sha256 followed by 64 hexadecimal characters" -}}
  {{- end -}}
{{- $image -}}
{{- else -}}
  {{- if default false .requireDigest -}}
    {{- fail "digest-pinned service images are required; set each service image to repository@sha256:<digest> for a production release" -}}
  {{- end -}}
  {{- $tag := required "global.imageTag is required for tag-based image references" .tag -}}
  {{- if eq (lower $tag) "latest" -}}
    {{- fail "global.imageTag must be an immutable release tag or commit SHA; latest is not allowed" -}}
  {{- end -}}
  {{- if not (default false .allowMutable) -}}
    {{- if not (regexMatch `^[0-9a-f]{40}$` $tag) -}}
      {{- fail "global.imageTag must be the full 40-character lowercase commit SHA unless global.allowMutableImages=true" -}}
    {{- end -}}
  {{- end -}}
  {{- printf "%s:%s" $image $tag -}}
{{- end -}}
{{- end -}}

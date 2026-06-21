# syntax=docker/dockerfile:1.7
FROM golang:1.24-alpine AS build
WORKDIR /src
RUN apk add --no-cache ca-certificates git
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/luanet-api ./cmd/luanet-api

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/luanet-api /luanet-api
USER nonroot:nonroot
EXPOSE 8080
ENTRYPOINT ["/luanet-api"]


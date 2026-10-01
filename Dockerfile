FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://raw.githubusercontent.com/sbt/sbt/v1.11.6/sbt | tee /usr/local/bin/sbt >/dev/null && chmod +x /usr/local/bin/sbt
COPY project/ project/
COPY build.sbt .
RUN sbt update
COPY src/ src/
RUN sbt stage

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/universal/stage/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/bin/faculty-map"]

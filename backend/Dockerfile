# One image recipe for every backend service (PLAN.md 4.3/4.4). Build context: ./backend
#   docker build --build-arg SERVICE=products -f backend/Dockerfile backend
ARG SERVICE

FROM maven:3.9.11-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /src
# poms first so dependency downloads are cached until a pom changes
COPY pom.xml .
COPY common/pom.xml common/
COPY wovely/pom.xml wovely/
COPY products/pom.xml products/
COPY inventory/pom.xml inventory/
COPY gateway/pom.xml gateway/
RUN mvn -B -q -pl ${SERVICE} -am dependency:go-offline -DexcludeReactor=true || true
COPY common/src common/src
COPY ${SERVICE}/src ${SERVICE}/src
RUN mvn -B -q -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*.jar /app.jar

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S wovely && adduser -S -G wovely wovely
WORKDIR /app
COPY --from=build /app.jar app.jar
USER wovely
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

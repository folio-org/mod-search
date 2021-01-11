#--------------------------------------------------------
# 1. image with extracted application layers
#--------------------------------------------------------
FROM adoptopenjdk/openjdk11:alpine-jre as builder
# should be a single jar file
ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

#--------------------------------------------------------
# 2. target image
#--------------------------------------------------------
FROM adoptopenjdk/openjdk11:alpine-jre

ENV JAVA_APP_DIR=/opt/folio
RUN mkdir -p ${JAVA_APP_DIR}

COPY --from=builder dependencies/ ${JAVA_APP_DIR}/
COPY --from=builder snapshot-dependencies/ ${JAVA_APP_DIR}/
COPY --from=builder spring-boot-loader/ ${JAVA_APP_DIR}/
COPY --from=builder application/ ${JAVA_APP_DIR}/

COPY run.sh ${JAVA_APP_DIR}/
RUN chmod 755 ${JAVA_APP_DIR}/run.sh

# Expose this port locally in the container.
EXPOSE 8081

WORKDIR $JAVA_APP_DIR

ENTRYPOINT ["sh", "./run.sh"]

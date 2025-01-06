FROM ue-test.harbor.useasy.net/base/openjdk:11-jre-slim

RUN rm -f /etc/localtime && \
    ln -sv /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY ./target/locals3-1.0.0.jar /app/locals3-1.0.0.jar

WORKDIR /app/
# jvm参数：通过环境变量 JAVA_TOOL_OPTIONS 指定
CMD ["sh", "-c", "java --add-opens=java.base/java.lang=ALL-UNNAMED ${JAVA_TOOL_OPTIONS} -jar locals3-1.0.0.jar"]

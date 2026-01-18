# 使用官方 Java 17 镜像
FROM openjdk:17-jdk-alpine

# 设置工作目录
WORKDIR /app

# 挂载临时目录
VOLUME /tmp

# 将构建好的 jar 包复制到镜像中
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 暴露端口
EXPOSE 7800

# 启动命令
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]

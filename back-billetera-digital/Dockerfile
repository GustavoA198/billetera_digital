FROM amazoncorretto:21-al2023-headless

#Establecer el directorio de trabajo
WORKDIR /app

# Copiar el archivo JAR al contenedor
COPY ./target/card-financial-settlement-balance-transfer-init.jar /app/app.jar

# Comando de entrada
ENTRYPOINT ["java", "-jar", "app.jar"]
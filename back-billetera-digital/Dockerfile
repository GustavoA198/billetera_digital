FROM amazoncorretto:21-al2023-headless

#Establecer el directorio de trabajo
WORKDIR /app

# Copiar el archivo JAR al contenedor
COPY ./target/back-billetera-digital.jar /app/app.jar

# Comando de entrada
ENTRYPOINT ["java", "-jar", "app.jar"]
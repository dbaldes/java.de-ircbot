FROM eclipse-temurin:11

ARG jarfile
COPY ${jarfile} /ircbot.jar

CMD java -Xms64m -Xmx256m \
 -jar /ircbot.jar --spring.config.additional-location=file:///data/conf/ircbot.properties


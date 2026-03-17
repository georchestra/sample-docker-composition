FROM georchestra/security-proxy:23.1.8

RUN java -jar "$JETTY_HOME/start.jar" --create-startd --add-to-start=jmx,jmx-remote,stats,gzip,http-forwarded,threadpool,session-store-jdbc,ext
RUN cd $JETTY_BASE/lib/ext && \
      wget https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.10/postgresql-42.7.10.jar && \
      wget https://repo1.maven.org/maven2/com/zaxxer/HikariCP/7.0.2/HikariCP-7.0.2.jar && \
      wget https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar && \
      wget https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.17/slf4j-simple-2.0.17.jar

COPY sessions-hikaricp.xml $JETTY_HOME/etc/
RUN echo "etc/sessions-hikaricp.xml" >> $JETTY_BASE/start.ini

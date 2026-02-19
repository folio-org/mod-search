FROM elasticsearch:8.4.1

# Switch from /dev/random to /dev/urandom, otherwise the plugin installer needs many minutes to get
# random numbers to verify the signature of the downloaded plugins.
# https://github.com/elastic/elasticsearch/pull/84766
# https://stackoverflow.com/questions/137212/how-to-deal-with-a-slow-securerandom-generator
# https://security.stackexchange.com/questions/3936/is-a-rand-from-dev-urandom-secure-for-a-login-key
USER root
RUN sed -i 's!^securerandom\.source=.*!securerandom.source=file:/dev/./urandom!' /usr/share/elasticsearch/jdk/conf/security/java.security
USER elasticsearch

RUN elasticsearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-nori \
  analysis-phonetic

RUN echo "xpack.security.enabled: false" >> /usr/share/elasticsearch/config/elasticsearch.yml
ENV ES_JAVA_OPTS="-Xms512m -Xmx512m"

FROM opensearchproject/opensearch:3.0.0

# Switch from /dev/random to /dev/urandom, otherwise the plugin installer needs many minutes to get
# random numbers to verify the signature of the downloaded plugins.
# https://github.com/elastic/elasticsearch/pull/84766
# https://stackoverflow.com/questions/137212/how-to-deal-with-a-slow-securerandom-generator
# https://security.stackexchange.com/questions/3936/is-a-rand-from-dev-urandom-secure-for-a-login-key
RUN sed -i 's!^securerandom\.source=.*!securerandom.source=file:/dev/./urandom!' /usr/share/opensearch/jdk/conf/security/java.security

RUN opensearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-nori \
  analysis-phonetic

RUN opensearch-plugin remove opensearch-security
ENV OPENSEARCH_JAVA_OPTS="-Xms512m -Xmx512m"

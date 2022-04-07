FROM amazon/opendistro-for-elasticsearch:1.13.2

RUN bin/elasticsearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-stempel \
  analysis-nori \
  analysis-phonetic

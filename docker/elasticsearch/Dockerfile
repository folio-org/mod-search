FROM docker.elastic.co/elasticsearch/elasticsearch:7.10.1

RUN elasticsearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-nori \
  analysis-phonetic

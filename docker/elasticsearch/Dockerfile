FROM amazon/opendistro-for-elasticsearch:1.13.2

RUN elasticsearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-nori \
  analysis-phonetic

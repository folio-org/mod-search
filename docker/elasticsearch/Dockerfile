FROM opensearchproject/opensearch:1.3.2

RUN elasticsearch-plugin install --batch \
  analysis-icu \
  analysis-kuromoji \
  analysis-smartcn \
  analysis-nori \
  analysis-phonetic

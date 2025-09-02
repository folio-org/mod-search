FROM opensearchproject/opensearch:3

# Switch from /dev/random to /dev/urandom for faster random number generation
RUN sed -i 's!^securerandom\.source=.*!securerandom.source=file:/dev/./urandom!' /usr/share/opensearch/jdk/conf/security/java.security && \
    opensearch-plugin list | grep -q opensearch-security && \
    opensearch-plugin remove opensearch-security || \
    echo "opensearch-security plugin not installed or cannot be removed" && \
    opensearch-plugin install --batch \
        analysis-icu \
        analysis-kuromoji \
        analysis-smartcn \
        analysis-nori \
        analysis-phonetic

# Add a healthcheck to monitor the OpenSearch service
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:9200/_cluster/health || exit 1

# Switch to a non-root user for better security
USER opensearch

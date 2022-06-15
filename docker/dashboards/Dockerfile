FROM opensearchproject/opensearch-dashboards:1.3.2

RUN /usr/share/opensearch-dashboards/bin/opensearch-dashboards-plugin remove securityDashboards

COPY --chown=opensearch-dashboards:opensearch-dashboards opensearch_dashboards.yml /usr/share/opensearch-dashboards/config/

#!/bin/bash

# Wait for Kafka to be ready
sleep 10

# Create Kafka topics
KAFKA_BROKER=${KAFKA_HOST}:${KAFKA_PORT}

echo "Creating Kafka topics for mod-search..."

# Topics for mod-search
TOPICS=(
"${ENV}.Default.inventory.instance"
"${ENV}.Default.inventory.holdings-record"
"${ENV}.Default.inventory.item"
"${ENV}.Default.inventory.bound-with"
"${ENV}.Default.authorities.authority"
"${ENV}.Default.inventory.classification-type"
"${ENV}.Default.inventory.call-number-type"
"${ENV}.Default.inventory.location"
"${ENV}.Default.inventory.campus"
"${ENV}.Default.inventory.institution"
"${ENV}.Default.inventory.library"
"${ENV}.Default.linked-data.instance"
"${ENV}.Default.linked-data.work"
"${ENV}.Default.linked-data.hub"
"${ENV}.Default.search.reindex.range-index"
"${ENV}.Default.inventory.reindex-records"
)

# Updated to use the full path to kafka-topics.sh
KAFKA_TOPICS_CMD="/opt/kafka/bin/kafka-topics.sh"

for TOPIC in "${TOPICS[@]}"; do
  $KAFKA_TOPICS_CMD \
    --create \
    --bootstrap-server "$KAFKA_BROKER" \
    --replication-factor 1 \
    --partitions "${KAFKA_TOPIC_PARTITIONS}" \
    --topic "$TOPIC" \
    --if-not-exists
  echo "Created topic: $TOPIC"
done

echo "Kafka topics created successfully."


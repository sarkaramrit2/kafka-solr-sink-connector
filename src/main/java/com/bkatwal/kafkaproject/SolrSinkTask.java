/**
 * Copyright 2018 Bikas Katwal.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 **/

package com.bkatwal.kafkaproject;

import com.bkatwal.kafkaproject.utils.PlainJsonSolrDocMappersImpl;
import com.bkatwal.kafkaproject.utils.SinkService;
import com.bkatwal.kafkaproject.utils.SolrClientFactory;
import com.bkatwal.kafkaproject.utils.SolrSinkService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.apache.solr.common.SolrInputDocument;

@Slf4j
public class SolrSinkTask extends SinkTask {

  private SinkService<String, SinkRecord> sinkService;
  private SolrSinkConnectorConfig config;

  @Override
  public String version() {
    return VersionUtil.getVersion(SolrSinkTask.class);
  }

  @Override
  public void start(Map<String, String> configMap) {
    config = new SolrSinkConnectorConfig(configMap);

    sinkService = SolrSinkService.builder().collection(config.getCollectionConfig())
        .commitWithinMs(config.getCommitWithinMs()).indexBatchSize(config.getBatchSize())
        .jsonSolrDocMapper(new PlainJsonSolrDocMappersImpl()).solrClient(SolrClientFactory
            .getClient(config.getSolrModeConfig(), config.getSolrURLConfig())).build();

    log.debug("Created config: {}", config);

  }

  @Override
  public void put(Collection<SinkRecord> kafkaRecords) {
    List<SolrInputDocument> batchdocuments = new ArrayList<>();
    String id = null;
    for (SinkRecord record : kafkaRecords) {
      int index = 0;

      id = record.key() != null ? record.key().toString() : null;

      Schema valueSchema = record.valueSchema();

      //not a plain json data/schema less data
      //Expecting schema less record
      if (valueSchema != null) {
        log.error(
            "Check if record in topic is plain json data and value is schema less. Set schema.enable=false for value.");
        throw new ConnectException(
            "Check if record in topic is plain json data and value is schema less. Set schema.enable=false for value.");
      }

      if (++index % config.getBatchSize() != 0) {
        batchdocuments.add(sinkService.getJsonSolrDocMapper().convertToSolrDocument(record));
      }
      else {
        sinkService.insertBatch(id, batchdocuments);
        batchdocuments.clear();
      }
    }
    if (batchdocuments.size() > 0) {
      sinkService.insertBatch(id, batchdocuments);
    }
    batchdocuments.clear();
  }

  private boolean isDeleteRequest(Object delete) {
    return delete != null && (delete instanceof String ?
        Boolean.parseBoolean((String) delete) : (Boolean) delete);
  }

  @Override
  public void flush(Map<TopicPartition, OffsetAndMetadata> map) {
  }


  @Override
  public void stop() {
    sinkService.stop();
  }


  public void setConfig(SolrSinkConnectorConfig config) {
    this.config = config;
  }

  public SolrSinkConnectorConfig getConfig() {
    return config;
  }
}

package org.folio.search.model.streamids;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.types.EntityType;
import org.folio.search.model.types.StreamJobStatus;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@NoArgsConstructor
@Table(name = "resource_ids_job")
@AllArgsConstructor(staticName = "of")
public class ResourceIdsJobEntity {

  @Id
  @UuidGenerator
  private String id;
  private String query;

  @Column(name = "temp_table_name")
  private String temporaryTableName;

  @Enumerated(EnumType.STRING)
  private StreamJobStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type")
  private EntityType entityType;

  @Basic
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "created_date")
  private Date createdDate;
}

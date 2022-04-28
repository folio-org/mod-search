package org.folio.search.model.streamids;

import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.search.model.types.EntityType;
import org.folio.search.model.types.StreamJobStatus;
import org.hibernate.annotations.GenericGenerator;

@Data
@Entity
@NoArgsConstructor
@Table(name = "resource_ids_job")
@AllArgsConstructor(staticName = "of")
public class ResourceIdsJobEntity {

  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
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

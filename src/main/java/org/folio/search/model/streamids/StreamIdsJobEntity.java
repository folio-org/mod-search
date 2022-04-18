package org.folio.search.model.streamids;

import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "stream_job")
@AllArgsConstructor(staticName = "of")
public class StreamIdsJobEntity {

  @Id
  private String id;
  private String query;

  @Enumerated(EnumType.STRING)
  private StreamJobStatus status;

  @Basic
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "created_date")
  private Date createdDate;

}

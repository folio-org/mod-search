package org.folio.search.model.entity;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLInsert;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
@Table(name = "instance_subjects")
@AllArgsConstructor(staticName = "of")
@SQLInsert(sql = "insert into instance_subjects(instance_id, subject) values (?, ?) on conflict do nothing")
public class InstanceSubjectEntity implements Persistable<InstanceSubjectEntityId> {

  @EmbeddedId
  private InstanceSubjectEntityId id;

  @Override
  public boolean isNew() {
    return true;
  }
}

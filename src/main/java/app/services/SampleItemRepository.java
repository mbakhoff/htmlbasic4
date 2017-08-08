package app.services;

import app.model.SampleItem;
import org.springframework.data.repository.CrudRepository;

public interface SampleItemRepository extends CrudRepository<SampleItem, Long> {

}

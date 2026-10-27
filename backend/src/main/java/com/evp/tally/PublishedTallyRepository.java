package com.evp.tally;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedTallyRepository extends JpaRepository<PublishedTally, Long> {

    List<PublishedTally> findAllByOrderByIdAsc();
}